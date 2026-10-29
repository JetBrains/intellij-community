// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.FontMetricsAccessor;
import com.jetbrains.JBR;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.font.FontRenderContext;
import java.util.Set;

/**
 * Thread-safe
 */
public final class FontInfo {
  static final FontRenderContext DEFAULT_CONTEXT = new FontRenderContext(null, false, false);

  // Removed in commit: https://github.com/JetBrains/JetBrainsRuntime/commit/e0219c19f4de062612965fb493e56c4077541204
  private static final int SLOTMASK =  0xff000000;
  private static final int GLYPHMASK = 0x00ffffff;

  private static final Font DUMMY_FONT = new Font(null);
  private static final @Nullable FontMetricsAccessor FONT_METRICS_ACCESSOR = JBR.getFontMetricsAccessor();

  public static @NotNull FontMetrics getFontMetrics(@NotNull Font font, @NotNull FontRenderContext fontRenderContext) {
    if (FONT_METRICS_ACCESSOR == null) {
      throw new UnsupportedOperationException("current runtime does not support FontMetricsAccessor");
    }
    return FONT_METRICS_ACCESSOR.getMetrics(font, fontRenderContext);
  }

  public static @NotNull FontRenderContext getFontRenderContext(@Nullable Component component) {
    if (component == null) {
      return DEFAULT_CONTEXT;
    }
    return component.getFontMetrics(DUMMY_FONT).getFontRenderContext();
  }

  public static boolean canDisplay(@NotNull Font font, int codePoint, boolean disableFontFallback) {
    if (!Character.isValidCodePoint(codePoint)) {
      return false;
    }
    if (disableFontFallback && SystemInfo.isMac) {
      String codePointStr = Character.toString(codePoint);
      int glyphCode = font.createGlyphVector(DEFAULT_CONTEXT, codePointStr).getGlyphCode(0);
      return (glyphCode & GLYPHMASK) != 0 && (glyphCode & SLOTMASK) == 0;
    } else {
      return font.canDisplay(codePoint);
    }
  }

  private final @NotNull Font myFont;
  private final float mySize;
  private final @Nullable FontRenderContext myContext;
  private final @NotNull IntSet mySafeCharacters = new IntOpenHashSet();
  private @Nullable FontMetrics myFontMetrics = null;

  /**
   * To get valid font metrics from this {@link FontInfo} instance, pass valid {@link FontRenderContext} here as a parameter.
   */
  public FontInfo(
    @NotNull Font font,
    float size,
    boolean useLigatures,
    @Unmodifiable @NotNull Set<@NotNull String> variants,
    @Nullable FontRenderContext fontRenderContext
  ) {
    mySize = size;
    myFont = EditorFontFeatures.withFeatures(font.deriveFont(size), useLigatures, variants);
    myContext = fontRenderContext;
  }

  public boolean canDisplay(int codePoint) {
    try {
      if (codePoint < 128) {
        return true;
      }
      synchronized (this) {
        if (mySafeCharacters.contains(codePoint)) {
          return true;
        }
      }
      if (canDisplay(myFont, codePoint, false)) {
        synchronized (this) {
          mySafeCharacters.add(codePoint);
        }
        return true;
      }
      return false;
    } catch (Exception e) {
      // JRE has problems working with the font. Just skip.
      return false;
    }
  }

  public @NotNull Font getFont() {
    return myFont;
  }

  public int charWidth(int codePoint) {
    FontMetrics metrics = fontMetrics();
    return FontLayoutService.getInstance().charWidth(metrics, codePoint);
  }

  public float charWidth2D(int codePoint) {
    FontMetrics metrics = fontMetrics();
    return FontLayoutService.getInstance().charWidth2D(metrics, codePoint);
  }

  public synchronized @NotNull FontMetrics fontMetrics() {
    if (myFontMetrics == null) {
      FontRenderContext context = myContext != null ? myContext : getFontRenderContext(null);
      myFontMetrics = getFontMetrics(myFont, context);
    }
    return myFontMetrics;
  }

  public int getSize() {
    return (int)(mySize + 0.5);
  }

  public float getSize2D() {
    return mySize;
  }

  public /* @Nullable */ FontRenderContext getFontRenderContext() {
    return myContext;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof FontInfo other && myFont.equals(other.myFont);
  }

  @Override
  public int hashCode() {
    return myFont.hashCode();
  }

  @Override
  public synchronized String toString() {
    return "FontInfo{" +
           "myFont=" + myFont +
           ", mySize=" + mySize +
           ", mySafeCharacters=" + mySafeCharacters +
           ", myContext=" + myContext +
           ", myFontMetrics=" + myFontMetrics +
           '}';
  }
}
