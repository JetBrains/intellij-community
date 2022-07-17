// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.ui.ProductIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class GotoProjectDirAction extends FileChooserAction {
  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    boolean enabled = getProjectPath(e) != null;
    e.getPresentation().setEnabledAndVisible(enabled);
    if (enabled) {
      e.getPresentation().setIcon(ProductIcons.getInstance().getProjectIcon());
    }
  }

  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    Path path = getProjectPath(e);
    if (path != null) {
      panel.load(path);
    }
  }

  private static @Nullable Path getProjectPath(AnActionEvent e) {
    VirtualFile dir = getProjectDir(e);
    return dir != null ? dir.getFileSystem().getNioPath(dir) : null;
  }

  @Override
  protected void update(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    VirtualFile projectPath = getProjectDir(e);
    boolean enabled = projectPath != null && fileSystemTree.isUnderRoots(projectPath);
    e.getPresentation().setEnabledAndVisible(enabled);
    if (enabled) {
      e.getPresentation().setIcon(ProductIcons.getInstance().getProjectIcon());
    }
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    VirtualFile projectPath = getProjectDir(e);
    if (projectPath != null) {
      fileSystemTree.select(projectPath, () -> fileSystemTree.expand(projectPath, null));
    }
  }

  private static @Nullable VirtualFile getProjectDir(AnActionEvent e) {
    VirtualFile projectFileDir = e.getData(PlatformCoreDataKeys.PROJECT_FILE_DIRECTORY);
    return projectFileDir != null && projectFileDir.isValid() ? projectFileDir : null;
  }
}
