package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.AddFileOrDirectoryAction;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextAdapter;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Options;

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
    for (Iterator each = myAllFiles.iterator(); each.hasNext();) {
      VirtualFile file = (VirtualFile)each.next();
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
      if (CvsConfiguration.getInstance(myProject).ON_FILE_ADDING != Options.DO_NOTHING) {
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

  private CvsContext createDataContext(final Collection files) {
    final Iterator first = files.iterator();
    return new CvsContextAdapter() {
      public Project getProject() {
        return myProject;
      }

      public VirtualFile getSelectedFile() {
        return (VirtualFile)(first.hasNext() ? first.next() : null);
      }

      public VirtualFile[] getSelectedFiles() {
        return (VirtualFile[])files.toArray(new VirtualFile[files.size()]);
      }
    };
  }

}
