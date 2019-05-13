/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.impl.FontFallbackIterator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import sun.java2d.SunGraphics2D;

import java.awt.*;
import java.awt.font.FontRenderContext;

public class FontUtil {
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
