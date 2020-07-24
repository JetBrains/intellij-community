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

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.cvsoperations.common.RepositoryPathProvider;
import com.intellij.cvsSupport2.errorHandling.InvalidModuleDescriptionException;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.file.*;

import java.io.IOException;
import java.io.InputStream;

public class AdminWriterStoringRepositoryPath implements IAdminWriter, RepositoryPathProvider {
  private String myRepositoryPath = null;
  private String myModulePath = null;
  private final String myModuleName;
  private final String myCvsRoot;

  public AdminWriterStoringRepositoryPath(String moduleName, String cvsRoot) {
    myModuleName = moduleName;

    myCvsRoot = cvsRoot;
  }

  @Override
  public void writeTemplateFile(DirectoryObject directoryObject,
                                int fileLength,
                                InputStream inputStream,
                                IReaderFactory readerFactory,
                                IClientEnvironment clientEnvironment) throws IOException {
    CvsUtil.skip(inputStream, fileLength);
  }

  @Override
  public void editFile(FileObject fileObject, Entry entry, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) {
  }

  @Override
  public void setStickyTagForDirectory(DirectoryObject directoryObject, String tag, ICvsFileSystem cvsFileSystem) {
  }

  @Override
  public void removeEntryForFile(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
  }

  @Override
  public void setEntry(DirectoryObject directoryObject, Entry entry, ICvsFileSystem cvsFileSystem) {
  }

  @Override
  public void ensureCvsDirectory(DirectoryObject directoryObject, String repositoryPath, CvsRoot cvsRoot, ICvsFileSystem cvsFileSystem) {
    if (myRepositoryPath == null) {
      myRepositoryPath = repositoryPath;
      myModulePath = cvsFileSystem.getRelativeRepositoryPath(repositoryPath);
    }
  }

  @Override
  public void setEntriesDotStatic(DirectoryObject directoryObject, boolean set, ICvsFileSystem cvsFileSystem) {
  }

  @Override
  public void uneditFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) {
  }

  @Override
  public void pruneDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
  }

  @Override
  public void directoryAdded(DirectoryObject directory, ICvsFileSystem cvsFileSystem) {
  }

  @Override
  public String getRepositoryPath(String repository) {
    if (myRepositoryPath == null) throw new InvalidModuleDescriptionException(
      CvsBundle.message("error.mesage.cannot.expand.module", myModuleName), myCvsRoot);
    return myRepositoryPath;
  }

  public String getModulePath() {
    return myModulePath;
  }
}
