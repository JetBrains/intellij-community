package org.netbeans.lib.cvsclient.file;

import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.File;
import java.io.IOException;

/**
 * @author  Thomas Singer
 */
public final class CvsFileSystem
        implements ICvsFileSystem {

	// Fields =================================================================

	private final IFileSystem localFileSystem;
	private final IFileSystem adminFileSystem;
	private final String repository;

	// Setup ==================================================================

	public CvsFileSystem(File rootDirectory, String repository) {
		this(rootDirectory, rootDirectory, repository);
	}

	public CvsFileSystem(File localRootDirectory, File adminRootDirectory, String repository) {
		BugLog.getInstance().assertNotNull(localRootDirectory);
		BugLog.getInstance().assertNotNull(adminRootDirectory);
		BugLog.getInstance().assertNotNull(repository);

		this.localFileSystem = new FileSystem(localRootDirectory);
		this.adminFileSystem = new FileSystem(adminRootDirectory);
		this.repository = FileUtils.ensureTrailingSlash(repository).replace('\\', '/');
	}

	// Implemented ============================================================

	/**
	 * @param relativeLocalDirectoryPath ends with a trailing slash (e.g. "./" or "dir/" or "dir1/dir2")
	 * @param repositoryFilePath ends with a trailing slash, if it is a directory ("e.g. "module/" or "module/file.txt")
	 */
	@Override
        public FileObject getFileObject(String relativeLocalDirectoryPath, String repositoryFilePath) {
		BugLog.getInstance().assertTrue(!relativeLocalDirectoryPath.startsWith("/"), "relativeLocalDirectory '" + relativeLocalDirectoryPath + "' must not start with /");
		BugLog.getInstance().assertTrue(relativeLocalDirectoryPath.endsWith("/"), "relativeLocalDirectory '" + relativeLocalDirectoryPath + "' must end with /");
		BugLog.getInstance().assertTrue(!repositoryFilePath.endsWith("/"), "repositoryFilePath '" + repositoryFilePath + "' must not end with /");
		BugLog.getInstance().assertTrue(repositoryFilePath.indexOf('/') >= 0, "repositoryFileName '" + repositoryFilePath + "' must contain a /");

		if (relativeLocalDirectoryPath.equals("./")) { // NOI18N
			relativeLocalDirectoryPath = "";
		}

		final String fileName = getFileNameFromRepositoryPath(repositoryFilePath);
		return FileObject.createInstance('/' + relativeLocalDirectoryPath + fileName);
	}

	@Override
        public DirectoryObject getDirectoryObject(String relativeLocalDirectoryPath, String repositoryDirectoryPath) {
		BugLog.getInstance().assertTrue(!relativeLocalDirectoryPath.startsWith("/"), "relativeLocalDirectoryPath '" + relativeLocalDirectoryPath + "' must not start with /");
		BugLog.getInstance().assertTrue(relativeLocalDirectoryPath.endsWith("/"), "relativeLocalDirectoryPath '" + relativeLocalDirectoryPath + "' must end with /");
		BugLog.getInstance().assertTrue(repositoryDirectoryPath.endsWith("/"), "repositoryDirectoryPath '" + repositoryDirectoryPath + "' must end with /");

		if (relativeLocalDirectoryPath.equals("./")) {
			relativeLocalDirectoryPath = "";
		}

		return DirectoryObject.createInstance('/' + FileUtils.removeTrailingSlash(relativeLocalDirectoryPath));
	}

	@Override
        public IFileSystem getLocalFileSystem() {
		return localFileSystem;
	}

	@Override
        public IFileSystem getAdminFileSystem() {
		return adminFileSystem;
	}

	@Override
        public String getRelativeRepositoryPath(String repositoryPath) {
          if (repositoryPath.startsWith(repository)) {
            String relativeRepositoryPath = repositoryPath.substring(repository.length());
            relativeRepositoryPath = FileUtils.removeTrailingSlash(relativeRepositoryPath);
            if (relativeRepositoryPath.length() == 0) {
                    relativeRepositoryPath = ".";
            }
            return relativeRepositoryPath;
          } else {
            return FileUtils.removeTrailingSlash(repositoryPath);
          }
        }

	@Override
        public String getRepositoryForDirectory(DirectoryObject directoryObject, IAdminReader adminReader) {
		try {
			return adminReader.getRepositoryForDirectory(directoryObject, repository, this);
		}
		catch (IOException ex) {
			return FileUtils.removeTrailingSlash(repository) + directoryObject.getPath();
		}
	}

	@Override
        public FileObject unixFileNameToFileObject(String unixFileName) {
		return FileObject.createInstance('/' + unixFileName);
	}

	// Utils ==================================================================

	private static String getFileNameFromRepositoryPath(String repositoryFilePath) {
		final int lastSlashIndex = repositoryFilePath.lastIndexOf('/');
		return repositoryFilePath.substring(lastSlashIndex + 1);
	}
}
