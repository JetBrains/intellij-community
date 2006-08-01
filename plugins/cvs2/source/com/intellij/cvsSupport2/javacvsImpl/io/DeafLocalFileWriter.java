package com.intellij.cvsSupport2.javacvsImpl.io;

import org.netbeans.lib.cvsclient.file.*;

import java.util.Date;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import com.intellij.cvsSupport2.CvsUtil;

/**
 * author: lesya
 */
public class DeafLocalFileWriter implements ILocalFileWriter{
  public final static ILocalFileWriter INSTANCE = new DeafLocalFileWriter();
  private DeafLocalFileWriter() {
  }

  public void setNextFileDate(Date modifiedDate) {
  }

  public void writeBinaryFile(FileObject fileObject,
                              int length,
                              InputStream inputStream,
                              boolean readOnly,
                              IFileReadOnlyHandler fileReadOnlyHandler,
                              ICvsFileSystem cvsFileSystem) throws IOException {
    CvsUtil.skip(inputStream, length);
  }

  public void renameLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, String newFileName)
    throws IOException {

  }

  public void removeLocalFile(FileObject fileObject,
                              ICvsFileSystem cvsFileSystem,
                              IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
  }

  public void writeTextFile(FileObject fileObject,
                            int length,
                            InputStream inputStream,
                            boolean readOnly,
                            IReaderFactory readerFactory,
                            IFileReadOnlyHandler fileReadOnlyHandler,
                            IFileSystem fileSystem, final Charset charSet) throws IOException {
    CvsUtil.skip(inputStream, length);
  }

  public void setNextFileMode(String nextFileMode) {
  }
}
