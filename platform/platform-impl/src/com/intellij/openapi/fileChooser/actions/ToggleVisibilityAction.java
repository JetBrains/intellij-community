// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import org.jetbrains.annotations.NotNull;

public final class ToggleVisibilityAction extends FileChooserAction implements Toggleable {
  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    Toggleable.setSelected(e.getPresentation(), panel.showHiddenFiles());
  }

  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    boolean state = !panel.showHiddenFiles();
    panel.showHiddenFiles(state);
    Toggleable.setSelected(e.getPresentation(), state);
  }

  @Override
  protected void update(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
    Toggleable.setSelected(e.getPresentation(), fileChooser.areHiddensShown());
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
    boolean state = !fileChooser.areHiddensShown();
    fileChooser.showHiddens(state);
    Toggleable.setSelected(e.getPresentation(), state);
  }
}
