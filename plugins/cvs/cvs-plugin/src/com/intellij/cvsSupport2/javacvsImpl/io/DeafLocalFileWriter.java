// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.cvsSupport2.CvsUtil;
import org.netbeans.lib.cvsclient.file.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;

/**
 * author: lesya
 */
public final class DeafLocalFileWriter implements ILocalFileWriter{
  public final static ILocalFileWriter INSTANCE = new DeafLocalFileWriter();
  private DeafLocalFileWriter() {
  }

  @Override
  public void setNextFileDate(Date modifiedDate) {
  }

  @Override
  public void writeBinaryFile(FileObject fileObject,
                              int length,
                              InputStream inputStream,
                              boolean readOnly,
                              IFileReadOnlyHandler fileReadOnlyHandler,
                              ICvsFileSystem cvsFileSystem) throws IOException {
    CvsUtil.skip(inputStream, length);
  }

  @Override
  public void renameLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, String newFileName) {

  }

  @Override
  public void removeLocalFile(FileObject fileObject,
                              ICvsFileSystem cvsFileSystem,
                              IFileReadOnlyHandler fileReadOnlyHandler) {
  }

  @Override
  public void writeTextFile(FileObject fileObject,
                            int length,
                            InputStream inputStream,
                            boolean readOnly,
                            IReaderFactory readerFactory,
                            IFileReadOnlyHandler fileReadOnlyHandler,
                            IFileSystem fileSystem, final Charset charSet) throws IOException {
    CvsUtil.skip(inputStream, length);
  }

  @Override
  public void setNextFileMode(String nextFileMode) {
  }
}
