package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.AddFileOrDirectoryAction;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * author: lesya
 */
class AddHandler {

  private final Collection<VirtualFile> myAddedFiles = new ArrayList<VirtualFile>();
  private final Collection<VirtualFile> myAllFiles = new ArrayList<VirtualFile>();
  private final Project myProject;
  private final CvsStorageComponent myCvsStorageComponent;

  public AddHandler(Project project, CvsStorageComponent cvsStorageComponent) {
    myProject = project;
    myCvsStorageComponent = cvsStorageComponent;
  }

  public void addFile(VirtualFile file) {
    myAllFiles.add(file);
  }

  public void execute() {
    for (VirtualFile file : myAllFiles) {
      if (!CvsUtil.fileIsUnderCvs(file.getParent())) {
        continue;
      }
      else if (CvsUtil.fileIsLocallyRemoved(file)) {
        CvsUtil.restoreFile(file);
      }
      else if (CvsUtil.fileIsUnderCvs(file)) {
        continue;
      }
      else {
        myAddedFiles.add(file);
      }

    }

    if (!myAddedFiles.isEmpty()) {
      if (CvsVcs2.getInstance(myProject).getAddConfirmation().getValue() != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (!myCvsStorageComponent.getIsActive()) return;
                AddFileOrDirectoryAction.createActionToAddNewFileAutomatically()
                  .actionPerformed(createDataContext(myAddedFiles));
              }
            });
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

      public VirtualFile[] getSelectedFiles() {
        return files.toArray(new VirtualFile[files.size()]);
      }
    };
  }

}
