package org.netbeans.lib.cvsclient.file;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.SmartCvsSrcBundle;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Date;

/**
 * @author  Thomas Singer
 */
public final class LocalFileWriter
        implements ILocalFileWriter {

        // Constants ==============================================================

        private static final int BUFFER_SIZE = 128 * 1024;

        // Fields =================================================================

        private final IReceiveTextFilePreprocessor receiveTextFilePreprocessor;

        private Date modifiedDate;
        private String nextFileMode;
  @NonNls private static final String RECEIVING_TMP_FILE_NAME = "receiving";

  // Setup ==================================================================

        public LocalFileWriter(IReceiveTextFilePreprocessor receiveTextFilePreprocessor) {
                BugLog.getInstance().assertNotNull(receiveTextFilePreprocessor);

                this.receiveTextFilePreprocessor = receiveTextFilePreprocessor;
        }

        // Implemented ============================================================

        public void writeTextFile(FileObject fileObject, int length, InputStream inputStream, boolean readOnly, IReaderFactory readerFactory,
                                  IFileReadOnlyHandler fileReadOnlyHandler, IFileSystem fileSystem, final Charset charSet) throws IOException {
                final File localFile = fileSystem.getFile(fileObject);
                localFile.getParentFile().mkdirs();
                if (localFile.exists()) {
                        deleteFile(localFile, fileReadOnlyHandler);
                }
                else {
                        localFile.createNewFile();
                }

                // For CRLF conversion, we have to read the file
                // into a temp file, then do the conversion. This is because we cannot
                // perform a sequence of readLines() until we've read the file from
                // the server - the file transmission is not followed by a newline.
                // Bah.
                final File tempFile = File.createTempFile(RECEIVING_TMP_FILE_NAME, null);
                try {
                        writeFile(tempFile, length, inputStream);

                        receiveTextFilePreprocessor.copyTextFileToLocation(tempFile, localFile, readerFactory, charSet);

                        setModifiedDateAndMode(localFile, fileReadOnlyHandler);
                }
                finally {
                        tempFile.delete();
                }

                fileReadOnlyHandler.setFileReadOnly(localFile, readOnly);
        }

        public void writeBinaryFile(FileObject fileObject, int length, InputStream inputStream, boolean readOnly, IFileReadOnlyHandler fileReadOnlyHandler, ICvsFileSystem cvsFileSystem) throws IOException {
                final File localFile = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
                localFile.getParentFile().mkdirs();

                deleteFile(localFile, fileReadOnlyHandler);

                writeFile(localFile, length, inputStream);
                setModifiedDateAndMode(localFile, fileReadOnlyHandler);

                fileReadOnlyHandler.setFileReadOnly(localFile, readOnly);
        }

        public void removeLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
                final File file = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
                deleteFile(file, fileReadOnlyHandler);
        }

        public void renameLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, String newFileName) {
                final File originalFile = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
                final File targetFile = new File(originalFile.getParentFile(), newFileName);
                try {
                    FileUtil.rename(originalFile, targetFile);
                }
                catch (IOException e) {
                    //ignore
                }
        }

        /**
         * Set the modified date of the next file to be written.
         * The next call to writeFile will use this date.
         *
         * @param modifiedDate the date the file should be marked as modified
         */
        public void setNextFileDate(Date modifiedDate) {
                this.modifiedDate = modifiedDate;
        }

        public void setNextFileMode(String nextFileMode) {
                this.nextFileMode = nextFileMode;
        }

        // Utils ==================================================================

        private static void deleteFile(File file, IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
                if (!file.exists()) {
                        return;
                }

                if (!file.canWrite()) {
                        fileReadOnlyHandler.setFileReadOnly(file, false);
                }
                if (!file.delete()) {
                        throw new IOException(SmartCvsSrcBundle.message("could.not.delete.file.error.message", file));
                }
        }

        private static void writeFile(File file, int length, InputStream inputStream) throws IOException {
                if (length == 0) {
                        file.createNewFile();
                        return;
                }

                final OutputStream fileOutputStream = new FileOutputStream(file);
                try {
                        final byte[] chunk = new byte[BUFFER_SIZE];
                        int size = length;
                        while (size > 0) {
                                final int bytesToRead = Math.min(size, chunk.length);
                                final int bytesRead = inputStream.read(chunk, 0, bytesToRead);
                                if (bytesRead < 0) {
                                        break;
                                }

                                size -= bytesRead;
                                fileOutputStream.write(chunk, 0, bytesRead);
                        }
                }
                finally {
                        try {
                                fileOutputStream.close();
                        }
                        catch (IOException ex) {
                                // ignore
                        }
                }
        }

        private void setModifiedDateAndMode(File localFile, IFileReadOnlyHandler readOnlyHandler) throws IOException {
                if (modifiedDate != null) {
                        localFile.setLastModified(modifiedDate.getTime());
                        modifiedDate = null;
                }

                if (nextFileMode != null) {
                        readOnlyHandler.setFileReadOnly(localFile, FileUtils.isReadOnlyMode(nextFileMode));
                        nextFileMode = null;
                }
        }
}
