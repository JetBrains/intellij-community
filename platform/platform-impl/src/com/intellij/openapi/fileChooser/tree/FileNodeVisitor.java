// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.tree;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tree.TreeVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

public class FileNodeVisitor extends TreeVisitor.ByComponent<VirtualFile, VirtualFile> {

  public FileNodeVisitor(@NotNull VirtualFile file) {
    super(file, object -> {
      FileNode node = object instanceof FileNode ? (FileNode)object : null;
      return node == null ? null : node.getFile();
    });
  }

  @NotNull
  @Override
  protected Action visit(VirtualFile file) {
    return file == null ? Action.CONTINUE : super.visit(file);
  }

  @Override
  protected boolean contains(@NotNull VirtualFile pathFile, @NotNull VirtualFile thisFile) {
    return isAncestor(pathFile, thisFile, true);
  }
}
