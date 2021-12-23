// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class GotoHomeAction extends FileChooserAction implements LightEditCompatible {
  @Override
  protected void update(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
      presentation.setEnabled(userHomeDir != null && fileSystemTree.isUnderRoots(userHomeDir));
    }
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
    if (userHomeDir != null) {
      fileSystemTree.select(userHomeDir, () -> fileSystemTree.expand(userHomeDir, null));
    }
  }
}
