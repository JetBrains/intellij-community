/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.file;

import org.netbeans.lib.cvsclient.util.BugLog;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author  Thomas Singer
 */
public final class FileSystem
        implements IFileSystem {

	// Fields =================================================================

	private final File rootDirectory;
	private final String canonicalRootDirectoryName;
  @NonNls private static final String OS_NAME_PARAMETER = "os.name";
  @NonNls private static final String WINDOWS = "Windows";

  // Setup ==================================================================

	public FileSystem(File rootDirectory) {
		BugLog.getInstance().assertNotNull(rootDirectory);

		this.rootDirectory = rootDirectory;
		this.canonicalRootDirectoryName = getCanonicalFileName(rootDirectory);
	}

	// Implemented ============================================================

	public File getRootDirectory() {
		return rootDirectory;
	}

	public File getFile(String relativeFileName) {
		BugLog.getInstance().assertNotNull(relativeFileName);

		return new File(rootDirectory, relativeFileName);
	}

	public File getFile(AbstractFileObject fileObject) {
		BugLog.getInstance().assertNotNull(fileObject);

		return new File(rootDirectory, fileObject.getPath().substring(1));
	}

	public FileObject getFileObject(File file) throws OutOfFileSystemException {
		BugLog.getInstance().assertNotNull(file);

		if (file.isDirectory()) {
			throw new IllegalArgumentException(file + " isn't a file");
		}

		return FileObject.createInstance(getPath(file));
	}

	public DirectoryObject getDirectoryObject(File directory) throws OutOfFileSystemException {
		BugLog.getInstance().assertNotNull(directory);

		if (directory.isFile()) {
			throw new IllegalArgumentException(directory + " isn't a directory");
		}

		return DirectoryObject.createInstance(getPath(directory));
	}

	// Utils ==================================================================

	private String getPath(File file) throws OutOfFileSystemException {
		final String canonicalFilePath = getCanonicalFileName(file);
		if (!canonicalFilePath.startsWith(canonicalRootDirectoryName)) {
			throw new OutOfFileSystemException(canonicalFilePath, canonicalRootDirectoryName);
		}

		final String filePath = file.getAbsolutePath();
		int beginIndex = canonicalRootDirectoryName.length();
		if (filePath.length() > beginIndex) {
			beginIndex++;
		}
		return '/' + filePath.substring(beginIndex).replace('\\', '/');
	}

	private static String getCanonicalFileName(File file) {
		String canonicalFileName = file.getAbsolutePath();
		if (canonicalFileName.endsWith(File.separator)) {
			canonicalFileName = canonicalFileName.substring(0, canonicalFileName.length() - 1);
		}
		if (System.getProperty(OS_NAME_PARAMETER).startsWith(WINDOWS)) {
			return canonicalFileName.toLowerCase();
		}
		return canonicalFileName;
	}
}
