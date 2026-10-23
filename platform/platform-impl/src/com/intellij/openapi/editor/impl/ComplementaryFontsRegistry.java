// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.util.ui.JdkConstants.FontStyle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.util.Arrays;
import java.util.List;

public final class ComplementaryFontsRegistry {
  /**
   * This font renders all characters as empty glyphs, so there's no reason to use it for fallback.
   */
  private static final String ADOBE_BLANK = "Adobe Blank";
  private static final String BOLD_SUFFIX = ".bold";
  private static final String ITALIC_SUFFIX = ".italic";

  private static final ComplementaryFontsRegistryImpl INSTANCE = new ComplementaryFontsRegistryImpl(collectFallbackFontNames());

  private ComplementaryFontsRegistry() {
  }

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  public static @NotNull FontInfo getFontAbleToDisplay(
    @NotNull CharSequence text,
    int start,
    int end,
    @FontStyle int style,
    @NotNull FontPreferences preferences,
    @Nullable FontRenderContext context
  ) {
    return INSTANCE.getFontAbleToDisplay(text, start, end, style, preferences, context);
  }

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  public static @NotNull FontInfo getFontAbleToDisplay(
    char @NotNull [] text,
    int start,
    int end,
    @FontStyle int style,
    @NotNull FontPreferences preferences,
    @Nullable FontRenderContext context
  ) {
    return INSTANCE.getFontAbleToDisplay(text, start, end, style, preferences, context);
  }

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  public static @NotNull FontInfo getFontAbleToDisplay(
    int codePoint,
    @FontStyle int style,
    @NotNull FontPreferences preferences,
    @Nullable FontRenderContext context
  ) {
    return INSTANCE.getFontAbleToDisplay(codePoint, style, preferences, context);
  }

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  public static @NotNull FontInfo getFontAbleToDisplay(
    int codePoint,
    int size,
    @FontStyle int style,
    @NotNull String defaultFontFamily,
    @Nullable FontRenderContext context
  ) {
    return INSTANCE.getFontAbleToDisplay(codePoint, size, style, defaultFontFamily, context);
  }

  /**
   * Checks the code point in the preferred font families without the registry lock, the same way as
   * {@link #getFontAbleToDisplay(int, int, FontPreferences, FontRenderContext)} does under the lock.
   * The first check of a glyph can be slow, for example on macOS. After this call, the check under the lock is fast,
   * so a concurrent font request does not wait for it.
   */
  @ApiStatus.Internal
  public static void preloadGlyph(int codePoint, @FontStyle int style, @NotNull FontPreferences preferences) {
    INSTANCE.preloadGlyph(codePoint, style, preferences);
  }

  private static @NotNull List<String> collectFallbackFontNames() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return List.of(Font.MONOSPACED);
    }
    // This must match the "system fonts loading" call in com.intellij.platform.ide.bootstrap.initUi, which caches the result at startup
    String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    return Arrays.stream(names)
      .filter(fontName -> {
        return !fontName.endsWith(BOLD_SUFFIX) &&
               !fontName.endsWith(ITALIC_SUFFIX) &&
               !fontName.equals(ADOBE_BLANK);
      })
      .toList();
  }
}
