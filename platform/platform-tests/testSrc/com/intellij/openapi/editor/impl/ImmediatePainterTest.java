// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.testFramework.junit5.RegistryKey;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.ui.JBColor;
import org.junit.jupiter.api.Test;

import java.awt.Font;

/**
 * @author Pavel Fatin
 */
@TestApplication
public class ImmediatePainterTest extends ImmediatePainterTestCase {
  @Test
  public void testEmptyFile() {
    myFixture.runTest(() -> {
      init("");
      assertRenderedCorrectly(0, 'c');
    });
  }

  @Test
  public void testBeginningOfFile() {
    myFixture.runTest(() -> {
      init("\nfoo");
      assertRenderedCorrectly(0, 'c');
    });
  }

  @Test
  public void testDrawingNarrowChar() {
    myFixture.runTest(() -> {
      init("");
      assertRenderedCorrectly(0, '▌');
    });
  }

  @Test
  public void testDrawingWideChar() {
    myFixture.runTest(() -> {
      init("");
      assertRenderedCorrectly(0, '█');
    });
  }

  @Test
  public void testRestoringNarrowChar() {
    myFixture.runTest(() -> {
      init("▌");
      assertRenderedCorrectly(1, ' ');
    });
  }

  @Test
  public void testRestoringWideChar() {
    myFixture.runTest(() -> {
      init("█");
      assertRenderedCorrectly(1, ' ');
    });
  }

  @Test
  public void testCaretRow() {
    myFixture.runTest(() -> {
      init("a");
      setCaretRowVisible(true);
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  public void testHighlighterBackground() {
    myFixture.runTest(() -> {
      init("a");
      addLineHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  @RegistryKey(key = "editor.text.fractional.metrics", value = "Enabled")
  public void testHighlighterForeground() {
    myFixture.runTest(() -> {
      init("a");
      addLineHighlighter(0, 1, HighlighterLayer.ERROR, foreground(JBColor.GREEN));
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  @RegistryKey(key = "editor.text.fractional.metrics", value = "Enabled")
  void testHighlighterForegroundWithoutDoubleBuffering() {
    myFixture.runTest(() -> {
      setDoubleBufferingEnabled(false);
      try {
        init("a");
        addLineHighlighter(0, 1, HighlighterLayer.ERROR, foreground(JBColor.GREEN));
        assertRenderedCorrectly(1, 'b');
      }
      finally {
        setDoubleBufferingEnabled(true);
      }
    });
  }

  @Test
  public void testHighlighterBelowCaretRow() {
    myFixture.runTest(() -> {
      init("a");
      setCaretRowVisible(true);
      addLineHighlighter(0, 1, HighlighterLayer.SYNTAX, background(JBColor.GREEN));
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  public void testHighlighterAboveCaretRow() {
    myFixture.runTest(() -> {
      init("a");
      setCaretRowVisible(true);
      addLineHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  @RegistryKey(key = "editor.text.fractional.metrics", value = "Enabled")
  public void testRangeHighlighter() {
    myFixture.runTest(() -> {
      init("a");
      addRangeHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  @RegistryKey(key = "editor.text.fractional.metrics", value = "Enabled")
  void testRangeHighlighterOnTypedChar() {
    myFixture.runTest(() -> {
      init("a");
      addRangeHighlighter(1, 1, HighlighterLayer.ERROR, background(JBColor.GREEN)).setGreedyToRight(true);
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  @RegistryKey(key = "editor.text.fractional.metrics", value = "Enabled")
  void testRangeHighlighterOverLineEnd() {
    myFixture.runTest(() -> {
      init("a\n");
      addRangeHighlighter(0, 2, HighlighterLayer.ERROR, background(JBColor.GREEN));
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  @RegistryKey(key = "editor.text.fractional.metrics", value = "Enabled")
  void testAdjacentRangeHighlighters() {
    myFixture.runTest(() -> {
      init("a");
      addRangeHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
      addRangeHighlighter(1, 1, HighlighterLayer.ERROR, background(JBColor.RED)).setGreedyToRight(true);
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  public void testThinLineCursor() {
    myFixture.runTest(() -> {
      init("");
      setLineCursorWidth(1);
      assertRenderedCorrectly(0, 'a');
    });
  }

  @Test
  public void testBlockCursor() {
    myFixture.runTest(() -> {
      init("");
      setBlockCursor(true);
      assertRenderedCorrectly(0, 'a');
    });
  }

  @Test
  public void testFullLineHeightCursor() {
    myFixture.runTest(() -> {
      init("");
      setFullLineHeightCursor(true);
      assertRenderedCorrectly(0, 'a');
    });
  }

  @Test
  public void testCaretColor() {
    myFixture.runTest(() -> {
      setCaretColor(JBColor.GREEN);
      init("");
      assertRenderedCorrectly(0, 'a');
    });
  }

  @Test
  public void testNonMonospacedFont() {
    myFixture.runTest(() -> {
      setFont(Font.SERIF, 14);
      init("i");
      assertRenderedCorrectly(1, ' ');
    });
  }

  // TODO Can IDEA really disable syntax highlighting?
  //public void testDisabledSyntaxHighlighting() throws Exception {
  //  init("1", TestFileType.JS);
  //  HighlightingSettingsPerFile settings = (HighlightingSettingsPerFile)HighlightingLevelManager.getInstance(myFixture.getProject());
  //  settings.setHighlightingSettingForRoot(myFixture.getFile(), FileHighlightingSetting.NONE);
  //  assertTypedCorrectly(1, '+');
  //}

}
