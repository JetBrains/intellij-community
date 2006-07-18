package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.history.ComparableVcsRevisionOnOperation;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.io.File;

public class CvsFile extends CvsElement {
  private VirtualFile myVirtualFile;
  private final CvsEnvironment myEnvironment;
  private final Project myProject;

  public CvsFile(String name, CvsEnvironment env, Project project) {
    super(getFileIcon(name), project);
    myEnvironment = env;
    myProject = project;
  }

  private static Icon getFileIcon(String name) {
    try {
      return FileTypeManager.getInstance().getFileTypeByFileName(name).getIcon();
    }
    catch (Exception ex) {
      return StdFileTypes.UNKNOWN.getIcon();
    }
  }

  public VirtualFile getVirtualFile() {
    if (myVirtualFile == null) {
      myVirtualFile = createVirtualFile();
    }
    return myVirtualFile;
  }

  private VirtualFile createVirtualFile() {
    return new VcsVirtualFile(myPath,
                              new ComparableVcsRevisionOnOperation(new GetFileContentOperation(getCvsLightFile(),
                                                                                               myEnvironment,
                                                                                               myEnvironment.getRevisionOrDate()
                              ),
                                                                   myProject),
                              VcsFileSystem.getInstance());
  }

  public File getCvsLightFile() {
    return new File(((CvsElement)getParent()).createPathForChild(myName));
  }

  public boolean isLeaf() {
    return true;
  }


  public int getChildCount() {
    return 0;
  }
}
