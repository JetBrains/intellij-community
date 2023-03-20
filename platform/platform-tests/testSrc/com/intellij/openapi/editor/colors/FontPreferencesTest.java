/*
 * Copyright 2000-2013 JetBrains s.r.o.
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


import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import org.junit.Test;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FontPreferencesTest {
  private final FontPreferencesImpl myPreferences = new FontPreferencesImpl();

  @Test
  public void testDefaults() {
    checkState(Collections.emptyList(),
               Collections.emptyList(),
               FontPreferences.DEFAULT_FONT_NAME,
               FontPreferences.DEFAULT_FONT_NAME, null);
  }

  @Test
  public void testRegisterExistingFont() {
    String fontName = getExistingNonDefaultFontName();
    myPreferences.register(fontName, 25);
    checkState(Arrays.asList(fontName),
               Arrays.asList(fontName),
               fontName,
               fontName, 25);
  }

  @Test
  public void testRegisterTwoExistingFonts() {
    String fontName = getExistingNonDefaultFontName();
    myPreferences.register(fontName, 25);
    String fontName2 = getAnotherExistingNonDefaultFontName();
    myPreferences.register(fontName2, 13);
    checkState(Arrays.asList(fontName, fontName2),
               Arrays.asList(fontName, fontName2),
               fontName,
               fontName, 25,
               fontName2, 13);
  }

  @Test
  public void testRegisterNonExistingFont() {
    String fontName = getNonExistingFontName();
    myPreferences.register(fontName, 25);
    checkState(Arrays.asList(fontName),
               Arrays.asList(fontName),
               fontName,
               fontName, 25);
  }

  @Test
  public void testAddExistingFont() {
    String fontName = getExistingNonDefaultFontName();
    myPreferences.addFontFamily(fontName);
    checkState(Arrays.asList(fontName),
               Arrays.asList(fontName),
               fontName,
               fontName, null);
  }

  @Test
  public void testAddNonExistingFont() {
    String fontName = getNonExistingFontName();
    myPreferences.addFontFamily(fontName);
    checkState(Arrays.asList(fontName),
               Arrays.asList(fontName),
               fontName,
               fontName, null);
  }

  private void checkState(java.util.List<String> expectedRealFontFamilies,
                          java.util.List<String> expectedEffectiveFontFamilies,
                          String expectedFontFamily,
                          Object... namesAndSizes) {
    checkState(myPreferences,
               expectedRealFontFamilies,
               expectedEffectiveFontFamilies,
               expectedFontFamily,
               namesAndSizes);

    // check object copying
    FontPreferencesImpl preferences = new FontPreferencesImpl();
    myPreferences.copyTo(preferences);
    // check myTemplateFontSize
    String fontName = "Another" + getNonExistingFontName();
    assertEquals(myPreferences.getSize(fontName), preferences.getSize(fontName));
    // check other fields
    checkState(myPreferences,
               preferences.getRealFontFamilies(),
               preferences.getEffectiveFontFamilies(),
               preferences.getFontFamily());
  }

  public static void checkState(FontPreferences fontPreferences,
                          java.util.List<String> expectedRealFontFamilies,
                          java.util.List<String> expectedEffectiveFontFamilies,
                          String expectedFontFamily,
                          Object... namesAndSizes) {
    assertEquals("Wrong real font families", expectedRealFontFamilies, fontPreferences.getRealFontFamilies());
    assertEquals("Wrong effective font families", expectedEffectiveFontFamilies, fontPreferences.getEffectiveFontFamilies());
    assertEquals("Wrong font family", expectedFontFamily, fontPreferences.getFontFamily());
    for (int i = 0; i < namesAndSizes.length - 1; ) {
      String fontName = (String)namesAndSizes[i++];
      Number fontSize = (Number)namesAndSizes[i++];
      assertEquals("Wrong hasSize", fontSize != null, fontPreferences.hasSize(fontName));
      assertEquals("Wrong font size", fontSize == null ? (float)FontPreferences.DEFAULT_FONT_SIZE : fontSize.floatValue(), fontPreferences.getSize2D(fontName), 0.001f);
    }
  }

  public static String getExistingNonDefaultFontName() {
    return getExistingFontNameButNot(FontPreferences.DEFAULT_FONT_NAME);
  }

  public static String getAnotherExistingNonDefaultFontName() {
    String firstOne = getExistingFontNameButNot(FontPreferences.DEFAULT_FONT_NAME);
    return getExistingFontNameButNot(FontPreferences.DEFAULT_FONT_NAME, firstOne);
  }

  private static String getExistingFontNameButNot(String... names) {
    String[] fontFamilyNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    for (String name : fontFamilyNames) {
      if (name != null && !Arrays.asList(names).contains(name)) {
        return name;
      }
    }
    fail("Couldn't find existing font not in " + Arrays.toString(names));
    return null;
  }

  public static String getNonExistingFontName() {
    return "DefinitelyNonExistingFontName";
  }
}
