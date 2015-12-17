/*******************************************************************************
 * Copyright (c) 2015 Sebastian Stenzel and others.
 * This file is licensed under the terms of the MIT license.
 * See the LICENSE.txt file for more info.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.crypto.fs;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.cryptomator.crypto.engine.Cryptor;
import org.cryptomator.filesystem.File;
import org.cryptomator.filesystem.FileSystem;
import org.cryptomator.filesystem.Folder;
import org.cryptomator.filesystem.FolderCreateMode;
import org.cryptomator.filesystem.ReadableFile;
import org.cryptomator.filesystem.WritableFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CryptoFileSystem extends CryptoFolder implements FileSystem {

	private static final Logger LOG = LoggerFactory.getLogger(CryptoFileSystem.class);
	private static final String DATA_ROOT_DIR = "d";
	private static final String ROOT_DIR_FILE = "root";
	private static final String MASTERKEY_FILENAME = "masterkey.cryptomator";
	private static final String MASTERKEY_BACKUP_FILENAME = "masterkey.cryptomator.bkup";

	private final Folder physicalRoot;

	public CryptoFileSystem(Folder physicalRoot, Cryptor cryptor, CharSequence passphrase) {
		super(null, "", cryptor);
		this.physicalRoot = physicalRoot;
		final File masterkeyFile = physicalRoot.file(MASTERKEY_FILENAME);
		if (masterkeyFile.exists()) {
			final boolean unlocked = decryptMasterKeyFile(cryptor, masterkeyFile, passphrase);
			if (!unlocked) {
				// TODO new InvalidPassphraseException() ?
				throw new IllegalArgumentException("Wrong passphrase.");
			}
		} else {
			encryptMasterKeyFile(cryptor, masterkeyFile, passphrase);
		}
		assert masterkeyFile.exists() : "A CryptoFileSystem can not exist without a masterkey file.";
		final File backupFile = physicalRoot.file(MASTERKEY_BACKUP_FILENAME);
		masterkeyFile.copyTo(backupFile);
	}

	private static boolean decryptMasterKeyFile(Cryptor cryptor, File masterkeyFile, CharSequence passphrase) {
		try (ReadableFile file = masterkeyFile.openReadable()) {
			// TODO we need to read the whole file but can not be sure about the
			// buffer size:
			final ByteBuffer bigEnoughBuffer = ByteBuffer.allocate(500);
			file.read(bigEnoughBuffer);
			bigEnoughBuffer.flip();
			assert bigEnoughBuffer.remaining() < bigEnoughBuffer.capacity() : "The buffer wasn't big enough.";
			final byte[] fileContents = new byte[bigEnoughBuffer.remaining()];
			bigEnoughBuffer.get(fileContents);
			return cryptor.readKeysFromMasterkeyFile(fileContents, passphrase);
		}
	}

	private static void encryptMasterKeyFile(Cryptor cryptor, File masterkeyFile, CharSequence passphrase) {
		try (WritableFile file = masterkeyFile.openWritable()) {
			final byte[] fileContents = cryptor.writeKeysToMasterkeyFile(passphrase);
			file.write(ByteBuffer.wrap(fileContents));
		}
	}

	@Override
	File physicalFile() {
		return physicalDataRoot().file(ROOT_DIR_FILE);
	}

	@Override
	Folder physicalDataRoot() {
		return physicalRoot.folder(DATA_ROOT_DIR);
	}

	@Override
	public Optional<CryptoFolder> parent() {
		return Optional.empty();
	}

	@Override
	public boolean exists() {
		return physicalRoot.exists();
	}

	@Override
	public void delete() {
		// no-op.
	}

	@Override
	public void create(FolderCreateMode mode) {
		physicalDataRoot().create(mode);
		final File dirFile = physicalFile();
		final String directoryId = getDirectoryId();
		try (WritableFile writable = dirFile.openWritable()) {
			final ByteBuffer buf = ByteBuffer.wrap(directoryId.getBytes());
			writable.write(buf);
		}
		physicalFolder().create(FolderCreateMode.INCLUDING_PARENTS);
	}

	@Override
	public String toString() {
		return physicalRoot + ":::/";
	}

}