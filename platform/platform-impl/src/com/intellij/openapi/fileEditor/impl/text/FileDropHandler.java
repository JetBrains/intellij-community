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
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.editor.EditorDropHandler;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

/**
 * @author yole
 */
public class FileDropHandler implements EditorDropHandler {
  public boolean canHandleDrop(final DataFlavor[] transferFlavors) {
    return transferFlavors != null && FileCopyPasteUtil.isFileListFlavorSupported(transferFlavors);
  }

  public void handleDrop(@NotNull final Transferable t, @Nullable final Project project) {
    if (project == null || !FileCopyPasteUtil.isFileListFlavorSupported(t)) {
      return;
    }

    final List<File> fileList = FileCopyPasteUtil.getFileList(t);
    if (fileList != null) {
      openFiles(project, fileList);
    }
  }

  private static void openFiles(final Project project, final List<File> fileList) {
    final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (File file : fileList) {
      final VirtualFile vFile = fileSystem.refreshAndFindFileByIoFile(file);
      if (vFile != null) {
        new OpenFileDescriptor(project, vFile).navigate(true);
      }
    }
  }
}
