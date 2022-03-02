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

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.markup.TextAttributes;
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

  public void testResolveConflictingColor() {
    final EditorColorsScheme colorsScheme = (EditorColorsScheme)EditorColorsManager.getInstance().getScheme("Default").clone();
    final Color newColorInPalette = EditorColorPaletteFactory.getInstance().getPalette(colorsScheme, Language.ANY)
      .withBackgroundColors()
      .withForegroundColors()
      .getClosestNonConflictingColor(Color.YELLOW);

    final TextAttributes newColorAttrInPallete = new TextAttributes();
    newColorAttrInPallete.setForegroundColor(newColorInPalette);
    
    // rainbow color generator can reuse LOCAL_VARIABLE or PARAM foreground color 
    colorsScheme.setAttributes(DefaultLanguageHighlighterColors.LOCAL_VARIABLE, newColorAttrInPallete);
    checkUsed(colorsScheme, newColorInPalette, true);

    // rainbow color generator can not reuse any other foreground color
    final TextAttributes bcOrigAttr = colorsScheme.getAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    checkUsed(colorsScheme, bcOrigAttr.getForegroundColor(), false);

    final TextAttributesKey generatedRainbowKey = RainbowHighlighter.getRainbowTempKeys()[0];
    
    // make BLOCK_COMMENT the same as LOCAL_VARIABLE => has conflict with non-rainbow BLOCK_COMMENT color 
    colorsScheme.setAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT, newColorAttrInPallete);
    checkUsed(colorsScheme, newColorInPalette, false);
    // rainbow generated colors are cached:
    assertNotNull(colorsScheme.getAttributes(generatedRainbowKey));
    
    // change in colorsScheme for non-mutable key drops generated cache
    colorsScheme.setAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT, bcOrigAttr);
    // there are no rainbow generated colors:
    assertNull(colorsScheme.getAttributes(generatedRainbowKey));  

    // resolve conflict for BLACK color (checking overflow)
    final TextAttributes blackColorAttrInPallete = new TextAttributes();
    blackColorAttrInPallete.setForegroundColor(Color.BLACK);
    colorsScheme.setAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT, blackColorAttrInPallete);
    checkUsed(colorsScheme, Color.BLACK, false);

    // resolve conflict for WHITE color (checking overflow)
    final TextAttributes whiteColorAttrInPallete = new TextAttributes();
    whiteColorAttrInPallete.setForegroundColor(Color.WHITE);
    colorsScheme.setAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT, whiteColorAttrInPallete);
    checkUsed(colorsScheme, Color.WHITE, false);
  }

  private static void checkUsed(@NotNull EditorColorsScheme colorsScheme, @NotNull Color testColor, boolean needToBeReused) {
    assertNotNull(testColor);
    colorsScheme.setAttributes(RainbowHighlighter.RAINBOW_COLOR_KEYS[0], RainbowHighlighter.createRainbowAttribute(testColor));
    final Color[] colors = RainbowHighlighter.testRainbowGenerateColors(colorsScheme);
    final double minimalColorDistance = 0.02;
    int i = 0;
    for (Color color : colors) {
      if (needToBeReused) {
        assertEquals(testColor, color);
        break;
      } 
      else {
        assertTrue(i++ + ": " + testColor + " vs " + color,
                   RainbowHighlighter.colorDistance01(testColor, color) >= minimalColorDistance);
      }
    }
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
