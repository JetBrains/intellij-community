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
package com.intellij.openapi.editor.impl;

import com.intellij.Patches;
import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.util.Pair;
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
  private static final Logger LOG = Logger.getInstance(ComplementaryFontsRegistry.class);
  
  private static final Object lock = new String("common lock");
  private static final List<String> ourFontNames;
  private static final Map<String, Pair<String, Integer>[]> ourStyledFontMap = new HashMap<>();
  private static final LinkedHashMap<FontKey, FontInfo> ourUsedFonts;
  private static FontKey ourSharedKeyInstance = new FontKey("", 0, Font.PLAIN, false);
  private static FontInfo ourSharedDefaultFont;
  private static final TIntHashSet ourUndisplayableChars = new TIntHashSet();
  private static boolean ourOldUseAntialiasing;

  // This matches style detection in JDK (class sun.font.Font2D)
  private static final String[] BOLD_NAMES = {"bold", "demibold", "demi-bold", "demi bold", "negreta", "demi" };
  private static final String[] ITALIC_NAMES = {"italic", "cursiva", "oblique", "inclined"};
  private static final String[] BOLD_ITALIC_NAMES = {"bolditalic", "bold-italic", "bold italic", "boldoblique", "bold-oblique", 
    "bold oblique", "demibold italic", "negreta cursiva","demi oblique"};

  // Explicit mapping fontName->style for cases where generic rules (given above) don't work.
  private static final Map<String, Integer> FONT_NAME_TO_STYLE = new HashMap<>();
  static {
    FONT_NAME_TO_STYLE.put("AnkaCoder-b",           Font.BOLD);
    FONT_NAME_TO_STYLE.put("AnkaCoder-i",           Font.ITALIC);
    FONT_NAME_TO_STYLE.put("AnkaCoder-bi",          Font.BOLD | Font.ITALIC);
    FONT_NAME_TO_STYLE.put("SourceCodePro-It",      Font.ITALIC);
    FONT_NAME_TO_STYLE.put("SourceCodePro-BoldIt",  Font.BOLD | Font.ITALIC);
    FONT_NAME_TO_STYLE.put("Hasklig-It",            Font.ITALIC);
    FONT_NAME_TO_STYLE.put("Hasklig-BoldIt",        Font.BOLD | Font.ITALIC);
  }

  static {
    final UISettings settings = UISettings.getInstance();
    ourOldUseAntialiasing = !AntialiasingType.OFF.equals(settings.EDITOR_AA_TYPE);

    // Reset font info on 'use antialiasing' setting change.
    // Assuming that the listener is notified from the EDT only.
    settings.addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        if (ourOldUseAntialiasing ^ !AntialiasingType.OFF.equals(settings.EDITOR_AA_TYPE)) {
          ourOldUseAntialiasing = !AntialiasingType.OFF.equals(settings.EDITOR_AA_TYPE);
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
    private String myFamilyName;
    private int mySize;
    private int myStyle;
    private boolean myUseLigatures;

    public FontKey(@NotNull String familyName, final int size, @JdkConstants.FontStyle int style, boolean useLigatures) {
      myFamilyName = familyName;
      mySize = size;
      myStyle = style;
      myUseLigatures = useLigatures;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      final FontKey fontKey = (FontKey)o;

      if (mySize != fontKey.mySize) return false;
      if (myStyle != fontKey.myStyle) return false;
      if (myUseLigatures != fontKey.myUseLigatures) return false;
      return myFamilyName.equals(fontKey.myFamilyName);
    }

    public int hashCode() {
      int result = myFamilyName.hashCode();
      result = 29 * result + mySize;
      result = 29 * result + myStyle;
      if (myUseLigatures) {
        result = 29 * result + 1;
      }
      return result;
    }
  }

  @NonNls private static final String BOLD_SUFFIX = ".bold";

  @NonNls private static final String ITALIC_SUFFIX = ".italic";

  static {
    ourFontNames = new ArrayList<>();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ourFontNames.add("Monospaced");
    } else {
      if (Patches.JDK_MAC_FONT_STYLE_DETECTION_WORKAROUND) {
        fillStyledFontMap();
      }
      String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
      for (final String fontName : fontNames) {
        if (!fontName.endsWith(BOLD_SUFFIX) && !fontName.endsWith(ITALIC_SUFFIX)) {
          ourFontNames.add(fontName);
        }
      }
    }
    ourUsedFonts = new LinkedHashMap<>();
  }

  private static void fillStyledFontMap() {
    Font[] allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
    for (Font font : allFonts) {
      String name = font.getName();
      Integer style = FONT_NAME_TO_STYLE.get(name);
      if (style == null) {
        if (!Patches.JDK_MAC_FONT_STYLE_BUG) continue;
        style = getFontStyle(name);
      }
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
  static int getFontStyle(String fontName) {
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
  public static FontInfo getFontAbleToDisplay(int codePoint, @JdkConstants.FontStyle int style, @NotNull FontPreferences preferences) {
    boolean tryDefaultFont = true;
    List<String> fontFamilies = preferences.getEffectiveFontFamilies();
    boolean useLigatures = preferences.useLigatures();
    FontInfo result;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, len = fontFamilies.size(); i < len; ++i) { // avoid foreach, it instantiates ArrayList$Itr, this traversal happens very often
      final String fontFamily = fontFamilies.get(i);
      result = doGetFontAbleToDisplay(codePoint, preferences.getSize(fontFamily), style, fontFamily, useLigatures);
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
      result = doGetFontAbleToDisplay(codePoint, size, style, FontPreferences.DEFAULT_FONT_NAME, useLigatures);
      if (result != null) {
        return result;
      }
    }
    result = doGetFontAbleToDisplay(codePoint, size, style, useLigatures);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Fallback font: " + result.getFont().getFontName());
    }
    return result;
  }
  
  @NotNull
  public static FontInfo getFontAbleToDisplay(int codePoint, int size, @JdkConstants.FontStyle int style, @NotNull String defaultFontFamily) {
    FontInfo result = doGetFontAbleToDisplay(codePoint, size, style, defaultFontFamily, false);
    if (result != null) {
      return result;
    }
    return doGetFontAbleToDisplay(codePoint, size, style, false);
  }

  @Nullable
  private static FontInfo doGetFontAbleToDisplay(int codePoint, int size, @JdkConstants.FontStyle int originalStyle, 
                                                 @NotNull String defaultFontFamily, boolean useLigatures) {
    synchronized (lock) {
      @JdkConstants.FontStyle int style = originalStyle;
      if (Patches.JDK_MAC_FONT_STYLE_DETECTION_WORKAROUND && style > 0 && style < 4) {
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
          ourSharedKeyInstance.myUseLigatures == useLigatures &&
          ourSharedDefaultFont != null &&
          ( codePoint < 128 ||
            ourSharedDefaultFont.canDisplay(codePoint)
          )
        ) {
        return ourSharedDefaultFont;
      }

      ourSharedKeyInstance.myFamilyName = defaultFontFamily;
      ourSharedKeyInstance.mySize = size;
      ourSharedKeyInstance.myStyle = style;
      ourSharedKeyInstance.myUseLigatures = useLigatures;

      FontInfo defaultFont = ourUsedFonts.get(ourSharedKeyInstance);
      if (defaultFont == null) {
        defaultFont = new FontInfo(defaultFontFamily, size, style, originalStyle, useLigatures);
        ourUsedFonts.put(ourSharedKeyInstance, defaultFont);
        ourSharedKeyInstance = new FontKey("", 0, Font.PLAIN, false);
      }

      ourSharedDefaultFont = defaultFont;
      if (codePoint < 128 || defaultFont.canDisplay(codePoint)) {
        return defaultFont;
      }
      else {
        return null;
      }
    }
  }
  
  @NotNull
  private static FontInfo doGetFontAbleToDisplay(int codePoint, int size, @JdkConstants.FontStyle int style, boolean useLigatures) {
    synchronized (lock) {
      if (ourUndisplayableChars.contains(codePoint)) return ourSharedDefaultFont;

      final Collection<FontInfo> descriptors = ourUsedFonts.values();
      for (FontInfo font : descriptors) {
        if (font.getSize() == size && 
            font.getStyle() == style && 
            font.areLigaturesEnabled() == useLigatures && 
            font.canDisplay(codePoint)) {
          return font;
        }
      }

      for (int i = 0; i < ourFontNames.size(); i++) {
        String name = ourFontNames.get(i);
        FontInfo font = new FontInfo(name, size, style, style, useLigatures);
        if (font.canDisplay(codePoint)) {
          ourUsedFonts.put(new FontKey(name, size, style, useLigatures), font);
          ourFontNames.remove(i);
          return font;
        }
      }

      ourUndisplayableChars.add(codePoint);

      return ourSharedDefaultFont;
    }
  }
}
