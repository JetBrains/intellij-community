package org.netbeans.lib.cvsclient.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.nio.charset.Charset;

/**
 * @author  Thomas Singer
 */
public final class DummyLocalFileWriter
        implements ILocalFileWriter {

	// Implemented ============================================================

	public void writeTextFile(FileObject fileObject, int length, InputStream inputStream, boolean readOnly, IReaderFactory readerFactory,
                                  IFileReadOnlyHandler fileReadOnlyHandler, IFileSystem fileSystem, final Charset charSet) throws IOException {
	}

	public void writeBinaryFile(FileObject fileObject, int length, InputStream inputStream, boolean readOnly, IFileReadOnlyHandler fileReadOnlyHandler, ICvsFileSystem cvsFileSystem) throws IOException {
	}

	public void removeLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
	}

	public void renameLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, String newFileName) throws IOException {
	}

	public void setNextFileDate(Date modifiedDate) {
	}

	public void setNextFileMode(String nextFileMode) {
	}
}
