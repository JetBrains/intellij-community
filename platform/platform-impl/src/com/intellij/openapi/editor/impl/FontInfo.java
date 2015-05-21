/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntHashSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;

/**
 * @author max
 */
public class FontInfo {
  private static final boolean USE_ALTERNATIVE_CAN_DISPLAY_PROCEDURE = SystemInfo.isAppleJvm && Registry.is("ide.mac.fix.font.fallback");
  private static final FontRenderContext DUMMY_CONTEXT = new FontRenderContext(null, false, false);

  private final TIntHashSet mySymbolsToBreakDrawingIteration = new TIntHashSet();

  private final Font myFont;
  private final int mySize;
  @JdkConstants.FontStyle private final int myStyle;
  private final TIntHashSet mySafeCharacters = new TIntHashSet();
  private FontMetrics myFontMetrics = null;
  private final int[] charWidth = new int[128];
  private boolean myHasGlyphsToBreakDrawingIteration;
  private boolean myCheckedForProblemGlyphs;

  public FontInfo(final String familyName, final int size, @JdkConstants.FontStyle int style) {
    mySize = size;
    myStyle = style;
    myFont = new Font(familyName, style, size);
  }
  
  private void parseProblemGlyphs() {
    myCheckedForProblemGlyphs = true;
    BufferedImage buffer = UIUtil.createImage(20, 20, BufferedImage.TYPE_INT_RGB);
    final Graphics graphics = buffer.getGraphics();
    if (!(graphics instanceof Graphics2D)) {
      return;
    }
    final FontRenderContext context = ((Graphics2D)graphics).getFontRenderContext();
    char[] charBuffer = new char[1];
    for (char c = 0; c < 128; c++) {
      if (!myFont.canDisplay(c)) {
        continue;
      }
      charBuffer[0] = c;
      final GlyphVector vector = myFont.createGlyphVector(context, charBuffer);
      final float y = vector.getGlyphMetrics(0).getAdvanceY();
      if (Math.round(y) != 0) {
        mySymbolsToBreakDrawingIteration.add(c);
      }
    }
    myHasGlyphsToBreakDrawingIteration = !mySymbolsToBreakDrawingIteration.isEmpty();
  }

  /**
   * We've experienced a problem that particular symbols from particular font are represented really weird
   * by the IJ editor (IDEA-83645).
   * <p/>
   * Eventually it was found out that outline font glyphs can have a 'y advance', i.e. instruction on how the subsequent
   * glyphs location should be adjusted after painting the current glyph. In terms of java that means that such a problem
   * glyph should be the last symbol at the {@link Graphics#drawChars(char[], int, int, int, int) text drawing iteration}.
   * <p/>
   * Hopefully, such glyphs are exceptions from the normal processing, so, this method allows to answer whether a font
   * {@link #getFont() referenced} by the current object has such a glyph.
   * 
   * @return    true if the {@link #getFont() target font} has problem glyphs; <code>false</code> otherwise
   */
  public boolean hasGlyphsToBreakDrawingIteration() {
    if (!myCheckedForProblemGlyphs) {
      parseProblemGlyphs();
    }
    return myHasGlyphsToBreakDrawingIteration;
  }

  /**
   * @return    unicode symbols which glyphs {@link #hasGlyphsToBreakDrawingIteration() have problems}
   * at the {@link #getFont() target font}.
   */
  @NotNull
  public TIntHashSet getSymbolsToBreakDrawingIteration() {
    if (!myCheckedForProblemGlyphs) {
      parseProblemGlyphs();
    }
    return mySymbolsToBreakDrawingIteration;
  }

  public boolean canDisplay(char c) {
    try {
      if (c < 128) return true;
      if (mySafeCharacters.contains(c)) return true;
      if (canDisplayImpl(c)) {
        mySafeCharacters.add(c);
        return true;
      }
      return false;
    }
    catch (Exception e) {
      // JRE has problems working with the font. Just skip.
      return false;
    }
  }

  private boolean canDisplayImpl(char c) {
    if (USE_ALTERNATIVE_CAN_DISPLAY_PROCEDURE) {
      return myFont.createGlyphVector(DUMMY_CONTEXT, new char[]{c}).getGlyphCode(0) > 0;
    }
    else {
      return myFont.canDisplay(c);
    }
  }

  public Font getFont() {
    return myFont;
  }

  public int charWidth(char c) {
    final FontMetrics metrics = fontMetrics();
    if (c < 128) return charWidth[c];
    return metrics.charWidth(c);
  }

  private FontMetrics fontMetrics() {
    if (myFontMetrics == null) {
      // We need to use antialising-aware font metrics because we've alrady encountered a situation when non-antialiased symbol
      // width is not equal to the antialiased one (IDEA-81539).
      final Graphics graphics = UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_RGB).getGraphics();
      EditorUIUtil.setupAntialiasing(graphics);
      graphics.setFont(myFont);
      myFontMetrics = graphics.getFontMetrics();
      for (int i = 0; i < 128; i++) {
        charWidth[i] = myFontMetrics.charWidth(i);
      }
    }
    return myFontMetrics;
  }

  void reset() {
    myFontMetrics = null;
  }
  
  public int getSize() {
    return mySize;
  }

  @JdkConstants.FontStyle
  public int getStyle() {
    return myStyle;
  }
}
