// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class CreateRectangularSelectionOnMouseDragAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    // actual logic is implemented in EditorImpl
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(false);
  }
}
