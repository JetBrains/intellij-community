// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ListWithSelection;

final class FileInfo {
  private final VirtualFile myFile;
  private final ListWithSelection<HandleType> myHandleType = new ListWithSelection<>();

  FileInfo(VirtualFile file, Project project) {
    myFile = file;
    myHandleType.add(HandleType.USE_FILE_SYSTEM);
    myHandleType.selectFirst();
    for(HandleTypeFactory factory: HandleTypeFactory.EP_NAME.getExtensions(project)) {
      final HandleType handleType = factory.createHandleType(file);
      if (handleType != null) {
        myHandleType.add(handleType);
        myHandleType.select(handleType);
      }
    }
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public HandleType getSelectedHandleType() {
    return myHandleType.getSelection();
  }

  public boolean hasVersionControl() {
    return myHandleType.size() > 1;
  }

  public ListWithSelection<HandleType> getHandleType(){
    return myHandleType;
  }
}
