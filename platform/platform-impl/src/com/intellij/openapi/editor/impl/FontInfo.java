// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.util.SystemInfo;
import gnu.trove.TIntHashSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import sun.font.CompositeGlyphMapper;
import sun.font.FontDesignMetrics;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.util.Collections;

/**
 * @author max
 */
public class FontInfo {
  private static final FontRenderContext DEFAULT_CONTEXT = new FontRenderContext(null, false, false);
  private static final Font DUMMY_FONT = new Font(null);

  private final Font myFont;
  private final int mySize;
  @JdkConstants.FontStyle private final int myStyle;
  private final TIntHashSet mySafeCharacters = new TIntHashSet();
  private final FontRenderContext myContext;
  private FontMetrics myFontMetrics = null;

  /**
   * @deprecated Use {@link #FontInfo(String, int, int, boolean, FontRenderContext)} instead.
   */
  @Deprecated
  public FontInfo(final String familyName, final int size, @JdkConstants.FontStyle int style) {
    this(familyName, size, style, false, null);
  }

  /**
   * @deprecated Use {@link #FontInfo(String, int, int, boolean, FontRenderContext)} instead.
   */
  @Deprecated
  public FontInfo(final String familyName, final int size, @JdkConstants.FontStyle int style, boolean useLigatures) {
    this(familyName, size, style, useLigatures, null);
  }

  /**
   * To get valid font metrics from this {@link FontInfo} instance, pass valid {@link FontRenderContext} here as a parameter.
   */
  public FontInfo(final String familyName, final int size, @JdkConstants.FontStyle int style, boolean useLigatures,
                  FontRenderContext fontRenderContext) {
    mySize = size;
    myStyle = style;
    Font font = new Font(familyName, style, size);
    myFont = useLigatures ? font.deriveFont(Collections.singletonMap(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON)) : font;
    myContext = fontRenderContext;
  }

  public boolean canDisplay(int codePoint) {
    try {
      if (codePoint < 128) return true;
      if (mySafeCharacters.contains(codePoint)) return true;
      if (canDisplay(myFont, codePoint, false)) {
        mySafeCharacters.add(codePoint);
        return true;
      }
      return false;
    }
    catch (Exception e) {
      // JRE has problems working with the font. Just skip.
      return false;
    }
  }

  public static boolean canDisplay(@NotNull Font font, int codePoint, boolean disableFontFallback) {
    if (!Character.isValidCodePoint(codePoint)) return false;
    if (disableFontFallback && SystemInfo.isMac) {
      int glyphCode = font.createGlyphVector(DEFAULT_CONTEXT, new String(new int[]{codePoint}, 0, 1)).getGlyphCode(0);
      return (glyphCode & CompositeGlyphMapper.GLYPHMASK) != 0 && (glyphCode & CompositeGlyphMapper.SLOTMASK) == 0;
    }
    else {
      return font.canDisplay(codePoint);
    }
  }

  public Font getFont() {
    return myFont;
  }

  public int charWidth(int codePoint) {
    final FontMetrics metrics = fontMetrics();
    return FontLayoutService.getInstance().charWidth(metrics, codePoint);
  }

  public float charWidth2D(int codePoint) {
    FontMetrics metrics = fontMetrics();
    return FontLayoutService.getInstance().charWidth2D(metrics, codePoint);
  }

  public synchronized FontMetrics fontMetrics() {
    if (myFontMetrics == null) {
      myFontMetrics = getFontMetrics(myFont, myContext == null ? getFontRenderContext(null) : myContext);
    }
    return myFontMetrics;
  }

  @NotNull
  public static FontMetrics getFontMetrics(@NotNull Font font, @NotNull FontRenderContext fontRenderContext) {
    return FontDesignMetrics.getMetrics(font, fontRenderContext);
  }

  public static FontRenderContext getFontRenderContext(Component component) {
    if (component == null) {
        return DEFAULT_CONTEXT;
    }
    return component.getFontMetrics(DUMMY_FONT).getFontRenderContext();
  }

  public int getSize() {
    return mySize;
  }

  @JdkConstants.FontStyle
  public int getStyle() {
    return myStyle;
  }

  public FontRenderContext getFontRenderContext() {
    return myContext;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FontInfo fontInfo = (FontInfo)o;

    if (!myFont.equals(fontInfo.myFont)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFont.hashCode();
  }
}
