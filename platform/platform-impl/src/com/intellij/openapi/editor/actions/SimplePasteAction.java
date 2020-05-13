// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.NotNull;

public class SimplePasteAction extends EditorAction {
  public SimplePasteAction() {
    super(new BasePasteHandler());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      Presentation presentation = e.getPresentation();
      presentation.setVisible(presentation.isEnabled());
    }
  }
}
