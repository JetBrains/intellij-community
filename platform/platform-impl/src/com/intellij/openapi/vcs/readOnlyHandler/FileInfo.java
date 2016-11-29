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
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ListWithSelection;

class FileInfo {
  private final VirtualFile myFile;
  private final ListWithSelection<HandleType> myHandleType = new ListWithSelection<>();

  public FileInfo(VirtualFile file, Project project) {
    myFile = file;
    myHandleType.add(HandleType.USE_FILE_SYSTEM);
    myHandleType.selectFirst();
    for(HandleTypeFactory factory: Extensions.getExtensions(HandleTypeFactory.EP_NAME, project)) {
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
