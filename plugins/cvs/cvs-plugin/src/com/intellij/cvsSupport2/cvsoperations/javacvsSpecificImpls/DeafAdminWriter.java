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
package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import com.intellij.cvsSupport2.CvsUtil;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.file.*;

import java.io.IOException;
import java.io.InputStream;

/**
 * author: lesya
 */
public class DeafAdminWriter implements IAdminWriter{
  public void ensureCvsDirectory(DirectoryObject directoryObject, String repositoryPath, CvsRoot cvsRoot, ICvsFileSystem cvsFileSystem) {

  }

  public void setEntry(DirectoryObject directoryObject, Entry entry, ICvsFileSystem cvsFileSystem) {

  }

  public void removeEntryForFile(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {

  }

  public void pruneDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {

  }

  public void editFile(FileObject fileObject, Entry entry, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) {

  }

  public void uneditFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) {

  }

  public void setStickyTagForDirectory(DirectoryObject directoryObject, String tag, ICvsFileSystem cvsFileSystem) {

  }

  public void setEntriesDotStatic(DirectoryObject directoryObject, boolean set, ICvsFileSystem cvsFileSystem) {

  }

  public void writeTemplateFile(DirectoryObject directoryObject, int fileLength, InputStream inputStream, IReaderFactory readerFactory, IClientEnvironment clientEnvironment) throws IOException {
    CvsUtil.skip(inputStream, fileLength);
  }

  public void directoryAdded(DirectoryObject directory, ICvsFileSystem cvsFileSystem) {

  }
}
