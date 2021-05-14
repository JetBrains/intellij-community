// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      @Override
      public void dragEnter(final DropTargetDragEvent event) {
      }

      @Override
      public void dragOver(final DropTargetDragEvent event) {
      }

      @Override
      public void dropActionChanged(final DropTargetDragEvent event) {
      }

      @Override
      public void dragExit(final DropTargetEvent dte) {
      }

      @Override
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
    void dropFiles(List<? extends VirtualFile> files);
  }
}
