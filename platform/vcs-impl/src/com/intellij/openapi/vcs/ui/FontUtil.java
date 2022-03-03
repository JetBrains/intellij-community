// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.impl.FontFallbackIterator;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;

public final class FontUtil {
  @NotNull
  @Nls
  public static String getHtmlWithFonts(@NotNull @Nls String input) {
    Font font = StartupUiUtil.getLabelFont();
    return getHtmlWithFonts(input, font.getStyle(), font);
  }

  @NotNull
  @Nls
  public static String getHtmlWithFonts(@NotNull @Nls String input, int style, @NotNull Font baseFont) {
    int start = baseFont.canDisplayUpTo(input);
    if (start == -1) return input;

    @Nls StringBuilder result = new StringBuilder();

    FontFallbackIterator it = new FontFallbackIterator();
    it.setPreferredFont(baseFont.getFamily(), baseFont.getSize());
    it.setFontStyle(style);

    it.start(input, 0, input.length());
    while (!it.atEnd()) {
      Font font = it.getFont();

      boolean insideFallbackBlock = !font.getFamily().equals(baseFont.getFamily());
      if (insideFallbackBlock) {
        result.append("<font face=\"").append(font.getFamily()).append("\">"); //NON-NLS
      }

      result.append(input, it.getStart(), it.getEnd());

      if (insideFallbackBlock) {
        result.append("</font>"); //NON-NLS
      }

      it.advance();
    }

    return result.toString();
  }

  @NotNull
  public static Font getEditorFont() {
    return EditorFontType.getGlobalPlainFont();
  }

  @NotNull
  public static Font getCommitMessageFont() {
    return getEditorFont();
  }

  public static Font getCommitMetadataFont() {
    return StartupUiUtil.getLabelFont();
  }

  public static int getStandardAscent(@NotNull Font font, @NotNull Graphics g) {
    FontRenderContext context = ((Graphics2D)g).getFontRenderContext();
    char[] chars = {'G', 'l', 'd', 'h', 'f'};
    double y = font.layoutGlyphVector(context, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT).getVisualBounds().getY();
    return Math.toIntExact(Math.round(Math.ceil(-y)));
  }
}
