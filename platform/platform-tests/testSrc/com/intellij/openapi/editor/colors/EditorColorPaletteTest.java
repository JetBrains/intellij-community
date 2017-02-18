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
package com.intellij.openapi.editor.colors;

import com.intellij.lang.Language;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collection;

@SuppressWarnings("SameParameterValue")
public class EditorColorPaletteTest extends LightPlatformTestCase {
  
  public void testDefaultColors() {
    checkSchemeBackgrounds("Default", 80, 255);
  }
  
  public void testDarculaColors() {
    checkSchemeBackgrounds("Darcula", 0, 175);
  }

  public void testGetNonConflictingColor() {
    Color sampleColor = new Color(0, 0, 255);
    EditorColorPalette palette = getAllColorsPalette("Default");
    Color nonConflictingColor = palette.getClosestNonConflictingColor(sampleColor);
    assertNotNull(nonConflictingColor);
    assertTrue(palette.getColors(EditorColorPalette.ORDER_NONE).contains(sampleColor));
    assertFalse(palette.getColors(EditorColorPalette.ORDER_NONE).contains(nonConflictingColor));
  }
  
  private static void checkSchemeBackgrounds(@NotNull String schemeName, int minIntensity, int maxIntensity) {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(schemeName);
    EditorColorPalette palette = EditorColorPaletteFactory.getInstance().getPalette(defaultScheme, Language.ANY).withBackgroundColors();
    Collection<Color> colors = palette.getColors(EditorColorPalette.ORDER_BY_INTENSITY);
    checkIntensity(colors, minIntensity, maxIntensity);
  }
  
  private static EditorColorPalette getAllColorsPalette(@NotNull String schemeName) {
    EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(schemeName);
    return
      EditorColorPaletteFactory.getInstance().getPalette(defaultScheme, Language.ANY)
        .withBackgroundColors()
        .withForegroundColors();
  }
  
  private static void checkIntensity(@NotNull Collection<Color> colors, int minIntensity, int maxIntensity) {
    for (Color color : colors) {
      int intensity = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
      assertTrue(color + " doesn't fit to intensity range [" + minIntensity + ".." + maxIntensity + "]",
                 intensity >= minIntensity && intensity <= maxIntensity);
    }
  }
}
