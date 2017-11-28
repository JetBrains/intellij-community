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
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.cvsstatuses.CvsStatusProvider;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.AdminReader;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.file.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * author: lesya
 */
public class AdminReaderOnCache implements IAdminReader {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.AdminReaderOnCache");

  private final IAdminReader ourStandardAdminReader = new AdminReader(CvsApplicationLevelConfiguration.getCharset());
  private final CvsEntriesManager myCvsEntriesManager = CvsEntriesManager.getInstance();

  public Entry getEntry(final AbstractFileObject fileObject, final ICvsFileSystem cvsFileSystem) {
    ProgressManager.checkCanceled();
    setProgressText(CvsBundle.message("progress.text.scanning.directory", cvsFileSystem.getLocalFileSystem().getFile(fileObject.getParent()).getAbsolutePath()));
    File file = cvsFileSystem.getAdminFileSystem().getFile(fileObject);
    Entry result = myCvsEntriesManager.getEntryFor(CvsVfsUtil.findFileByIoFile(file.getParentFile()), file.getName());
    if (result == null) {
      return null;
    }
    else {
      try {
        return (Entry)result.clone();
      }
      catch (CloneNotSupportedException e) {
        LOG.error(e);
        return null;
      }
    }
  }

  public Collection<Entry> getEntries(final DirectoryObject directoryObject, final ICvsFileSystem cvsFileSystem) {
    setProgressText(CvsBundle.message("progress.text.scanning.directory", cvsFileSystem.getLocalFileSystem().getFile(directoryObject).getAbsolutePath()));
    ProgressManager.checkCanceled();
    File parent = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);
    Collection<Entry> entries = myCvsEntriesManager.getEntriesIn(CvsVfsUtil.findFileByIoFile(parent));
    ArrayList<Entry> copy = new ArrayList<>();
    for (final Entry entry : entries) {
      try {
        copy.add((Entry)entry.clone());
      }
      catch (CloneNotSupportedException e) {
        LOG.error(e);
      }
    }
    return copy;
  }

  private void setProgressText(String text) {
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator == null) return;
    progressIndicator.setText2(text);
  }

  public String getRepositoryForDirectory(DirectoryObject directoryObject, String repository, ICvsFileSystem cvsFileSystem) {
    File parent = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);
    VirtualFile virtualFile = CvsVfsUtil.findFileByIoFile(parent);
    String repositoryDerectory = myCvsEntriesManager.getRepositoryFor(virtualFile);
    if (repositoryDerectory == null) {
      String parentRepository = myCvsEntriesManager.getRepositoryFor(virtualFile.getParent());
      repositoryDerectory = parentRepository + "/" + virtualFile.getName();
    }
    if (StringUtil.startsWithChar(repositoryDerectory, '/')) {
      return repositoryDerectory;
    }
    else {
      return FileUtils.ensureTrailingSlash(repository) + repositoryDerectory;
    }
  }

  public String getStickyTagForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    File parent = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);
    return myCvsEntriesManager.getStickyTagFor(CvsVfsUtil.findFileByIoFile(parent));
  }

  public boolean hasCvsDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    return ourStandardAdminReader.hasCvsDirectory(directoryObject, cvsFileSystem);
  }

  public boolean isModified(FileObject fileObject, Date entryLastModified, ICvsFileSystem cvsFileSystem) {
    File file = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
    VirtualFile virtualFile = CvsVfsUtil.findFileByIoFile(file);
    if (virtualFile == null) {
      return !CvsStatusProvider.timeStampsAreEqual(entryLastModified.getTime(), file.lastModified());
    }
    else {
      return !CvsStatusProvider.timeStampsAreEqual(entryLastModified.getTime(), CvsVfsUtil.getTimeStamp(virtualFile));
    }
  }

    public boolean isStatic(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
        return ourStandardAdminReader.isStatic(directoryObject, cvsFileSystem);
    }

}
