// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.impl.FontFallbackIterator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;

public final class FontUtil {
  @NotNull
  public static String getHtmlWithFonts(@NotNull String input) {
    Font font = UIUtil.getLabelFont();
    return getHtmlWithFonts(input, font.getStyle(), font);
  }

  @NotNull
  public static String getHtmlWithFonts(@NotNull String input, int style, @NotNull Font baseFont) {
    int start = baseFont.canDisplayUpTo(input);
    if (start == -1) return input;

    StringBuilder result = new StringBuilder();

    FontFallbackIterator it = new FontFallbackIterator();
    it.setPreferredFont(baseFont.getFamily(), baseFont.getSize());
    it.setFontStyle(style);

    it.start(input, 0, input.length());
    while (!it.atEnd()) {
      Font font = it.getFont();

      boolean insideFallbackBlock = !font.getFamily().equals(baseFont.getFamily());
      if (insideFallbackBlock) {
        result.append("<font face=\"").append(font.getFamily()).append("\">");
      }

      result.append(input, it.getStart(), it.getEnd());

      if (insideFallbackBlock) {
        result.append("</font>");
      }

      it.advance();
    }

    return result.toString();
  }

  @NotNull
  public static Font getEditorFont() {
    return EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
  }

  @NotNull
  public static Font getCommitMessageFont() {
    return getEditorFont();
  }

  public static Font getCommitMetadataFont() {
    return UIUtil.getLabelFont();
  }

  public static int getStandardAscent(@NotNull Font font, @NotNull Graphics g) {
    FontRenderContext context = ((Graphics2D)g).getFontRenderContext();
    char[] chars = {'G', 'l', 'd', 'h', 'f'};
    double y = font.layoutGlyphVector(context, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT).getVisualBounds().getY();
    return Math.toIntExact(Math.round(Math.ceil(-y)));
  }
}
