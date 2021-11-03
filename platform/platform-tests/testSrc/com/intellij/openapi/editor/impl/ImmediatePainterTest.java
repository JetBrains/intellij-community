// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.ui.JBColor;

import java.awt.*;

/**
 * @author Pavel Fatin
 */
public class ImmediatePainterTest extends ImmediatePainterTestCase {
  public void testEmptyFile() throws Exception {
    init("");
    assertRenderedCorrectly(0, 'c');
  }

  public void testBeginningOfFile() throws Exception {
    init("\nfoo");
    assertRenderedCorrectly(0, 'c');
  }

  public void testDrawingNarrowChar() throws Exception {
    init("");
    assertRenderedCorrectly(0, '▌');
  }

  public void testDrawingWideChar() throws Exception {
    init("");
    assertRenderedCorrectly(0, '█');
  }

  public void testRestoringNarrowChar() throws Exception {
    init("▌");
    assertRenderedCorrectly(1, ' ');
  }

  public void testRestoringWideChar() throws Exception {
    init("█");
    assertRenderedCorrectly(1, ' ');
  }

  public void testCaretRow() throws Exception {
    init("a");
    setCaretRowVisible(true);
    assertRenderedCorrectly(1, 'b');
  }

  public void testHighlighterBackground() throws Exception {
    init("a");
    addLineHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
    assertRenderedCorrectly(1, 'b');
  }

  public void testHighlighterForeground() throws Exception {
    init("a");
    addLineHighlighter(0, 1, HighlighterLayer.ERROR, foreground(JBColor.GREEN));
    assertRenderedCorrectly(1, 'b');
  }

  public void testHighlighterBelowCaretRow() throws Exception {
    init("a");
    setCaretRowVisible(true);
    addLineHighlighter(0, 1, HighlighterLayer.SYNTAX, background(JBColor.GREEN));
    assertRenderedCorrectly(1, 'b');
  }

  public void testHighlighterAboveCaretRow() throws Exception {
    init("a");
    setCaretRowVisible(true);
    addLineHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
    assertRenderedCorrectly(1, 'b');
  }

  public void testRangeHighlighter() throws Exception {
    init("a");
    addRangeHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
    assertRenderedCorrectly(1, 'b');
  }

  public void testThinLineCursor() throws Exception {
    init("");
    setLineCursorWidth(1);
    assertRenderedCorrectly(0, 'a');
  }

  public void testBlockCursor() throws Exception {
    init("");
    setBlockCursor(true);
    assertRenderedCorrectly(0, 'a');
  }

  public void testCaretColor() throws Exception {
    setCaretColor(JBColor.GREEN);
    init("");
    assertRenderedCorrectly(0, 'a');
  }

  public void testNonMonospacedFont() throws Exception {
    setFont(Font.SERIF, 14);
    init("i");
    assertRenderedCorrectly(1, ' ');
  }

  // TODO Can IDEA really disable syntax highlighting?
  //public void testDisabledSyntaxHighlighting() throws Exception {
  //  init("1", TestFileType.JS);
  //  HighlightingSettingsPerFile settings = (HighlightingSettingsPerFile)HighlightingLevelManager.getInstance(getProject());
  //  settings.setHighlightingSettingForRoot(getFile(), FileHighlightingSetting.NONE);
  //  assertTypedCorrectly(1, '+');
  //}

}