// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.util.ui.JdkConstants.FontStyle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.awt.font.FontRenderContext;

/**
 * This iterator finds font(s) that can display a given text.
 * Text's characters are grouped into ranges that can be displayed using the same font.
 * <p>
 * Instances can be reused for different texts or preferred fonts -
 * each {@code start} method invocation initiates a new iteration.
 * All required font-related properties for a given iteration should be set before corresponding
 * {@code start} method invocation.
 * <p>
 * Sample usage scenario:
 * <code><pre>
 *   char[] text = getSourceText();
 *   FontFallbackIterator it = new FontFallbackIterator();
 *   it.start(text, 0, text.length);
 *   while (!it.atEnd()) {
 *     processFragment(new String(text, it.getStart(), it.getEnd()), it.getFont()));
 *     it.advance();
 *   }
 * </pre></code>
 */
public final class FontFallbackIterator {
  private FontFallbackIteratorImpl impl;

  public FontFallbackIterator() {
    this(new FontPreferencesImpl(), null, Font.PLAIN);
  }

  @ApiStatus.Internal
  public FontFallbackIterator(
    @NotNull FontPreferences preferences,
    @Nullable FontRenderContext context,
    @FontStyle int style
  ) {
    impl = new FontFallbackIteratorImpl(preferences, context, style);
  }

  public FontFallbackIterator setPreferredFonts(@NotNull FontPreferences fontPreferences) {
    impl = impl.withFontPreferences(fontPreferences);
    return this;
  }

  /// @noinspection UnusedReturnValue
  public FontFallbackIterator setPreferredFont(@NotNull String familyName, int size) {
    FontPreferencesImpl preferences = new FontPreferencesImpl();
    preferences.register(familyName, size);
    return setPreferredFonts(preferences);
  }

  public FontFallbackIterator setFontStyle(@FontStyle int fontStyle) {
    impl = impl.withFontStyle(fontStyle);
    return this;
  }

  public FontFallbackIterator setFontRenderContext(@Nullable FontRenderContext fontRenderContext) {
    impl = impl.withFontRenderContext(fontRenderContext);
    return this;
  }

  public void start(@NotNull CharSequence text, int start, int end) {
    impl.start(text, start, end);
  }

  public void start(char @NotNull [] text, int start, int end) {
    impl.start(text, start, end);
  }

  public boolean atEnd() {
    return impl.atEnd();
  }

  public void advance() {
    impl.advance();
  }

  public int getStart() {
    return impl.getStart();
  }

  public int getEnd() {
    return impl.getEnd();
  }

  public @NotNull FontInfo getFontInfo() {
    return impl.getFontInfo();
  }

  public @NotNull Font getFont() {
    return impl.getFont();
  }
}
