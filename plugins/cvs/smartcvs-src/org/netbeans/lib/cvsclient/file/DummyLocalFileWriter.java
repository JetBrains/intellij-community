package org.netbeans.lib.cvsclient.file;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;

/**
 * @author  Thomas Singer
 */
public final class DummyLocalFileWriter
        implements ILocalFileWriter {

	// Implemented ============================================================

	@Override
        public void writeTextFile(FileObject fileObject, int length, InputStream inputStream, boolean readOnly, IReaderFactory readerFactory,
                                  IFileReadOnlyHandler fileReadOnlyHandler, IFileSystem fileSystem, final Charset charSet) {
	}

	@Override
        public void writeBinaryFile(FileObject fileObject, int length, InputStream inputStream, boolean readOnly, IFileReadOnlyHandler fileReadOnlyHandler, ICvsFileSystem cvsFileSystem) {
	}

	@Override
        public void removeLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) {
	}

	@Override
        public void renameLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, String newFileName) {
	}

	@Override
        public void setNextFileDate(Date modifiedDate) {
	}

	@Override
        public void setNextFileMode(String nextFileMode) {
	}
}
