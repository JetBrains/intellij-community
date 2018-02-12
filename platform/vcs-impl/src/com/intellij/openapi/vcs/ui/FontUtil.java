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

import com.intellij.openapi.editor.impl.FontFallbackIterator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

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
}
