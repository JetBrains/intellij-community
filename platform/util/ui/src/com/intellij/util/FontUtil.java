// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class FontUtil {
  @NotNull
  public static String rightArrow(@NotNull Font font) {
    return canDisplay(font, '\u2192', "->");
  }

  public static boolean isValidFont(@NotNull Font font) {
    try {
      return font.canDisplay('a') &&
             font.canDisplay('z') &&
             font.canDisplay('A') &&
             font.canDisplay('Z') &&
             font.canDisplay('0') &&
             font.canDisplay('1');
    }
    catch (Exception e) {
      // JRE has problems working with the font. Just skip.
      return false;
    }
  }

  @NotNull
  public static String upArrow(@NotNull Font font, @NotNull String defaultValue) {
    return canDisplay(font, '\u2191', defaultValue);
  }

  /**
   * The method checks whether the font can display the character.
   *
   * If the character should be shown in editor, the method might return incorrect result,
   * since the editor will try to use fallback fonts if the base one cannot display the character.
   * In this case use {@link com.intellij.openapi.editor.ex.util.EditorUtil#displayCharInEditor(char, com.intellij.openapi.editor.colors.TextAttributesKey, String)} instead.
   */
  @NotNull
  public static String canDisplay(@NotNull Font font, char value, @NotNull String defaultValue) {
    return font.canDisplay(value) ? String.valueOf(value) : defaultValue;
  }

  @NotNull
  public static String spaceAndThinSpace() {
    return " " + thinSpace();
  }

  @NotNull
  public static String thinSpace() {
    return canDisplay(StartupUiUtil.getLabelFont(), '\u2009', " ");
  }

  @NotNull
  public static Font minusOne(@NotNull Font font) {
    return font.deriveFont(font.getSize() - 1f);
  }
}
