// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.awt.font.TextAttribute.KERNING;
import static java.awt.font.TextAttribute.KERNING_ON;
import static java.util.Collections.singletonMap;

public final class FontUtil {
  public static Font getMenuFont() {
    return UIManager.getFont("Menu.font");
  }

  public static String @NotNull [] getValidFontNames(final boolean familyName) {
    Set<String> result = new TreeSet<>();

    // adds fonts that can display symbols at [A, Z] + [a, z] + [0, 9]
    for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
      try {
        if (isValidFont(font)) {
          result.add(familyName ? font.getFamily() : font.getName());
        }
      }
      catch (Exception ignore) {
        // JRE has problems working with the font. Just skip.
      }
    }

    // add label font (if isn't listed among above)
    Font labelFont = StartupUiUtil.getLabelFont();
    if (isValidFont(labelFont)) {
      result.add(familyName ? labelFont.getFamily() : labelFont.getName());
    }

    return ArrayUtilRt.toStringArray(result);
  }

  public static @NotNull String leftArrow(@NotNull Font font) {
    return canDisplay(font, '\u2190', "<-");
  }

  public static @NotNull @NlsSafe String rightArrow(@NotNull Font font) {
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

  public static @NotNull String upArrow(@NotNull Font font, @NotNull String defaultValue) {
    return canDisplay(font, '\u2191', defaultValue);
  }

  /**
   * The method checks whether the font can display the character.
   * <p>
   * If the character should be shown in editor, the method might return incorrect result,
   * since the editor will try to use fallback fonts if the base one cannot display the character.
   * In this case use {@code com.intellij.openapi.editor.ex.util.EditorUtil#displayCharInEditor(char, com.intellij.openapi.editor.colors.TextAttributesKey, String)} instead.
   * </p>
   */
  public static @NotNull String canDisplay(@NotNull Font font, char value, @NotNull String defaultValue) {
    return font.canDisplay(value) ? String.valueOf(value) : defaultValue;
  }

  public static @NotNull @NlsSafe String spaceAndThinSpace() {
    return " " + thinSpace();
  }

  public static @NotNull String thinSpace() {
    return canDisplay(StartupUiUtil.getLabelFont(), '\u2009', " ");
  }

  public static @NotNull Font minusOne(@NotNull Font font) {
    return font.deriveFont(font.getSize() - 1f);
  }

  /**
   * @param oldFont    a base font to derive from
   * @param attributes a map of attributes to override in the given font
   * @return a new font that replicates the given font with the specified attributes
   */
  public static @NotNull Font deriveFont(@NotNull Font oldFont, @NotNull Map<? extends Attribute, ?> attributes) {
    Font newFont = oldFont.deriveFont(attributes);
    return oldFont instanceof UIResource ? new FontUIResource(newFont) : newFont;
  }

  /**
   * @param font a base font to derive from
   * @return a new font that replicates the given font without a kerning attribute
   */
  public static @NotNull Font disableKerning(@NotNull Font font) {
    return deriveFont(font, DisableKerning.LAZY);
  }

  private static final class DisableKerning {
    private static final Map<Attribute, Integer> LAZY = singletonMap(KERNING, null);
  }


  /**
   * @param font a base font to derive from
   * @return a new font that replicates the given font with a kerning attribute
   */
  public static @NotNull Font enableKerning(@NotNull Font font) {
    return deriveFont(font, EnableKerning.LAZY);
  }

  private static final class EnableKerning {
    private static final Map<Attribute, Integer> LAZY = singletonMap(KERNING, KERNING_ON);
  }
}
