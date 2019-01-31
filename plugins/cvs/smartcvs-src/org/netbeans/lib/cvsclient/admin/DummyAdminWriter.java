package org.netbeans.lib.cvsclient.admin;

import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.file.*;

import java.io.InputStream;

/**
 * @author  Thomas Singer
 */
public final class DummyAdminWriter
        implements IAdminWriter {

	// Implemented ============================================================

	@Override
        public void ensureCvsDirectory(DirectoryObject directoryObject, String repositoryPath, CvsRoot cvsRoot, ICvsFileSystem cvsFileSystem) {
	}

	@Override
        public void setEntry(DirectoryObject directoryObject, Entry entry, ICvsFileSystem cvsFileSystem) {
	}

	@Override
        public void removeEntryForFile(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
	}

	@Override
        public void pruneDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
	}

	@Override
        public void setStickyTagForDirectory(DirectoryObject directoryObject, String tag, ICvsFileSystem cvsFileSystem) {
	}

	@Override
        public void editFile(FileObject fileObject, Entry entry, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) {
	}

	@Override
        public void uneditFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) {
	}

	@Override
        public void setEntriesDotStatic(DirectoryObject directoryObject, boolean set, ICvsFileSystem cvsFileSystem) {
	}

	@Override
        public void writeTemplateFile(DirectoryObject directoryObject, int fileLength, InputStream inputStream, IReaderFactory readerFactory, IClientEnvironment clientEnvironment) {
	}

	@Override
        public void directoryAdded(DirectoryObject directory, ICvsFileSystem cvsFileSystem) {
	}
}
