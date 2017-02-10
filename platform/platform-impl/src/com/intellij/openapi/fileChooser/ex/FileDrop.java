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
package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.dnd.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileDrop {
  public FileDrop(JComponent c, final Target target) {
    final DropTargetListener listener = new DropTargetListener() {
      public void dragEnter(final DropTargetDragEvent event) {
      }

      public void dragOver(final DropTargetDragEvent event) {
      }

      public void dropActionChanged(final DropTargetDragEvent event) {
      }

      public void dragExit(final DropTargetEvent dte) {
      }

      public void drop(final DropTargetDropEvent event) {
        event.acceptDrop(event.getDropAction());

        final List<File> fileList = FileCopyPasteUtil.getFileList(event.getTransferable());
        if (fileList == null) return;

        final List<VirtualFile> files = new ArrayList<>();
        final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
        for (File file : fileList) {
          final VirtualFile vFile = fileSystem.findFileByIoFile(file);
          if (vFile != null && vFile.exists() && target.getDescriptor().isFileVisible(vFile, target.isHiddenShown())) {
            files.add(vFile);
          }
        }

        if (files.size() > 0) {
          target.dropFiles(files);
        }
      }
    };

    new DropTarget(c, TransferHandler.COPY_OR_MOVE, listener, true);
  }

  public interface Target {
    FileChooserDescriptor getDescriptor();
    boolean isHiddenShown();
    void dropFiles(List<VirtualFile> files);
  }
}
