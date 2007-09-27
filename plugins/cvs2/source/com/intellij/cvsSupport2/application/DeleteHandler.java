package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.RemoveLocallyFileOrDirectoryAction;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * author: lesya
 */

class DeleteHandler {

  private final Collection<String> myDeletedFiles = new HashSet<String>();
  private final DeletedCVSDirectoryStorage myDeletedStorage;
  private final Collection<VirtualFile> myDeletedFilesParents = new HashSet<VirtualFile>();
  private final Collection<String> myDeletedFilesPaths = new HashSet<String>();
  private final Project myProject;
  private final CvsStorageComponent myCvsStorageComponent;
  private final Collection<File> myFilesToDeleteEntry = new ArrayList<File>();


  public DeleteHandler(DeletedCVSDirectoryStorage deletedStorage,
                       Project project, CvsStorageComponent cvsStorageComponent) {
    myDeletedStorage = deletedStorage;
    myProject = project;
    myCvsStorageComponent = cvsStorageComponent;
  }

  public void execute() {
    if (CvsVcs2.getInstance(myProject).getRemoveConfirmation().getValue() != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      if (!myCvsStorageComponent.getIsActive()) return;
      removeFiles();
    }

    for (final VirtualFile myDeletedFilesParent : myDeletedFilesParents) {
      myDeletedFilesParent.refresh(true, true);
    }
  }

  private void removeFiles() {
    for (File file : myFilesToDeleteEntry) {
      if (!file.exists()) {
        CvsUtil.removeEntryFor(file);
      }
    }

    if (myDeletedFiles.isEmpty()) return;
    for (String s : myDeletedFiles) {
      FileUtil.delete(new File(s));
    }

    CvsContext context = new CvsContextAdapter() {
      public Project getProject() {
        return myProject;
      }

      public Collection<String> getDeletedFileNames() {
        return myDeletedFiles;
      }

    };

    RemoveLocallyFileOrDirectoryAction
      .createAutomaticallyAction()
      .actionPerformed(context);
  }

  private void collectAllDeletedFilesFrom(VirtualFile directory) {
    VirtualFile[] children = directory.getChildren();

    if (children == null) return;

    for (VirtualFile child : children) {
      if (!child.isDirectory() && CvsUtil.fileIsUnderCvs(child)) {
        addFile(child);
      }
      else if (!DeletedCVSDirectoryStorage.isAdminDir(child)) {
        collectAllDeletedFilesFrom(child);
      }
    }
  }

  public void addDeletedRoot(VirtualFile file) {
    myDeletedFilesParents.add(file.getParent());
    myDeletedFilesPaths.add(file.getPath());
    if (file.isDirectory()) {
      collectAllDeletedFilesFrom(file);
    }
    else {
      if (CvsUtil.fileIsUnderCvs(file) && !CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(file.getParent()).isOffline()) {
        addFile(file);
      }
    }

  }

  private void addFile(VirtualFile file) {
    VirtualFile adminDirectoryForFile = file.getParent().findChild(CvsUtil.CVS);
    if (adminDirectoryForFile != null) {
      if (CvsUtil.fileIsUnderCvs(file)) {
        if (CvsUtil.fileExistsInCvs(file)) {
          myDeletedFiles.add(file.getPath());
        }
        else {
          myFilesToDeleteEntry.add(CvsVfsUtil.getFileFor(file));
        }
      }
    }

  }

}


