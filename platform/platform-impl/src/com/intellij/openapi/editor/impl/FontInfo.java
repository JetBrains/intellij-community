// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.Extensions;
import com.jetbrains.FontMetricsAccessor;
import com.jetbrains.JBR;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.util.EnumSet;
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

  /**
   * The scripts whose letters join or hang from one head line, so the letter spacing would break their words apart.
   * Their letters take no letter spacing.
   * The joining scripts are the scripts with joining letters in the Unicode file {@code ArabicShaping.txt}.
   */
  private static final Set<Character.UnicodeScript> SCRIPTS_WITHOUT_LETTER_SPACING = EnumSet.of(
    // joining scripts
    Character.UnicodeScript.ADLAM,
    Character.UnicodeScript.ARABIC,
    Character.UnicodeScript.CHORASMIAN,
    Character.UnicodeScript.HANIFI_ROHINGYA,
    Character.UnicodeScript.MANDAIC,
    Character.UnicodeScript.MANICHAEAN,
    Character.UnicodeScript.MONGOLIAN,
    Character.UnicodeScript.NKO,
    Character.UnicodeScript.OLD_UYGHUR,
    Character.UnicodeScript.PHAGS_PA,
    Character.UnicodeScript.PSALTER_PAHLAVI,
    Character.UnicodeScript.SOGDIAN,
    Character.UnicodeScript.SYRIAC,
    // scripts with a head line or a stem line
    Character.UnicodeScript.BENGALI,
    Character.UnicodeScript.DEVANAGARI,
    Character.UnicodeScript.GURMUKHI,
    Character.UnicodeScript.MODI,
    Character.UnicodeScript.OGHAM,
    Character.UnicodeScript.SHARADA,
    Character.UnicodeScript.SYLOTI_NAGRI,
    Character.UnicodeScript.TIRHUTA
  );

  /**
   * The Arabic tatweel joins the letters around it, but its script is Common, so it is not in {@link #SCRIPTS_WITHOUT_LETTER_SPACING}.
   */
  private static final int ARABIC_TATWEEL = 0x0640;

  /**
   * No script of {@link #SCRIPTS_WITHOUT_LETTER_SPACING} has a code point below this one, the first Arabic code point.
   */
  private static final int FIRST_CODE_POINT_WITHOUT_LETTER_SPACING = 0x0600;

  public static @NotNull FontMetrics getFontMetrics(@NotNull Font font, @NotNull FontRenderContext fontRenderContext) {
    if (FONT_METRICS_ACCESSOR == null) {
      throw new UnsupportedOperationException("current runtime does not support FontMetricsAccessor");
    }
    return FONT_METRICS_ACCESSOR.getMetrics(font, fontRenderContext);
  }

  /**
   * Returns the letter spacing in pixels at the font size, see {@link com.intellij.openapi.editor.colors.FontPreferences#getLetterSpacing()}.
   * Without fractional metrics, the value is a whole number of device pixels, so all gaps between the glyphs are equal.
   * A {@code null} context counts as a context without fractional metrics and without a transform.
   */
  @ApiStatus.Internal
  public static float letterSpacingPixels(float letterSpacing, float size, @Nullable FontRenderContext context) {
    float pixels = letterSpacing * size;
    if (pixels == 0) {
      // also turns -0 into 0
      return 0;
    }
    if (context != null && context.usesFractionalMetrics()) {
      return pixels;
    }
    double scale = context == null ? 1 : Math.abs(context.getTransform().getScaleX());
    if (scale == 0) {
      scale = 1;
    }
    return (float)(Math.round(pixels * scale) / scale);
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
    if (disableFontFallback) {
      var ext = JBR.getFontExtensions(Extensions.CAN_DISPLAY_WITHOUT_FALLBACK);
      if (ext != null) {
        return ext.canDisplayWithoutFallback(font, codePoint);
      }
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
  private final float myLetterSpacingPixels;
  private final @Nullable FontRenderContext myContext;
  private final @NotNull IntSet mySafeCharacters = new IntOpenHashSet();
  private final @NotNull Int2FloatMap myGlyphLeftSideBearings = new Int2FloatOpenHashMap(); // by glyph code, see glyphLeftSideBearing
  private @Nullable FontMetrics myFontMetrics = null;

  /**
   * To get valid font metrics from this {@link FontInfo} instance, pass valid {@link FontRenderContext} here as a parameter.
   * The widths of this instance include the letter spacing. The font has none, so the text layout adds it to the glyph positions.
   */
  public FontInfo(
    @NotNull Font font,
    float size,
    boolean useLigatures,
    float letterSpacing,
    @Unmodifiable @NotNull Set<@NotNull String> variants,
    @Nullable FontRenderContext fontRenderContext
  ) {
    mySize = size;
    myFont = EditorFontFeatures.withFeatures(font.deriveFont(size), useLigatures, variants);
    myLetterSpacingPixels = letterSpacingPixels(letterSpacing, size, fontRenderContext);
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

  /**
   * Returns the integer advance of the code point plus its letter spacing, rounded to an integer.
   * See {@link #letterSpacingAfter}.
   */
  public int charWidth(int codePoint) {
    FontMetrics metrics = fontMetrics();
    int width = FontLayoutService.getInstance().charWidth(metrics, codePoint);
    if (width <= 0) {
      return width;
    }
    return Math.round(width + letterSpacingAfter(codePoint, width));
  }

  /**
   * Returns the advance of the code point plus its letter spacing, see {@link #letterSpacingAfter}.
   */
  public float charWidth2D(int codePoint) {
    float advance = charAdvance2D(codePoint);
    if (advance <= 0) {
      return advance;
    }
    return advance + letterSpacingAfter(codePoint, advance);
  }

  /**
   * Returns the advance of the code point in this font, without the letter spacing.
   */
  @ApiStatus.Internal
  public float charAdvance2D(int codePoint) {
    FontMetrics metrics = fontMetrics();
    return FontLayoutService.getInstance().charWidth2D(metrics, codePoint);
  }

  /**
   * Returns the letter spacing after the code point with this advance, or 0 when the code point takes none.
   * A negative letter spacing is no larger than the advance, so the width of a code point is never below 0.
   * See {@link #takesLetterSpacing}.
   */
  @ApiStatus.Internal
  public float letterSpacingAfter(int codePoint, float advance) {
    if (advance <= 0 || myLetterSpacingPixels == 0 || !takesLetterSpacingByUnicode(codePoint)) {
      return 0;
    }
    return Math.max(myLetterSpacingPixels, -advance);
  }

  /**
   * Returns true when the text layout adds the letter spacing after the code point.
   * A code point without an advance takes no letter spacing.
   * A mark, such as a combining accent, and a format char, such as the zero width joiner, take none either.
   * They belong to the char before them, whatever advance the font gives them.
   * A letter of a script whose letters join, such as Arabic or Devanagari, takes none too.
   */
  @ApiStatus.Internal
  public boolean takesLetterSpacing(int codePoint) {
    return charAdvance2D(codePoint) > 0 && takesLetterSpacingByUnicode(codePoint);
  }

  /**
   * Returns the letter spacing in pixels at the size of this font.
   * The font itself has no letter spacing. The text layout adds it, see {@link #takesLetterSpacing}.
   */
  @ApiStatus.Internal
  public float getLetterSpacingPixels() {
    return myLetterSpacingPixels;
  }

  /**
   * Returns the left side bearing of the ink of a glyph, or {@link Float#NaN} when the glyph has no ink.
   * A negative value means that the ink starts before the position of the glyph, as for a ligature over spacer glyphs.
   * The glyph vector must come from the font and the render context of this instance.
   */
  @ApiStatus.Internal
  public float glyphLeftSideBearing(@NotNull GlyphVector glyphVector, int glyphIndex) {
    int glyphCode = glyphVector.getGlyphCode(glyphIndex);
    synchronized (this) {
      float cached = myGlyphLeftSideBearings.getOrDefault(glyphCode, Float.POSITIVE_INFINITY);
      if (cached != Float.POSITIVE_INFINITY) {
        return cached;
      }
    }
    GlyphMetrics metrics = glyphVector.getGlyphMetrics(glyphIndex);
    float leftSideBearing = metrics.getBounds2D().isEmpty() ? Float.NaN : metrics.getLSB();
    synchronized (this) {
      myGlyphLeftSideBearings.put(glyphCode, leftSideBearing);
    }
    return leftSideBearing;
  }

  /**
   * Returns true for a mark, such as a combining accent or a vowel sign, and for a format char, such as the zero width joiner.
   * The text layout keeps them with the char before them.
   */
  @ApiStatus.Internal
  public static boolean isMarkOrFormat(int codePoint) {
    int type = Character.getType(codePoint);
    return type == Character.NON_SPACING_MARK ||
           type == Character.ENCLOSING_MARK ||
           type == Character.COMBINING_SPACING_MARK ||
           type == Character.FORMAT;
  }

  /**
   * Returns false for a mark, a format char, and a letter of {@link #SCRIPTS_WITHOUT_LETTER_SPACING}, see {@link #takesLetterSpacing}.
   */
  @ApiStatus.Internal
  public static boolean takesLetterSpacingByUnicode(int codePoint) {
    if (!Character.isValidCodePoint(codePoint)) {
      return true;
    }
    if (isMarkOrFormat(codePoint)) {
      return false;
    }
    if (codePoint < FIRST_CODE_POINT_WITHOUT_LETTER_SPACING) {
      return true;
    }
    return codePoint != ARABIC_TATWEEL && !SCRIPTS_WITHOUT_LETTER_SPACING.contains(Character.UnicodeScript.of(codePoint));
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
    return o instanceof FontInfo other && myFont.equals(other.myFont) && myLetterSpacingPixels == other.myLetterSpacingPixels;
  }

  @Override
  public int hashCode() {
    return 31 * myFont.hashCode() + Float.hashCode(myLetterSpacingPixels);
  }

  @Override
  public synchronized String toString() {
    return "FontInfo{" +
           "myFont=" + myFont +
           ", mySize=" + mySize +
           ", myLetterSpacingPixels=" + myLetterSpacingPixels +
           ", mySafeCharacters=" + mySafeCharacters +
           ", myContext=" + myContext +
           ", myFontMetrics=" + myFontMetrics +
           '}';
  }
}
