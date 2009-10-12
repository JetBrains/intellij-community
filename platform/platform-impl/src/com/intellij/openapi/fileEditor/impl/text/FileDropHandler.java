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
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.editor.EditorDropHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;

/**
* @author yole
*/
public class FileDropHandler implements EditorDropHandler {
  public boolean canHandleDrop(DataFlavor[] transferFlavors) {
    for (DataFlavor transferFlavor : transferFlavors) {
      if (transferFlavor.equals(DataFlavor.javaFileListFlavor)) {
        return true;
      }
    }
    return false;
  }

  public void handleDrop(Transferable t, final Project project) {
    if (project == null) {
      return;
    }
    java.util.List<File> fileList;
    try {
      //noinspection unchecked
      fileList = (java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
    }
    catch (Exception ex) {
      return;
    }
    if (fileList != null) {
      for (File file : fileList) {
        final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (vFile != null) {
          new OpenFileDescriptor(project, vFile).navigate(true);
        }
      }
    }
  }
}
