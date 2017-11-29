/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public void renameLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, String newFileName) {

  }

  public void removeLocalFile(FileObject fileObject,
                              ICvsFileSystem cvsFileSystem,
                              IFileReadOnlyHandler fileReadOnlyHandler) {
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
