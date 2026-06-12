// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.components.ScrollBarPainter;

import javax.swing.JScrollBar;
import java.awt.Color;

public class EditorScrollBarBackgroundTest extends AbstractEditorTest {
  public void testScrollBarBackgroundFollowsEditorBackground() {
    initText("some text");
    EditorEx editor = (EditorEx)getEditor();
    JScrollBar verticalScrollBar = editor.getScrollPane().getVerticalScrollBar();

    Color editorBackground = new Color(0x123456);
    editor.setBackgroundColor(editorBackground);
    // The editor scroll bar background follows the editor background instead of the LAF default ScrollBar.background.
    assertEquals(editorBackground, EditorColorsUtil.getColor(verticalScrollBar, ScrollBarPainter.BACKGROUND));

    // It keeps tracking subsequent editor background changes.
    Color newBackground = new Color(0x654321);
    editor.setBackgroundColor(newBackground);
    assertEquals(newBackground, EditorColorsUtil.getColor(verticalScrollBar, ScrollBarPainter.BACKGROUND));
  }
}
