package com.intellij.cvsSupport2.application;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.RemoveLocallyFileOrDirectoryAction;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.ui.RestoreDirectoriesConfirmationDialog;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
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
    try {
      boolean restored = false;
      for (final String myDeletedFilesPath : myDeletedFilesPaths) {
        restored |= myDeletedStorage.restore((String)myDeletedFilesPath);
      }
      myDeletedStorage.purgeDirsWithNoEntries();
      final boolean showWarning = restored;

      if (CvsVcs2.getInstance(myProject).getRemoveConfirmation().getValue() != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (!myCvsStorageComponent.getIsActive()) return;
            removeFiles();
          }
        }, ModalityState.NON_MODAL);

      }

      final int[] myRefreshedParents = new int[]{myDeletedFilesParents.size()};
      for (final VirtualFile myDeletedFilesParent : myDeletedFilesParents) {
        myDeletedFilesParent.refresh(true, true, new Runnable() {
          public void run() {
            myRefreshedParents[0]++;
            if (myRefreshedParents[0] == myDeletedFilesParents.size()) {
              if (showWarning) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    if (!myCvsStorageComponent.getIsActive()) return;
                    if (CvsApplicationLevelConfiguration.getInstance().SHOW_RESTORE_DIRECTORIES_CONFIRMATION) {
                      new RestoreDirectoriesConfirmationDialog().show();
                    }
                  }
                });
              }
            }
          }
        });
      }


    }
    catch (final IOException e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showMessageDialog(CvsBundle.message("message.error.cannot.restore.cvs.admin.directories", e.getLocalizedMessage()),
                                     CvsBundle.message("message.error.cannot.restore.cvs.admin.directories.title"),
                                     Messages.getErrorIcon());
        }
      });
    }
  }

  public void removeFiles() {

    for (File file : myFilesToDeleteEntry) {
      if (!file.exists()) {
        CvsUtil.removeEntryFor(file);
      }
    }

    final ArrayList<String> reallyDeletedFiles = new ArrayList<String>();
    for (String s : myDeletedFiles) {
      if (!new File(s).exists()) {
        reallyDeletedFiles.add(s);
      }
    }

    if (reallyDeletedFiles.isEmpty()) return;

    CvsContext context = new CvsContextAdapter() {
      public Project getProject() {
        return myProject;
      }

      public Collection<String> getDeletedFileNames() {
        return reallyDeletedFiles;
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


