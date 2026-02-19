// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.tree;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tree.TreeVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

@ApiStatus.Internal
public final class FileNodeVisitor extends TreeVisitor.ByComponent<VirtualFile, VirtualFile> {

  public FileNodeVisitor(@NotNull VirtualFile file) {
    super(file, object -> {
      FileNode node = object instanceof FileNode ? (FileNode)object : null;
      return node == null ? null : node.getFile();
    });
  }

  @Override
  protected @NotNull Action visit(VirtualFile file) {
    return file == null ? Action.CONTINUE : super.visit(file);
  }

  @Override
  protected boolean contains(@NotNull VirtualFile pathFile, @NotNull VirtualFile thisFile) {
    return isAncestor(pathFile, thisFile, true);
  }
}
