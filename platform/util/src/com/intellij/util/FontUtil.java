/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class FontUtil {
  @NotNull
  public static String rightArrow(@NotNull Font font) {
    return canDisplay(font, '\u2192', "->");
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
   * In this case use {@link com.intellij.openapi.editor.ex.util.EditorUtil#displayCharInEditor()} instead.
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
    return canDisplay(UIUtil.getLabelFont(), '\u2009', " ");
  }

  @NotNull
  public static Font minusOne(@NotNull Font font) {
    return font.deriveFont(font.getSize() - 1f);
  }
}
