// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.NotNull;

public final class SimplePasteAction extends EditorAction {
  public SimplePasteAction() {
    super(new BasePasteHandler());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (e.isFromContextMenu()) {
      Presentation presentation = e.getPresentation();
      presentation.setVisible(presentation.isEnabled());
    }
  }
}
