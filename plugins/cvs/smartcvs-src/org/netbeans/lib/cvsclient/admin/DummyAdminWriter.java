package org.netbeans.lib.cvsclient.admin;

import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.file.*;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author  Thomas Singer
 */
public final class DummyAdminWriter
        implements IAdminWriter {

	// Implemented ============================================================

	public void ensureCvsDirectory(DirectoryObject directoryObject, String repositoryPath, CvsRoot cvsRoot, ICvsFileSystem cvsFileSystem) {
	}

	public void setEntry(DirectoryObject directoryObject, Entry entry, ICvsFileSystem cvsFileSystem) {
	}

	public void removeEntryForFile(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
	}

	public void pruneDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
	}

	public void setStickyTagForDirectory(DirectoryObject directoryObject, String tag, ICvsFileSystem cvsFileSystem) {
	}

	public void editFile(FileObject fileObject, Entry entry, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) {
	}

	public void uneditFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) {
	}

	public void setEntriesDotStatic(DirectoryObject directoryObject, boolean set, ICvsFileSystem cvsFileSystem) {
	}

	public void writeTemplateFile(DirectoryObject directoryObject, int fileLength, InputStream inputStream, IReaderFactory readerFactory, IClientEnvironment clientEnvironment) {
	}

	public void directoryAdded(DirectoryObject directory, ICvsFileSystem cvsFileSystem) {
	}
}
