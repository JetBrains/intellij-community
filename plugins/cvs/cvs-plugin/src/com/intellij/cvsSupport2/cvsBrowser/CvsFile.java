/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.history.ComparableVcsRevisionOnOperation;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
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
    super(name, getFileIcon(name));
    myEnvironment = env;
    myProject = project;
  }

  private static Icon getFileIcon(String name) {
    try {
      return FileTypeManager.getInstance().getFileTypeByFileName(name).getIcon();
    }
    catch (Exception ex) {
      return FileTypes.UNKNOWN.getIcon();
    }
  }

  @Override
  public VirtualFile getVirtualFile() {
    if (myVirtualFile == null) {
      myVirtualFile = createVirtualFile();
    }
    return myVirtualFile;
  }

  private VirtualFile createVirtualFile() {
    return new VcsVirtualFile(myPath,
                              new ComparableVcsRevisionOnOperation(
                                new GetFileContentOperation(getCvsLightFile(), myEnvironment, myEnvironment.getRevisionOrDate()),
                                myProject),
                              VcsFileSystem.getInstance());
  }

  @Override
  public File getCvsLightFile() {
    return new File(((CvsElement)getParent()).createPathForChild(myName));
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  @Override
  public int getChildCount() {
    return 0;
  }
}
