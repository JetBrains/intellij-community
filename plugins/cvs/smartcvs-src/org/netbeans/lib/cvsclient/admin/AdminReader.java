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
package org.netbeans.lib.cvsclient.admin;

import org.netbeans.lib.cvsclient.file.*;
import org.netbeans.lib.cvsclient.util.BugLog;
import org.netbeans.lib.cvsclient.SmartCvsSrcBundle;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

/**
 * A handler for administrative information that maintains full compatibility
 * with the one employed by the original C implementation of a CVS client.
 * <p>This implementation strives to provide complete compatibility with
 * the standard CVS client, so that operations on locally checked-out
 * files can be carried out by either this library or the standard client
 * without causing the other to fail. Any such failure should be considered
 * a bug in this library.
 * @author  Robert Greig
 */
public final class AdminReader
        implements IAdminReader {

	// Setup ==================================================================

    private final String myCharset;
  @NonNls private static final String CVS_REPOSITORY_FILE_PATH = "CVS/Repository";
  @NonNls private static final String CVS_DIR_NAME = "CVS";
  @NonNls private static final String CVS_ENTRIES_STATIC_FILE_PATH = "CVS/Entries.Static";
  @NonNls private static final String CVS_ROOT_FILE_PATH = "CVS/Root";

  public AdminReader(String charset) {
    myCharset = charset;
  }

	// Implemented ============================================================

	/**
	 * Get the Entry for the specified file, if one exists
	 */
	public Entry getEntry(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) throws IOException {
		final File directory = cvsFileSystem.getAdminFileSystem().getFile(fileObject.getParent());

		try {
			final EntriesHandler entriesHandler = new EntriesHandler(directory);
			entriesHandler.read(myCharset);
			return entriesHandler.getEntries().getEntry(fileObject.getName());
		}
		catch (FileNotFoundException ex) {
			return null;
		}
	}

	public Collection getEntries(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException {
		final File directory = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);

		try {
			final EntriesHandler entriesHandler = new EntriesHandler(directory);
			entriesHandler.read(myCharset);
			return entriesHandler.getEntries().getEntries();
		}
		catch (FileNotFoundException ex) {
			return Collections.emptySet();
		}
	}

	/**
	 * Get the repository path for a given directory, for example in
	 * the directory /home/project/foo/bar, the repository directory
	 * might be /usr/cvs/foo/bar. The repository directory is commonly
	 * stored in the file <pre>Repository</pre> in the CVS directory on
	 * the client. (This is the case in the standard CVS command-line tool).
	 * However, the path stored in that file is relative to the repository
	 * path
	 */
	public String getRepositoryForDirectory(DirectoryObject directoryObject, String repository, ICvsFileSystem cvsFileSystem) throws IOException {
		final File directory = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);
		// if there is no "CVS/Repository" file, try to search up the file-hierarchy
		File repositoryFile;
		String repositoryDirs = "";
		File dirFile = directory;
		while (true) {
			// if there is no Repository file we cannot very well get any repository from it
			if (dirFile == null
			        || dirFile.getName().length() == 0
			        || !dirFile.exists()) {
				throw new FileNotFoundException(
                                  SmartCvsSrcBundle.message("repository.file.not.found.for.directory.error.message", directory));
			}

			repositoryFile = new File(dirFile, CVS_REPOSITORY_FILE_PATH);
			if (repositoryFile.exists()) {
				break;
			}

			repositoryDirs = '/' + dirFile.getName() + repositoryDirs;
			dirFile = dirFile.getParentFile();
		}

		String fileRepository = FileUtils.readLineFromFile(repositoryFile);

		if (fileRepository == null) {
			fileRepository = "";
		}

                fileRepository += repositoryDirs;

                fileRepository = fileRepository.replace(File.separatorChar, '/');

                if (fileRepository.startsWith(repository)) {
                  return fileRepository;
                } else {
                  return FileUtils.ensureTrailingSlash(repository) + fileRepository;
                }

		// otherwise the cvs is using relative repository path
		// must be a forward slash, regardless of the local filing system
	}

	public String getStickyTagForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
		return AdminUtils.getStickyTagForDirectory(directoryObject, cvsFileSystem);
	}

	public boolean hasCvsDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
		BugLog.getInstance().assertNotNull(directoryObject);
		BugLog.getInstance().assertNotNull(cvsFileSystem);

		final File directory = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);
		return new File(directory, CVS_DIR_NAME).isDirectory();
	}

	public boolean isModified(FileObject fileObject, Date entryLastModified, ICvsFileSystem cvsFileSystem) {
		final File file = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
		return !DateComparator.getInstance().equals(file.lastModified(), entryLastModified.getTime());
	}

    public boolean isStatic(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
        BugLog.getInstance().assertNotNull(directoryObject);
        BugLog.getInstance().assertNotNull(cvsFileSystem);

        final File directory = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);
        return new File(directory, CVS_ENTRIES_STATIC_FILE_PATH).isFile();
    }

    public String getCvsRootForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException {
    final File directory = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);
    // if there is no "CVS/Root" file, try to search up the file-hierarchy
    File rootFile;
    String repositoryDirs = "";
    File dirFile = directory;
    while (true) {
      // if there is no Repository file we cannot very well get any repository from it
      if (dirFile == null
              || dirFile.getName().length() == 0
              || !dirFile.exists()) {
        throw new FileNotFoundException(SmartCvsSrcBundle.message("root.file.not.found.for.directory.err.rmessage", directory));
      }

      rootFile = new File(dirFile, CVS_ROOT_FILE_PATH);
      if (rootFile.exists()) {
        break;
      }

      repositoryDirs = '/' + dirFile.getName() + repositoryDirs;
      dirFile = dirFile.getParentFile();
    }

    return FileUtils.readLineFromFile(rootFile).trim();
  }
}
