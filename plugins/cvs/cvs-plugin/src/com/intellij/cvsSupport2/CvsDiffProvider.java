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
package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.changeBrowser.CvsBinaryContentRevision;
import com.intellij.cvsSupport2.changeBrowser.CvsContentRevision;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.File;

public class CvsDiffProvider implements DiffProvider{
  private final Project myProject;

  public CvsDiffProvider(final Project project) {
    myProject = project;
  }

  @Override
  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    final Entry entry = CvsEntriesManager.getInstance().getEntryFor(file);
    if (entry == null) return null;
    return new CvsRevisionNumber(entry.getRevision());
  }

  @Override
  public ItemLatestState getLastRevision(VirtualFile virtualFile) {
    //return getLastRevision(CvsVfsUtil.getFileFor(virtualFile));
    if (virtualFile.getParent() == null) {
      return new ItemLatestState(new CvsRevisionNumber("HEAD"), true, true);
    }
    return getLastState(virtualFile.getParent(), virtualFile.getName());
  }

  @Override
  public ContentRevision createFileContent(final VcsRevisionNumber revisionNumber, VirtualFile selectedFile) {
    if ((revisionNumber instanceof CvsRevisionNumber)) {
      final CvsConnectionSettings settings = CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(selectedFile.getParent());
      final File file = new File(CvsUtil.getModuleName(selectedFile));
      final CvsRevisionNumber cvsRevisionNumber = ((CvsRevisionNumber)revisionNumber);
      final RevisionOrDate versionInfo;
      if (cvsRevisionNumber.getDateOrRevision() != null) {
        versionInfo = RevisionOrDateImpl.createOn(cvsRevisionNumber.getDateOrRevision());
      }
      else {
        versionInfo = new SimpleRevision(cvsRevisionNumber.asString());
      }

      if (selectedFile.getFileType().isBinary()) {
        return new CvsBinaryContentRevision(file, file, versionInfo, settings, myProject);
      }
      else {
        return new CvsContentRevision(file, file, versionInfo, settings, myProject);
      }

    } else {
      return null;
    }
  }

  @Override
  public ItemLatestState getLastRevision(FilePath filePath) {
    //return getLastRevision(filePath.getIOFile());
    VirtualFile parent = filePath.getVirtualFileParent();
    if (parent == null) {
      parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(filePath.getParentPath().getIOFile());
    }
    if (parent != null) {
      return getLastState(parent, filePath.getName());
    }
    return new ItemLatestState(new CvsRevisionNumber("HEAD"), true, true);
  }

  @Override
  public VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
    // todo
    return null;
  }

  private ItemLatestState getLastState(final VirtualFile parent, final String name) {
    final Entry entry = CvsEntriesManager.getInstance().getEntryFor(parent, name);
    if (entry == null) return new ItemLatestState(new CvsRevisionNumber("HEAD"), true, true);

    final String stickyHead = new StickyHeadGetter.MyStickyBranchHeadGetter(entry.getRevision(), myProject).getHead(parent, name);
    if (stickyHead == null) {
      return new ItemLatestState(new CvsRevisionNumber("HEAD"), true, true);
    }
    return new ItemLatestState(new CvsRevisionNumber(stickyHead), (! entry.isRemoved()), false);
  }
}
