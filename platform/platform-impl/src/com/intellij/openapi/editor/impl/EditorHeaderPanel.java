// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import javax.swing.*;
import java.awt.*;


final class EditorHeaderPanel extends JPanel {

  private final EditorImpl editor;
  private int oldHeight;

  EditorHeaderPanel(EditorImpl editor) {
    super(new BorderLayout());
    this.editor = editor;
  }

  @Override
  public void revalidate() {
    oldHeight = getHeight();
    super.revalidate();
  }

  @Override
  protected void validateTree() {
    int height = oldHeight;
    super.validateTree();
    height -= getHeight();

    if (height != 0 &&
        !(oldHeight == 0 && getComponentCount() > 0 && editor.getPermanentHeaderComponent() == getComponent(0))) {
      editor.getVerticalScrollBar().setValue(editor.getVerticalScrollBar().getValue() - height);
    }
    oldHeight = getHeight();
  }
}
