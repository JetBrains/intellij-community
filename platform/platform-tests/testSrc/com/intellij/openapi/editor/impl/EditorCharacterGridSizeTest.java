// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.EditorEx;

public class EditorCharacterGridSizeTest extends AbstractEditorTest {
  private static final String LONG_LINE = "x".repeat(200);
  private static final int CONTENT_WIDTH = LONG_LINE.length() * TEST_CHAR_WIDTH;

  private void enableGridMode() {
    getEditor().getSettings().setCharacterGridWidthMultiplier(1.0f);
    ((EditorEx)getEditor()).reinitSettings();
    assertNotNull(((EditorImpl)getEditor()).getCharacterGrid());
  }

  private int preferredWidth() {
    return getEditor().getContentComponent().getPreferredSize().width;
  }

  // IJPL-247496: non-wrapping grid editors (Markdown) must expose the full line width, not the viewport.
  public void testNonWrappingGridEditorIsScrollableToTheEndOfLongLines() {
    initText(LONG_LINE);
    getEditor().getSettings().setAdditionalColumnsCount(0);
    setEditorVisibleSize(20, 10);
    enableGridMode();

    assertTrue(getEditor().getScrollingModel().getVisibleArea().width < CONTENT_WIDTH);
    assertTrue("preferred=" + preferredWidth(), preferredWidth() >= CONTENT_WIDTH);
  }

  // IJPL-180831: soft-wrapping grid editors (terminal) still clamp preferred width to the viewport.
  public void testWrappingGridEditorStaysClampedToViewport() {
    initText(LONG_LINE);
    getEditor().getSettings().setAdditionalColumnsCount(0);
    configureSoftWraps(20);
    enableGridMode();

    assertTrue("preferred=" + preferredWidth(), preferredWidth() < CONTENT_WIDTH);
  }
}
