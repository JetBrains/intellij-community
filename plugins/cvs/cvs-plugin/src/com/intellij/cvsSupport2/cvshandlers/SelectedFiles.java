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
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */

class SelectedFiles extends AnyProcessedFiles {
  private final Collection<VirtualFile> myFiles = new ArrayList<>();

  public SelectedFiles(FilePath[] files) {
    for (int i = 0; i < files.length; i++) {
      FilePath file = files[i];
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        myFiles.add(virtualFile);
      }
      else {
        myFiles.add(file.getVirtualFileParent());
      }
    }
  }

  public SelectedFiles(VirtualFile[] files) {
    ContainerUtil.addAll(myFiles, files);
  }


  public Collection<VirtualFile> getFiles() {
    return myFiles;
  }
}
