/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.AddFileOrDirectoryAction;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * author: lesya
 */
class AddHandler {
  private final Collection<VirtualFile> myAddedFiles = new ArrayList<>();
  private final Collection<VirtualFile> myAllFiles = new ArrayList<>();
  private final Collection<File> myIOFiles = new ArrayList<>();
  private final Project myProject;
  private final CvsStorageComponent myCvsStorageComponent;

  public AddHandler(@NotNull Project project, CvsStorageComponent cvsStorageComponent) {
    myProject = project;
    myCvsStorageComponent = cvsStorageComponent;
  }

  public void addFile(VirtualFile file) {
    myAllFiles.add(file);
  }

  public void addFile(File file) {
    myIOFiles.add(file);
  }

  @SuppressWarnings({"UnnecessaryContinue"})
  public void execute() {
    //for(final File file: myIOFiles) {
    //  ApplicationManager.getApplication().runWriteAction(new Runnable() {
    //    public void run() {
    //      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    //      if (virtualFile != null) {
    //        myAllFiles.add(virtualFile);
    //      }
    //    }
    //  });
    //}
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    final CvsEntriesManager cvsEntriesManager = CvsEntriesManager.getInstance();
    for (VirtualFile file : myAllFiles) {
      if (changeListManager.isIgnoredFile(file)) {
        continue;
      }
      else if (!CvsUtil.fileIsUnderCvs(file.getParent())) {
        continue;
      }
      else if (CvsUtil.fileIsLocallyRemoved(file)) {
        CvsUtil.restoreFile(file);
      }
      else if (CvsUtil.fileIsUnderCvs(file)) {
        continue;
      }
      else if (cvsEntriesManager.getCvsConnectionSettingsFor(file.getParent()).isOffline()) {
        continue;
      }
      else if (cvsEntriesManager.fileIsIgnored(file)) {
        continue;
      }
      else {
        myAddedFiles.add(file);
      }

    }

    if (!myAddedFiles.isEmpty()) {
      if (CvsVcs2.getInstance(myProject).getAddConfirmation().getValue() != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
        final Runnable addRunnable = () -> {
          if (!myCvsStorageComponent.getIsActive()) return;
          AddFileOrDirectoryAction.createActionToAddNewFileAutomatically()
            .actionPerformed(createDataContext(myAddedFiles));
        };
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          addRunnable.run();
        }
        else {
          WaitForProgressToShow.runOrInvokeLaterAboveProgress(addRunnable, null, myProject);
        }
      }
    }
  }

  private CvsContext createDataContext(final Collection<VirtualFile> files) {
    final Iterator<VirtualFile> first = files.iterator();
    return new CvsContextAdapter() {
      public Project getProject() {
        return myProject;
      }

      public VirtualFile getSelectedFile() {
        return first.hasNext() ? first.next() : null;
      }

      @NotNull
      public VirtualFile[] getSelectedFiles() {
        return VfsUtil.toVirtualFileArray(files);
      }
    };
  }

}
