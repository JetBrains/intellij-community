// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import org.jetbrains.annotations.NotNull;

abstract sealed class GoToHistoryAction extends FileChooserAction {
  @SuppressWarnings("FieldMayBeStatic") private final boolean myBackward = this instanceof Backward;

  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(panel.hasHistory(myBackward));
  }

  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    panel.loadHistory(myBackward);
  }

  @Override
  protected void update(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) { }

  static final class Backward extends GoToHistoryAction { }

  static final class Forward extends GoToHistoryAction { }
}
