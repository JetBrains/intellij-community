// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private final CvsStorageSupportingDeletionComponent myCvsStorageComponent;

  AddHandler(@NotNull Project project, CvsStorageSupportingDeletionComponent cvsStorageComponent) {
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
      @Override
      public Project getProject() {
        return myProject;
      }

      @Override
      public VirtualFile getSelectedFile() {
        return first.hasNext() ? first.next() : null;
      }

      @Override
      public VirtualFile @NotNull [] getSelectedFiles() {
        return VfsUtil.toVirtualFileArray(files);
      }
    };
  }

}
