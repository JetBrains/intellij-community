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
package com.intellij.openapi.editor.impl;

import com.intellij.Patches;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntHashSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.FontRenderContext;
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
  private static final LinkedHashMap<FontFaceKey, FallBackInfo> ourUsedFonts;
  public static final FallBackInfo DEFAULT_FONT_INFO = new FallBackInfo("", Font.PLAIN, Font.PLAIN);
  private static FontFaceKey ourSharedKeyInstance = new FontFaceKey("", Font.PLAIN);
  private static FallBackInfo ourSharedFallBackInfo = DEFAULT_FONT_INFO;
  private static final TIntHashSet ourUndisplayableChars = new TIntHashSet();

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
    FONT_NAME_TO_STYLE.put("FiraCode-Light",        Font.PLAIN);
    FONT_NAME_TO_STYLE.put("FiraCode-Medium",       Font.BOLD);
  }

  private ComplementaryFontsRegistry() {
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

  /**
   * @deprecated Use {{@link #getFontAbleToDisplay(int, int, FontPreferences, FontRenderContext)}} instead.
   */
  @NotNull
  public static FontInfo getFontAbleToDisplay(int codePoint, @JdkConstants.FontStyle int style, @NotNull FontPreferences preferences) {
    return getFontAbleToDisplay(codePoint, style, preferences, null);
  }

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  @NotNull
  public static FontInfo getFontAbleToDisplay(@NotNull CharSequence text, int start, int end, 
                                              @JdkConstants.FontStyle int style, @NotNull FontPreferences preferences,
                                              FontRenderContext context) {
    assert 0 <= start && start < end && end <= text.length() : "Start: " + start + ", end: " + end + ", length: " + text.length();
    if (end - start == 1) {
      // fast path for BMP code points
      return getFontAbleToDisplay(text.charAt(start), style, preferences, context);
    }
    int firstCodePoint = Character.codePointAt(text, start);
    int secondOffset = Character.offsetByCodePoints(text, start, 1);
    if (secondOffset == end) {
      // fast path for a single SMP code point
      return getFontAbleToDisplay(firstCodePoint, style, preferences, context);
    }
    char[] tmp = CharArrayUtil.fromSequence(text, secondOffset, end);
    return getFontAbleToDisplay(firstCodePoint, tmp, 0, tmp.length, style, preferences, context);
  }

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  @NotNull
  public static FontInfo getFontAbleToDisplay(@NotNull char[] text, int start, int end, 
                                              @JdkConstants.FontStyle int style, @NotNull FontPreferences preferences,
                                              FontRenderContext context) {
    assert 0 <= start && start < end && end <= text.length : "Start: " + start + ", end: " + end + ", length: " + text.length;
    if (end - start == 1) {
      // fast path for BMP code points
      return getFontAbleToDisplay(text[start], style, preferences, context);
    }
    int firstCodePoint = Character.codePointAt(text, start);
    int secondOffset = Character.offsetByCodePoints(text, start, end - start, start, 1);
    if (secondOffset == end) {
      // fast path for a single SMP code point
      return getFontAbleToDisplay(firstCodePoint, style, preferences, context);
    }
    return getFontAbleToDisplay(firstCodePoint, text, secondOffset, end, style, preferences, context);
  }
  
  private static FontInfo getFontAbleToDisplay(int codePoint, @NotNull char[] remainingText, int start, int end, 
                                              @JdkConstants.FontStyle int style, @NotNull FontPreferences preferences,
                                              FontRenderContext context) {
    boolean tryDefaultFont = true;
    List<String> fontFamilies = preferences.getEffectiveFontFamilies();
    boolean useLigatures = SystemInfo.isJetBrainsJvm && preferences.useLigatures();
    FontInfo result;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, len = fontFamilies.size(); i < len; ++i) { // avoid foreach, it instantiates ArrayList$Itr, this traversal happens very often
      final String fontFamily = fontFamilies.get(i);
      result = doGetFontAbleToDisplay(codePoint, preferences.getSize(fontFamily), style, fontFamily, useLigatures, context);
      if (result != null && result.getFont().canDisplayUpTo(remainingText, start, end) == -1) {
        return result;
      }
      tryDefaultFont &= !FontPreferences.DEFAULT_FONT_NAME.equals(fontFamily);
    }
    int size = FontPreferences.DEFAULT_FONT_SIZE;
    if (!fontFamilies.isEmpty()) {
      size = preferences.getSize(fontFamilies.get(0));
    }
    if (tryDefaultFont) {
      result = doGetFontAbleToDisplay(codePoint, size, style, FontPreferences.DEFAULT_FONT_NAME, useLigatures, context);
      if (result != null && result.getFont().canDisplayUpTo(remainingText, start, end) == -1) {
        return result;
      }
    }
    result = doGetFontAbleToDisplay(codePoint, remainingText, start, end, size, style, useLigatures, context);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Fallback font: " + result.getFont().getFontName());
    }
    return result;
  }

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  @NotNull
  public static FontInfo getFontAbleToDisplay(int codePoint, @JdkConstants.FontStyle int style, @NotNull FontPreferences preferences,
                                              FontRenderContext context) {
    boolean tryDefaultFont = true;
    List<String> fontFamilies = preferences.getEffectiveFontFamilies();
    boolean useLigatures = SystemInfo.isJetBrainsJvm && preferences.useLigatures();
    FontInfo result;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, len = fontFamilies.size(); i < len; ++i) { // avoid foreach, it instantiates ArrayList$Itr, this traversal happens very often
      final String fontFamily = fontFamilies.get(i);
      result = doGetFontAbleToDisplay(codePoint, preferences.getSize(fontFamily), style, fontFamily, useLigatures, context);
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
      result = doGetFontAbleToDisplay(codePoint, size, style, FontPreferences.DEFAULT_FONT_NAME, useLigatures, context);
      if (result != null) {
        return result;
      }
    }
    result = doGetFontAbleToDisplay(codePoint, null, 0, 0, size, style, useLigatures, context);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Fallback font: " + result.getFont().getFontName());
    }
    return result;
  }

  /**
   * @deprecated Use {{@link #getFontAbleToDisplay(int, int, int, String, FontRenderContext)}}
   */
  @NotNull
  public static FontInfo getFontAbleToDisplay(int codePoint, int size, @JdkConstants.FontStyle int style, @NotNull String defaultFontFamily) {
    return getFontAbleToDisplay(codePoint, size, style, defaultFontFamily, null);
  }

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  @NotNull
  public static FontInfo getFontAbleToDisplay(int codePoint, int size, @JdkConstants.FontStyle int style, @NotNull String defaultFontFamily,
                                              FontRenderContext context) {
    FontInfo result = doGetFontAbleToDisplay(codePoint, size, style, defaultFontFamily, false, context);
    if (result != null) {
      return result;
    }
    return doGetFontAbleToDisplay(codePoint, null, 0, 0, size, style, false, context);
  }

  @Nullable
  private static FontInfo doGetFontAbleToDisplay(int codePoint, int size, @JdkConstants.FontStyle int originalStyle, 
                                                 @NotNull String defaultFontFamily, boolean useLigatures, FontRenderContext context) {
    if (originalStyle < 0 || originalStyle > 3) originalStyle = Font.PLAIN;
    synchronized (lock) {
      @JdkConstants.FontStyle int style = originalStyle;
      if (Patches.JDK_MAC_FONT_STYLE_DETECTION_WORKAROUND && style != Font.PLAIN) {
        Pair<String, Integer>[] replacement = ourStyledFontMap.get(defaultFontFamily);
        if (replacement != null) {
          defaultFontFamily = replacement[style].first;
          style = replacement[style].second;
        }
      }
      FallBackInfo fallBackInfo;
      if (ourSharedKeyInstance.myStyle == style &&
          ourSharedKeyInstance.myFamilyName.equals(defaultFontFamily) &&
          ourSharedFallBackInfo.canDisplay(codePoint)) {
        fallBackInfo = ourSharedFallBackInfo;
      }
      else {
        ourSharedKeyInstance.myFamilyName = defaultFontFamily;
        ourSharedKeyInstance.myStyle = style;

        fallBackInfo = ourUsedFonts.get(ourSharedKeyInstance);
        if (fallBackInfo == null) {
          fallBackInfo = new FallBackInfo(defaultFontFamily, style, originalStyle);
          ourUsedFonts.put(ourSharedKeyInstance.clone(), fallBackInfo);
        }
      }
      ourSharedFallBackInfo = fallBackInfo;
      return fallBackInfo.canDisplay(codePoint) ? fallBackInfo.getFontInfo(size, useLigatures, context) : null;
    }
  }
  
  @NotNull
  private static FontInfo doGetFontAbleToDisplay(int codePoint, char[] remainingText, int start, int end, 
                                                 int size, @JdkConstants.FontStyle int style, boolean useLigatures,
                                                 FontRenderContext context) {
    if (style < 0 || style > 3) style = Font.PLAIN;
    synchronized (lock) {
      FallBackInfo fallBackInfo = DEFAULT_FONT_INFO;
      if (!ourUndisplayableChars.contains(codePoint)) {
        boolean canDisplayFirst = false;
        final Collection<FallBackInfo> descriptors = ourUsedFonts.values();
        for (FallBackInfo info : descriptors) {
          if (info.myOriginalStyle == style && info.canDisplay(codePoint)) {
            canDisplayFirst = true;
            if (remainingText == null || info.myBaseFont.canDisplayUpTo(remainingText, start, end) == -1) {
              fallBackInfo = info;
              break;
            }
          }
        }
        if (fallBackInfo == DEFAULT_FONT_INFO) {
          for (int i = 0; i < ourFontNames.size(); i++) {
            String name = ourFontNames.get(i);
            FallBackInfo info = new FallBackInfo(name, style, style);
            if (info.canDisplay(codePoint)) {
              canDisplayFirst = true;
              if (remainingText == null || info.myBaseFont.canDisplayUpTo(remainingText, start, end) == -1) {
                ourUsedFonts.put(new FontFaceKey(name, style), info);
                ourFontNames.remove(i);
                fallBackInfo = info;
                break;
              }
            }
          }
          if (fallBackInfo == DEFAULT_FONT_INFO && !canDisplayFirst) {
            ourUndisplayableChars.add(codePoint);
          }
        }
      }
      return fallBackInfo.getFontInfo(size, useLigatures, context);
    }
  }

  private static class FontFaceKey implements Cloneable {
    private String myFamilyName;
    private int myStyle;

    private FontFaceKey(String familyName, int style) {
      myFamilyName = familyName;
      myStyle = style;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FontFaceKey key = (FontFaceKey)o;

      if (myStyle != key.myStyle) return false;
      if (!myFamilyName.equals(key.myFamilyName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myFamilyName.hashCode();
      result = 31 * result + myStyle;
      return result;
    }

    @Override
    protected FontFaceKey clone() {
      try {
        return (FontFaceKey)super.clone();
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class FontKey implements Cloneable {
    private int mySize;
    private boolean myUseLigatures;
    private FontRenderContext myContext;

    private FontKey(int size, boolean useLigatures, FontRenderContext context) {
      mySize = size;
      myUseLigatures = useLigatures;
      myContext = context;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FontKey key = (FontKey)o;

      if (mySize != key.mySize) return false;
      if (myUseLigatures != key.myUseLigatures) return false;
      if (myContext != null ? !myContext.equals(key.myContext) : key.myContext != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = mySize;
      result = 31 * result + (myUseLigatures ? 1 : 0);
      result = 31 * result + (myContext != null ? myContext.hashCode() : 0);
      return result;
    }

    @Override
    protected FontKey clone() {
      try {
        return (FontKey)super.clone();
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class FallBackInfo {
    private final Font myBaseFont;
    private final int myOriginalStyle;
    private final Map<FontKey, FontInfo> myFontInfoMap = new HashMap<>();
    private final FontKey myLastFontKey = new FontKey(-1, false, new FontRenderContext(null, false, false));
    private FontInfo myLastFontInfo;

    private FallBackInfo(String familyName, @JdkConstants.FontStyle int style, int originalStyle) {
      myBaseFont = new Font(familyName, style, 1);
      myOriginalStyle = originalStyle;
    }

    private boolean canDisplay(int codePoint) {
      return codePoint < 128 || myBaseFont.canDisplay(codePoint);
    }

    private FontInfo getFontInfo(int size, boolean useLigatures, FontRenderContext fontRenderContext) {
      if (myLastFontKey.mySize == size &&
          myLastFontKey.myUseLigatures == useLigatures &&
          Objects.equals(myLastFontKey.myContext, fontRenderContext)) {
        return myLastFontInfo;
      }
      myLastFontKey.mySize = size;
      myLastFontKey.myUseLigatures = useLigatures;
      myLastFontKey.myContext = fontRenderContext;
      FontInfo fontInfo = myFontInfoMap.get(myLastFontKey);
      if (fontInfo == null) {
        fontInfo = new FontInfo(myBaseFont.getName(), size, myBaseFont.getStyle(), myOriginalStyle, useLigatures, fontRenderContext);
        myFontInfoMap.put(myLastFontKey.clone(), fontInfo);
      }
      myLastFontInfo = fontInfo;
      return fontInfo;
    }
  }
}
