// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.fileTypes.PlainTextSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.junit5.RegistryKey;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;

/**
 * @author Pavel Fatin
 */
@TestApplication
public class ImmediatePainterTest extends ImmediatePainterTestCase {
  private static final TextAttributesKey SYNTAX_BACKGROUND =
    TextAttributesKey.createTextAttributesKey("IMMEDIATE_PAINTER_TEST_SYNTAX_BACKGROUND");

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
  public void testLetterSpacingTurnsImmediatePaintingOff() {
    myFixture.runTest(() -> {
      init("a");
      setLetterSpacing(0.2f);
      assertNotRenderedImmediately(1, 'b');
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
  public void testRoundedHighlighterEndingAtCaretTurnsImmediatePaintingOff() {
    myFixture.runTest(() -> {
      enableRoundedHighlighting();
      init("a");
      addRangeHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
      assertNotRenderedImmediately(1, 'b');
    });
  }

  @Test
  public void testRoundedHighlighterOnTypedCharTurnsImmediatePaintingOff() {
    myFixture.runTest(() -> {
      enableRoundedHighlighting();
      init("a");
      addRangeHighlighter(1, 1, HighlighterLayer.ERROR, background(JBColor.GREEN)).setGreedyToRight(true);
      assertNotRenderedImmediately(1, 'b');
    });
  }

  @Test
  public void testRoundedHighlightingKeepsImmediatePaintingOnCaretRow() {
    myFixture.runTest(() -> {
      enableRoundedHighlighting();
      init("a");
      setCaretRowVisible(true);
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  public void testRoundedHighlightingKeepsImmediatePaintingOnLineHighlighter() {
    myFixture.runTest(() -> {
      enableRoundedHighlighting();
      init("a");
      addLineHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
      assertRenderedCorrectly(1, 'b');
    });
  }

  @Test
  public void testHuggingHighlighterOverLineBreakTurnsImmediatePaintingOff() {
    myFixture.runTest(() -> {
      enableHuggingHighlighting();
      init("a\nb");
      addRangeHighlighter(0, 3, HighlighterLayer.ERROR, background(JBColor.GREEN));
      assertNotRenderedImmediately(1, 'c');
    });
  }

  @Test
  public void testHuggingSyntaxBackgroundTurnsImmediatePaintingOff() {
    myFixture.runTest(() -> {
      enableHuggingHighlighting();
      init("a\nb");
      setSyntaxBackground(JBColor.GREEN);
      assertNotRenderedImmediately(1, 'c');
    });
  }

  @Test
  public void testHuggingKeepsImmediatePaintingOnHighlighterBeforeLineBreak() {
    myFixture.runTest(() -> {
      enableHuggingHighlighting();
      init("a\nb");
      addRangeHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
      assertRenderedCorrectly(1, 'c');
    });
  }

  @Test
  public void testHuggingKeepsImmediatePaintingOnLineHighlighter() {
    myFixture.runTest(() -> {
      enableHuggingHighlighting();
      init("a\nb");
      addLineHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
      assertRenderedCorrectly(1, 'c');
    });
  }

  /**
   * Sets a plain text highlighter that gives every token the background {@code color}.
   */
  private void setSyntaxBackground(Color color) {
    EditorEx editor = (EditorEx)myFixture.getEditor();
    EditorColorsScheme scheme = editor.getColorsScheme();
    scheme.setAttributes(SYNTAX_BACKGROUND, background(color));
    SyntaxHighlighterBase highlighter = new SyntaxHighlighterBase() {
      @Override
      public @NotNull Lexer getHighlightingLexer() {
        return PlainTextSyntaxHighlighterFactory.createPlainTextLexer();
      }

      @Override
      public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return new TextAttributesKey[]{SYNTAX_BACKGROUND};
      }
    };
    editor.setHighlighter(new LexerEditorHighlighter(highlighter, scheme));
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
