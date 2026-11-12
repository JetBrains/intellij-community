// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.ui.Graphics2DDelegate;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_CHAR_WIDTH;
import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_DESCENT;
import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_LINE_HEIGHT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class SimpleTextFragmentTest {
  private final BufferedImage image = new BufferedImage(200, 20, BufferedImage.TYPE_INT_RGB);
  private Graphics2D graphics;

  @Before
  public void setUp() {
    FontLayoutService.setInstance(new MockFontLayoutService(TEST_CHAR_WIDTH, TEST_LINE_HEIGHT, TEST_DESCENT));
    graphics = image.createGraphics();
  }

  @After
  public void tearDown() {
    graphics.dispose();
    FontLayoutService.setInstance(null);
  }

  @Test
  public void testLetterSpacingFollowsEachChar() {
    SimpleTextFragment fragment = new SimpleTextFragment("abc".toCharArray(), 0, 3, createFontInfo(2), null);
    assertEquals(12, fragment.visualColumnToX(0, 1), 0);
    assertEquals(36, fragment.visualColumnToX(0, 3), 0);
  }

  @Test
  public void testLetterSpacingDrawsEachGlyphAtItsPosition() {
    SimpleTextFragment fragment = new SimpleTextFragment("abcd".toCharArray(), 0, 4, createFontInfo(2), null);
    List<Double> positions = new ArrayList<>();
    fragment.draw(5, 15, 1, 4).accept(new Graphics2DDelegate(graphics) {
      @Override
      public void drawGlyphVector(GlyphVector glyphVector, float x, float y) {
        assertEquals(5, x, 0);
        for (int i = 0; i < glyphVector.getNumGlyphs(); i++) {
          positions.add(glyphVector.getGlyphPosition(i).getX());
        }
      }
    });
    assertEquals(List.of(1.0, 13.0, 25.0), positions); // each glyph in the middle of its cell
  }

  @Test
  public void testRepaintOfTheSameRangeReusesTheGlyphs() {
    SimpleTextFragment fragment = new SimpleTextFragment("abcd".toCharArray(), 0, 4, createFontInfo(2), null);
    List<GlyphVector> drawn = new ArrayList<>();
    Graphics2DDelegate capturing = new Graphics2DDelegate(graphics) {
      @Override
      public void drawGlyphVector(GlyphVector glyphVector, float x, float y) {
        drawn.add(glyphVector);
      }
    };
    fragment.draw(0, 15, 0, 4).accept(capturing);
    fragment.draw(0, 15, 0, 4).accept(capturing);
    fragment.draw(0, 15, 1, 4).accept(capturing);
    assertSame(drawn.get(0), drawn.get(1));
    assertNotSame(drawn.get(1), drawn.get(2));
    assertEquals(3, drawn.get(2).getNumGlyphs());
  }

  @Test
  public void testNoLetterSpacingDrawsTheChars() {
    SimpleTextFragment fragment = new SimpleTextFragment("abc".toCharArray(), 0, 3, createFontInfo(0), null);
    List<String> drawn = new ArrayList<>();
    fragment.draw(0, 15, 0, 3).accept(new Graphics2DDelegate(graphics) {
      @Override
      public void drawChars(char[] data, int offset, int length, int x, int y) {
        drawn.add(new String(data, offset, length));
      }

      @Override
      public void drawGlyphVector(GlyphVector glyphVector, float x, float y) {
        drawn.add("glyph vector");
      }
    });
    assertEquals(List.of("abc"), drawn);
    assertEquals(TEST_CHAR_WIDTH * 3, fragment.visualColumnToX(0, 3), 0);
  }

  /**
   * Creates a font of size 1, so the letter spacing is also the spacing in pixels.
   */
  private static @NotNull FontInfo createFontInfo(float letterSpacing) {
    Font font = new Font(Font.MONOSPACED, Font.PLAIN, 1);
    FontRenderContext context = new FontRenderContext(null, false, false);
    return new FontInfo(font, 1, false, letterSpacing, Set.of(), context);
  }
}
