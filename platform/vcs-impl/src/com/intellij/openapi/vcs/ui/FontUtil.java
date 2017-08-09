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

import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class FontUtil {
  @Nullable
  public static Font getFontAbleToDisplay(char c, int size, int style, @NotNull String family) {
    Font font = ComplementaryFontsRegistry.getFontAbleToDisplay(c, size, style, family, null).getFont();
    return font.canDisplay(c) ? font : null;
  }

  @NotNull
  public static String getHtmlWithFonts(@NotNull String input) {
    Font font = UIUtil.getLabelFont();
    return getHtmlWithFonts(input, font.getStyle(), font);
  }

  @NotNull
  public static String getHtmlWithFonts(@NotNull String input, int style, @NotNull Font baseFont) {
    int start = baseFont.canDisplayUpTo(input);
    if (start == -1) return input;

    Font font = null;
    StringBuilder result = new StringBuilder(input.substring(0, start));
    for (int i = start; i < input.length(); i++) {
      char c = input.charAt(i);
      if (baseFont.canDisplay(c)) {
        if (font != null) result.append("</font>");
        result.append(c);
        font = null;
      }
      else if (font != null && font.canDisplay(c)) {
        result.append(c);
      }
      else {
        if (font != null) result.append("</font>");
        font = getFontAbleToDisplay(c, baseFont.getSize(), style, baseFont.getFamily());
        if (font != null) result.append("<font face=\"").append(font.getFamily()).append("\">");
        result.append(c);
      }
    }
    if (font != null) result.append("</font>");

    return result.toString();
  }
}
