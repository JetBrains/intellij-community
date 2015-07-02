/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import gnu.trove.TIntHashSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class ComplementaryFontsRegistry {
  private static final Object lock = new String("common lock");
  private static final List<String> ourFontNames;
  private static final Map<String, Pair<String, Integer>[]> ourStyledFontMap = new HashMap<String, Pair<String, Integer>[]>();
  private static final LinkedHashMap<FontKey, FontInfo> ourUsedFonts;
  private static FontKey ourSharedKeyInstance = new FontKey("", 0, 0);
  private static FontInfo ourSharedDefaultFont;
  private static final TIntHashSet ourUndisplayableChars = new TIntHashSet();
  private static boolean ourOldUseAntialiasing;

  // This matches style detection in JDK (class sun.font.Font2D)
  private static final String[] BOLD_NAMES = {"bold", "demibold", "demi-bold", "demi bold", "negreta", "demi" };
  private static final String[] ITALIC_NAMES = {"italic", "cursiva", "oblique", "inclined"};
  private static final String[] BOLD_ITALIC_NAMES = {"bolditalic", "bold-italic", "bold italic", "boldoblique", "bold-oblique", 
    "bold oblique", "demibold italic", "negreta cursiva","demi oblique"};

  static {
    final UISettings settings = UISettings.getInstance();
    ourOldUseAntialiasing = settings.ANTIALIASING_IN_EDITOR;

    // Reset font info on 'use antialiasing' setting change.
    // Assuming that the listener is notified from the EDT only.
    settings.addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        if (ourOldUseAntialiasing ^ source.ANTIALIASING_IN_EDITOR) {
          ourOldUseAntialiasing = source.ANTIALIASING_IN_EDITOR;
          for (FontInfo fontInfo : ourUsedFonts.values()) {
            fontInfo.reset();
          }
          ourUsedFonts.clear();
        }
      }
    }, ApplicationManager.getApplication());
  }
  
  private ComplementaryFontsRegistry() {
  }

  private static class FontKey {
    public String myFamilyName;
    public int mySize;
    public int myStyle;

    public FontKey(@NotNull String familyName, final int size, @JdkConstants.FontStyle int style) {
      myFamilyName = familyName;
      mySize = size;
      myStyle = style;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      final FontKey fontKey = (FontKey)o;

      if (mySize != fontKey.mySize) return false;
      if (myStyle != fontKey.myStyle) return false;
      return myFamilyName.equals(fontKey.myFamilyName);
    }

    public int hashCode() {
      int result = myFamilyName.hashCode();
      result = 29 * result + mySize;
      result = 29 * result + myStyle;
      return result;
    }
  }

  @NonNls private static final String BOLD_SUFFIX = ".bold";

  @NonNls private static final String ITALIC_SUFFIX = ".italic";

  static {
    ourFontNames = new ArrayList<String>();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ourFontNames.add("Monospaced");
    } else {
      if (SystemInfo.isMac) {
        fillStyledFontMap();
      }
      String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
      for (final String fontName : fontNames) {
        if (!fontName.endsWith(BOLD_SUFFIX) && !fontName.endsWith(ITALIC_SUFFIX)) {
          ourFontNames.add(fontName);
        }
      }
    }
    ourUsedFonts = new LinkedHashMap<FontKey, FontInfo>();
  }

  private static void fillStyledFontMap() {
    Font[] allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
    for (Font font : allFonts) {
      String name = font.getName();
      int style = getFontStyle(name);
      if (style != Font.PLAIN) {
        String familyName = font.getFamily();
        Pair<String, Integer>[] entry = ourStyledFontMap.get(familyName);
        if (entry == null) {
          //noinspection unchecked
          entry = new Pair[4];
          for (int i = 1; i < 4; i++) {
            entry[i] = Pair.create(familyName, i);
          }
          ourStyledFontMap.put(familyName, entry);
        }
        entry[style] = Pair.create(name, Font.PLAIN);
      }
    }
  }

  @JdkConstants.FontStyle
  private static int getFontStyle(String fontName) {
    fontName = fontName.toLowerCase(Locale.getDefault());
    for (String name : BOLD_ITALIC_NAMES) {
      if (fontName.contains(name)) return Font.BOLD | Font.ITALIC;
    }
    for (String name : ITALIC_NAMES) {
      if (fontName.contains(name)) return Font.ITALIC;
    }
    for (String name : BOLD_NAMES) {
      if (fontName.contains(name)) return Font.BOLD;
    }
    return Font.PLAIN;
  }

  @NotNull
  public static FontInfo getFontAbleToDisplay(char c, @JdkConstants.FontStyle int style, @NotNull FontPreferences preferences) {
    boolean tryDefaultFont = true;
    List<String> fontFamilies = preferences.getEffectiveFontFamilies();
    FontInfo result;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, len = fontFamilies.size(); i < len; ++i) { // avoid foreach, it instantiates ArrayList$Itr, this traversal happens very often
      final String fontFamily = fontFamilies.get(i);
      result = doGetFontAbleToDisplay(c, preferences.getSize(fontFamily), style, fontFamily);
      if (result != null) {
        return result;
      }
      tryDefaultFont &= !FontPreferences.DEFAULT_FONT_NAME.equals(fontFamily);
    }
    int size = FontPreferences.DEFAULT_FONT_SIZE;
    if (!fontFamilies.isEmpty()) {
      size = preferences.getSize(fontFamilies.get(0));
    }
    if (tryDefaultFont) {
      result = doGetFontAbleToDisplay(c, size, style, FontPreferences.DEFAULT_FONT_NAME);
      if (result != null) {
        return result;
      }
    }
    return doGetFontAbleToDisplay(c, size, style);
  }
  
  @NotNull
  public static FontInfo getFontAbleToDisplay(char c, int size, @JdkConstants.FontStyle int style, @NotNull String defaultFontFamily) {
    FontInfo result = doGetFontAbleToDisplay(c, size, style, defaultFontFamily);
    if (result != null) {
      return result;
    }
    return doGetFontAbleToDisplay(c, size, style);
  }

  @Nullable
  private static FontInfo doGetFontAbleToDisplay(char c, int size, @JdkConstants.FontStyle int style, @NotNull String defaultFontFamily) {
    synchronized (lock) {
      if (SystemInfo.isMac && style > 0 && style < 4) {
        Pair<String, Integer>[] replacement = ourStyledFontMap.get(defaultFontFamily);
        if (replacement != null) {
          defaultFontFamily = replacement[style].first;
          style = replacement[style].second;
        }
      }
      if (ourSharedKeyInstance.mySize == size &&
          ourSharedKeyInstance.myStyle == style &&
          ourSharedKeyInstance.myFamilyName != null &&
          ourSharedKeyInstance.myFamilyName.equals(defaultFontFamily) &&
          ourSharedDefaultFont != null &&
          ( c < 128 ||
            ourSharedDefaultFont.canDisplay(c)
          )
        ) {
        return ourSharedDefaultFont;
      }

      ourSharedKeyInstance.myFamilyName = defaultFontFamily;
      ourSharedKeyInstance.mySize = size;
      ourSharedKeyInstance.myStyle = style;

      FontInfo defaultFont = ourUsedFonts.get(ourSharedKeyInstance);
      if (defaultFont == null) {
        defaultFont = new FontInfo(defaultFontFamily, size, style);
        ourUsedFonts.put(ourSharedKeyInstance, defaultFont);
        ourSharedKeyInstance = new FontKey("", 0, 0);
      }

      ourSharedDefaultFont = defaultFont;
      if (c < 128 || defaultFont.canDisplay(c)) {
        return defaultFont;
      }
      else {
        return null;
      }
    }
  }
  
  @NotNull
  private static FontInfo doGetFontAbleToDisplay(char c, int size, @JdkConstants.FontStyle int style) {
    synchronized (lock) {
      if (ourUndisplayableChars.contains(c)) return ourSharedDefaultFont;

      final Collection<FontInfo> descriptors = ourUsedFonts.values();
      for (FontInfo font : descriptors) {
        if (font.getSize() == size && font.getStyle() == style && font.canDisplay(c)) {
          return font;
        }
      }

      for (int i = 0; i < ourFontNames.size(); i++) {
        String name = ourFontNames.get(i);
        FontInfo font = new FontInfo(name, size, style);
        if (font.canDisplay(c)) {
          ourUsedFonts.put(new FontKey(name, size, style), font);
          ourFontNames.remove(i);
          return font;
        }
      }

      ourUndisplayableChars.add(c);

      return ourSharedDefaultFont;
    }
  }
}
