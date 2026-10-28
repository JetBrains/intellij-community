// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.JdkConstants.FontStyle;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Holds the state of {@link ComplementaryFontsRegistry} and finds the fonts for it.
 * <p>
 * A search tries these fonts in order and stops at the first font that can display the code point:
 * <ol>
 *   <li>the preferred font families of the {@link FontPreferences}, or the given font family</li>
 *   <li>{@link Font#MONOSPACED}, if the first step did not try it</li>
 *   <li>the fallback fonts that earlier searches picked</li>
 *   <li>the other installed fonts</li>
 *   <li>the placeholder font, if no font fits</li>
 * </ol>
 * When the text has more code points, the font must display all of them.
 * <p>
 * The class is thread-safe. A lock on the instance guards all mutable state.
 */
final class ComplementaryFontsRegistryImpl {
  private static final Logger LOG = Logger.getInstance(ComplementaryFontsRegistryImpl.class);
  private static final String DEFAULT_FALLBACK_FONT = Font.MONOSPACED;

  private final Map<Font, FontFallback> preferredFamilyFonts = new HashMap<>();
  private final StyleState[] styles;

  /**
   * The font that shows a placeholder glyph for a character that no installed font can display.
   * The placeholder is the glyph with code 0 of this font.
   */
  private final FontFallback undisplayableFallback = new FontFallback(FontPreferences.JETBRAINS_MONO, Font.PLAIN);

  private @Nullable String lastFontFamily;
  private @Nullable String lastRegularSubFamily;
  private @Nullable String lastBoldSubFamily;
  private boolean lastTypographicNames;

  /**
   * @param fontNames the font families to search when the preferred font families cannot display a character.
   *                  The registry keeps its own copy of the list.
   */
  ComplementaryFontsRegistryImpl(@NotNull List<String> fontNames) {
    styles = new StyleState[] {
      new StyleState(fontNames), // PLAIN
      new StyleState(fontNames), // BOLD
      new StyleState(fontNames), // ITALIC
      new StyleState(fontNames) // BOLD | ITALIC
    };
  }

  @NotNull FontInfo getFontAbleToDisplay(
    @NotNull CharSequence text,
    int start,
    int end,
    @FontStyle int style,
    @NotNull FontPreferences preferences,
    @Nullable FontRenderContext context
  ) {
    assert 0 <= start && start < end && end <= text.length() : "Start: " + start + ", end: " + end + ", length: " + text.length();
    if (end - start == 1) {
      // fast path for BMP code points
      return getFontAbleToDisplay(text.charAt(start), style, preferences, context);
    }
    int firstCodePoint = Character.codePointAt(text, start);
    int secondOffset = Character.offsetByCodePoints(text, start, 1);
    if (secondOffset == end) {
      // fast path for a single SMP code point
      return getFontAbleToDisplay(firstCodePoint, style, preferences, context);
    }
    char[] tmp = CharArrayUtil.fromSequence(text, secondOffset, end);
    return findFont(firstCodePoint, tmp, 0, tmp.length, style, preferences, context);
  }

  @NotNull FontInfo getFontAbleToDisplay(
    char @NotNull [] text,
    int start,
    int end,
    @FontStyle int style,
    @NotNull FontPreferences preferences,
    @Nullable FontRenderContext context
  ) {
    assert 0 <= start && start < end && end <= text.length : "Start: " + start + ", end: " + end + ", length: " + text.length;
    if (end - start == 1) {
      // fast path for BMP code points
      return getFontAbleToDisplay(text[start], style, preferences, context);
    }
    int firstCodePoint = Character.codePointAt(text, start);
    int secondOffset = Character.offsetByCodePoints(text, start, end - start, start, 1);
    if (secondOffset == end) {
      // fast path for a single SMP code point
      return getFontAbleToDisplay(firstCodePoint, style, preferences, context);
    }
    return findFont(firstCodePoint, text, secondOffset, end, style, preferences, context);
  }

  @NotNull FontInfo getFontAbleToDisplay(
    int codePoint,
    @FontStyle int style,
    @NotNull FontPreferences preferences,
    @Nullable FontRenderContext context
  ) {
    return findFont(codePoint, null, 0, 0, style, preferences, context);
  }

  @NotNull FontInfo getFontAbleToDisplay(
    int codePoint,
    int size,
    @FontStyle int style,
    @NotNull String defaultFontFamily,
    @Nullable FontRenderContext context
  ) {
    FontInfo result = findInFamily(codePoint, size, style, defaultFontFamily, false, Set.of(), context);
    if (result != null) {
      return result;
    }
    if (!DEFAULT_FALLBACK_FONT.equals(defaultFontFamily)) {
      result = findInFamily(codePoint, size, style, DEFAULT_FALLBACK_FONT, false, Set.of(), context);
      if (result != null) {
        return result;
      }
    }
    return findInFallbackFonts(codePoint, null, 0, 0, size, style, false, Set.of(), context);
  }

  void preloadGlyph(int codePoint, @FontStyle int style, @NotNull FontPreferences preferences) {
    style = validStyle(style);
    // One version for all the reads below
    preferences = preferences.getSnapshot();
    List<String> fontFamilies = preferences.getEffectiveFontFamilies();
    for (int i = 0, len = fontFamilies.size(); i < len; i++) {
      Font font = FontFamilyService.getFont(
        fontFamilies.get(i),
        i == 0 ? preferences.getRegularSubFamily() : null,
        i == 0 ? preferences.getBoldSubFamily() : null,
        style
      );
      if (FontInfo.canDisplay(font, codePoint, true)) {
        return;
      }
    }
  }

  /**
   * @param remainingText the text after {@code codePoint} that the font must also display, or {@code null} for a single code point
   */
  private @NotNull FontInfo findFont(
    int codePoint,
    char @Nullable [] remainingText,
    int start,
    int end,
    @FontStyle int style,
    @NotNull FontPreferences preferences,
    @Nullable FontRenderContext context
  ) {
    // One version for all the reads below
    preferences = preferences.getSnapshot();
    boolean tryDefaultFallback = true;
    List<String> fontFamilies = preferences.getEffectiveFontFamilies();
    boolean useLigatures = preferences.useLigatures();
    Set<String> variants = preferences.getCharacterVariants();
    FontInfo result;
    // avoid foreach, it instantiates ArrayList$Itr, this traversal happens very often
    for (int i = 0, len = fontFamilies.size(); i < len; i++) {
      String fontFamily = fontFamilies.get(i);
      result = findInPreferredFamily(
        codePoint,
        preferences.getSize2D(fontFamily),
        style,
        fontFamily,
        i == 0 ? preferences.getRegularSubFamily() : null,
        i == 0 ? preferences.getBoldSubFamily() : null,
        useLigatures,
        variants,
        context
      );
      if (result != null && canDisplayAll(result.getFont(), remainingText, start, end)) {
        return result;
      }
      tryDefaultFallback &= !DEFAULT_FALLBACK_FONT.equals(fontFamily);
    }
    float size = FontPreferences.DEFAULT_FONT_SIZE;
    if (!fontFamilies.isEmpty()) {
      size = preferences.getSize2D(fontFamilies.getFirst());
    }
    if (tryDefaultFallback) {
      result = findInFamily(codePoint, size, style, DEFAULT_FALLBACK_FONT, useLigatures, Set.of(), context);
      if (result != null && canDisplayAll(result.getFont(), remainingText, start, end)) {
        return result;
      }
    }
    result = findInFallbackFonts(codePoint, remainingText, start, end, size, style, useLigatures, Set.of(), context);
    if (LOG.isTraceEnabled()) {
      LOG.trace("Fallback font: " + result.getFont().getFontName());
    }
    return result;
  }

  /**
   * Finds the font in a preferred font family by its typographic family and subfamily names.
   * On macOS, the glyph check uses only this font and not the fallback fonts of the system.
   */
  private synchronized @Nullable FontInfo findInPreferredFamily(
    int codePoint,
    float size,
    @FontStyle int style,
    @NotNull String fontFamily,
    @Nullable String regularSubFamily,
    @Nullable String boldSubFamily,
    boolean useLigatures,
    @Unmodifiable @NotNull Set<@NotNull String> variants,
    @Nullable FontRenderContext context
  ) {
    style = validStyle(style);
    StyleState state = styles[style];
    FontFallback fallback = getLastFallback(state, fontFamily, regularSubFamily, boldSubFamily, true);
    if (fallback == null) {
      Font font = FontFamilyService.getFont(fontFamily, regularSubFamily, boldSubFamily, style);
      fallback = preferredFamilyFonts.computeIfAbsent(font, FontFallback::new);
      state.lastFallback = fallback;
    }
    if (fallback.canDisplay(codePoint, true)) {
      return fallback.getFontInfo(size, useLigatures, variants, context);
    }
    return null;
  }

  /**
   * Finds the font in a font family by its plain family name.
   */
  private synchronized @Nullable FontInfo findInFamily(
    int codePoint,
    float size,
    @FontStyle int style,
    @NotNull String fontFamily,
    boolean useLigatures,
    @Unmodifiable @NotNull Set<@NotNull String> variants,
    @Nullable FontRenderContext context
  ) {
    style = validStyle(style);
    StyleState state = styles[style];
    FontFallback fallback = getLastFallback(state, fontFamily, null, null, false);
    if (fallback == null) {
      fallback = state.usedFonts.get(fontFamily);
      if (fallback == null) {
        fallback = new FontFallback(fontFamily, style);
        state.usedFonts.put(fontFamily, fallback);
      }
      state.lastFallback = fallback;
    }
    if (fallback.canDisplay(codePoint, false)) {
      return fallback.getFontInfo(size, useLigatures, variants, context);
    }
    return null;
  }

  /**
   * Returns the fallback that the last search with the same font family found for the style, or {@code null}.
   * A search with another font family clears the last fallbacks of all styles.
   */
  private @Nullable FontFallback getLastFallback(
    @NotNull StyleState state,
    @NotNull String fontFamily,
    @Nullable String regularSubFamily,
    @Nullable String boldSubFamily,
    boolean useTypographicNames
  ) {
    if (useTypographicNames == lastTypographicNames &&
        fontFamily.equals(lastFontFamily) &&
        (!useTypographicNames ||
         Objects.equals(regularSubFamily, lastRegularSubFamily) && Objects.equals(boldSubFamily, lastBoldSubFamily))) {
      return state.lastFallback;
    }
    lastTypographicNames = useTypographicNames;
    lastFontFamily = fontFamily;
    lastRegularSubFamily = regularSubFamily;
    lastBoldSubFamily = boldSubFamily;
    clearLastFallbacks();
    return null;
  }

  /**
   * Finds the font among the fallback fonts that earlier searches picked, and then among the other installed fonts.
   * Returns the placeholder font if no font fits.
   */
  private synchronized @NotNull FontInfo findInFallbackFonts(
    int codePoint,
    char @Nullable [] remainingText,
    int start,
    int end,
    float size,
    @FontStyle int style,
    boolean useLigatures,
    @Unmodifiable @NotNull Set<@NotNull String> variants,
    @Nullable FontRenderContext context
  ) {
    style = validStyle(style);
    StyleState state = styles[style];
    FontFallback fallback = undisplayableFallback;
    if (!state.undisplayableChars.contains(codePoint)) {
      boolean canDisplayFirst = false;
      for (FontFallback candidate : state.usedFonts.values()) {
        if (candidate.canDisplay(codePoint, false)) {
          canDisplayFirst = true;
          if (canDisplayAll(candidate.baseFont(), remainingText, start, end)) {
            fallback = candidate;
            break;
          }
        }
      }
      if (fallback == undisplayableFallback) {
        List<String> fontNames = state.fontNames;
        for (int i = 0, len = fontNames.size(); i < len; i++) {
          String name = fontNames.get(i);
          FontFallback candidate = new FontFallback(name, style);
          if (candidate.canDisplay(codePoint, false)) {
            canDisplayFirst = true;
            if (canDisplayAll(candidate.baseFont(), remainingText, start, end)) {
              state.usedFonts.put(name, candidate);
              fontNames.remove(i);
              fallback = candidate;
              break;
            }
          }
        }
        if (fallback == undisplayableFallback && !canDisplayFirst) {
          state.undisplayableChars.add(codePoint);
        }
      }
    }
    return fallback.getFontInfo(size, useLigatures, variants, context);
  }

  private void clearLastFallbacks() {
    for (StyleState state : styles) {
      state.lastFallback = null;
    }
  }

  private static boolean canDisplayAll(@NotNull Font font, char @Nullable [] text, int start, int end) {
    return text == null || font.canDisplayUpTo(text, start, end) == -1;
  }

  private static @FontStyle int validStyle(@FontStyle int style) {
    return style < 0 || style > 3 ? Font.PLAIN : style;
  }

  /**
   * The search state of the registry for one font style.
   */
  private static final class StyleState {
    /**
     * The fallback font families that the search did not pick yet.
     */
    final List<String> fontNames;
    final LinkedHashMap<String, FontFallback> usedFonts = new LinkedHashMap<>();
    final IntSet undisplayableChars = new IntOpenHashSet();
    @Nullable FontFallback lastFallback;

    StyleState(@NotNull List<String> fontNames) {
      this.fontNames = new ArrayList<>(fontNames);
    }
  }
}
