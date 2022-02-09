// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import org.jetbrains.annotations.NotNull;

public class TogglePathShowingAction extends FileChooserAction implements Toggleable {
  public TogglePathShowingAction() {
    setEnabledInModalContext(true);
  }

  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    Toggleable.setSelected(e.getPresentation(), panel.showPathBar());
  }

  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    boolean state = !panel.showPathBar();
    panel.showPathBar(state);
    Toggleable.setSelected(e.getPresentation(), state);
  }

  @Override
  protected void update(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
    // the old chooser uses `com.intellij.openapi.fileChooser.ex.TextFieldAction` instead
    e.getPresentation().setEnabledAndVisible(false);
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) { }
}
