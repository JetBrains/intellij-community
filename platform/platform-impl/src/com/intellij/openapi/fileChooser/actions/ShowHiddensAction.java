// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladimir Kondratyev
 */
public final class ShowHiddensAction extends ToggleAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    FileSystemTree tree = e.getData(FileSystemTree.DATA_KEY);
    e.getPresentation().setEnabled(tree != null);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    final FileSystemTree fileSystemTree = e.getData(FileSystemTree.DATA_KEY);
    return fileSystemTree != null && fileSystemTree.areHiddensShown();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final FileSystemTree fileSystemTree = e.getData(FileSystemTree.DATA_KEY);
    if (fileSystemTree != null) {
      fileSystemTree.showHiddens(state);
    }
  }
}