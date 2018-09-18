// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class GotoProjectDirectory extends FileChooserAction {
  private static final Icon ourIcon = PlatformUtils.isJetBrainsProduct()
                                   ? AllIcons.Nodes.IdeaProject
                                   : IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl());

  @Override
  protected void actionPerformed(final FileSystemTree fileSystemTree, final AnActionEvent e) {
    final VirtualFile projectPath = getProjectDir(e);
    if (projectPath != null) {
      fileSystemTree.select(projectPath, () -> fileSystemTree.expand(projectPath, null));
    }
  }

  @Override
  protected void update(final FileSystemTree fileSystemTree, final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setIcon(ourIcon);
    final VirtualFile projectPath = getProjectDir(e);
    presentation.setEnabled(projectPath != null && fileSystemTree.isUnderRoots(projectPath));
  }

  @Nullable
  private static VirtualFile getProjectDir(final AnActionEvent e) {
    final VirtualFile projectFileDir = e.getData(PlatformDataKeys.PROJECT_FILE_DIRECTORY);
    return projectFileDir != null && projectFileDir.isValid() ? projectFileDir : null;
  }
}
