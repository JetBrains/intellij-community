// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import org.jetbrains.annotations.NotNull;

public abstract class EditorPopupHandler implements EditorMouseListener {
  public abstract void invokePopup(EditorMouseEvent event);

  private void handle(EditorMouseEvent e) {
    if (e.getMouseEvent().isPopupTrigger() && e.getArea() == EditorMouseEventArea.EDITING_AREA) {
      invokePopup(e);
      e.consume();
    }
  }

  @Override
  public void mouseClicked(@NotNull EditorMouseEvent e) {
    handle(e);
  }

  @Override
  public void mousePressed(@NotNull EditorMouseEvent e) {
    handle(e);
  }

  @Override
  public void mouseReleased(@NotNull EditorMouseEvent e) {
    handle(e);
  }
}