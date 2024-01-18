// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.Patches;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions;
import com.intellij.openapi.util.Pair;
import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.util.List;
import java.util.*;

public final class ComplementaryFontsRegistry {
  private static final Logger LOG = Logger.getInstance(ComplementaryFontsRegistry.class);
  private static final boolean PATCH_FONT_NAMES = Patches.JDK_MAC_FONT_STYLE_DETECTION_WORKAROUND &&
                                                  !AppEditorFontOptions.NEW_FONT_SELECTOR;
  private static final String DEFAULT_FALLBACK_FONT = Font.MONOSPACED;
  private static final Object lock = new Object();
  @SuppressWarnings("unchecked")
  private static final List<String>[] ourFontNames = new List[4]; // per font style
  private static final Map<String, Pair<String, Integer>[]> ourStyledFontMap = new HashMap<>();
  @SuppressWarnings("unchecked")
  private static final LinkedHashMap<String, FallBackInfo>[] ourUsedFonts = new LinkedHashMap[] { // per font style
    new LinkedHashMap<String, FallBackInfo>(), new LinkedHashMap<String, FallBackInfo>(),
    new LinkedHashMap<String, FallBackInfo>(), new LinkedHashMap<String, FallBackInfo>()
  };
  private static final Map<Font, FallBackInfo> ourMainUsedFonts = new HashMap<>();
  // This is the font that will be used to show placeholder glyphs for characters no installed font can display.
  // Glyph with code 0 will be used as a placeholder from this font.
  private static final FallBackInfo UNDISPLAYABLE_FONT_INFO = new FallBackInfo("JetBrains Mono", Font.PLAIN, Font.PLAIN);
  private static final IntSet[] ourUndisplayableChars = new IntOpenHashSet[] { // per font style
    new IntOpenHashSet(), new IntOpenHashSet(), new IntOpenHashSet(), new IntOpenHashSet()
  };
  private static String ourLastFontFamily = null;
  private static String ourLastRegularSubFamily;
  private static String ourLastBoldSubFamily;
  private static boolean ourLastTypographicNames;
  private static final FallBackInfo[] ourLastFallBackInfo = new FallBackInfo[4]; // per font style

  private static final @NonNls Map<String, Integer> FONT_NAME_TO_STYLE = new HashMap<>();
  static {
    FONT_NAME_TO_STYLE.put("AnkaCoder-b",           Font.BOLD);
    FONT_NAME_TO_STYLE.put("AnkaCoder-i",           Font.ITALIC);
    FONT_NAME_TO_STYLE.put("AnkaCoder-bi",          Font.BOLD | Font.ITALIC);
    FONT_NAME_TO_STYLE.put("SourceCodePro-It",      Font.ITALIC);
    FONT_NAME_TO_STYLE.put("SourceCodePro-BoldIt",  Font.BOLD | Font.ITALIC);
    FONT_NAME_TO_STYLE.put("Hasklig-It",            Font.ITALIC);
    FONT_NAME_TO_STYLE.put("Hasklig-BoldIt",        Font.BOLD | Font.ITALIC);
    FONT_NAME_TO_STYLE.put("FiraCode-Medium",       Font.BOLD);
  }

  private ComplementaryFontsRegistry() {
  }

  private static final @NonNls String BOLD_SUFFIX = ".bold";

  private static final @NonNls String ITALIC_SUFFIX = ".italic";

  // This font renders all characters as empty glyphs, so there's no reason to use it for fallback
  private static final String ADOBE_BLANK = "Adobe Blank";

  static {
    List<String> fontNames = new ArrayList<>();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      fontNames.add("Monospaced");
    } else {
      if (PATCH_FONT_NAMES) {
        fillStyledFontMap();
      }
      // This must match the corresponding call in com.intellij.idea.StartupUtil#updateFrameClassAndWindowIconAndPreloadSystemFonts for optimal performance
      String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
      for (final String fontName : families) {
        if (!fontName.endsWith(BOLD_SUFFIX) && !fontName.endsWith(ITALIC_SUFFIX) && !fontName.equals(ADOBE_BLANK)) {
          fontNames.add(fontName);
        }
      }
    }
    ourFontNames[0] = fontNames;
    for (int i = 1; i < 4; i++) {
      ourFontNames[i] = new ArrayList<>(fontNames);
    }
  }

  private static void fillStyledFontMap() {
    Font[] allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
    for (Font font : allFonts) {
      String name = font.getName();
      Integer style = FONT_NAME_TO_STYLE.get(name);
      if (style == null) {
        continue;
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

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  public static @NotNull FontInfo getFontAbleToDisplay(@NotNull CharSequence text, int start, int end,
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
  public static @NotNull FontInfo getFontAbleToDisplay(char @NotNull [] text, int start, int end,
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

  private static FontInfo getFontAbleToDisplay(int codePoint, char @NotNull [] remainingText, int start, int end,
                                               @JdkConstants.FontStyle int style, @NotNull FontPreferences preferences,
                                               FontRenderContext context) {
    boolean tryDefaultFallback = true;
    List<String> fontFamilies = preferences.getEffectiveFontFamilies();
    boolean useLigatures = preferences.useLigatures();
    FontInfo result;
    for (int i = 0, len = fontFamilies.size(); i < len; ++i) { // avoid foreach, it instantiates ArrayList$Itr, this traversal happens very often
      final String fontFamily = fontFamilies.get(i);
      result = doGetFontAbleToDisplay(codePoint, preferences.getSize2D(fontFamily), style, fontFamily,
                                      i == 0 ? preferences.getRegularSubFamily() : null, i == 0 ? preferences.getBoldSubFamily() : null,
                                      useLigatures, context, true, true);
      if (result != null && result.getFont().canDisplayUpTo(remainingText, start, end) == -1) {
        return result;
      }
      tryDefaultFallback &= !DEFAULT_FALLBACK_FONT.equals(fontFamily);
    }
    float size = FontPreferences.DEFAULT_FONT_SIZE;
    if (!fontFamilies.isEmpty()) {
      size = preferences.getSize2D(fontFamilies.get(0));
    }
    if (tryDefaultFallback) {
      result = doGetFontAbleToDisplay(codePoint, size, style, DEFAULT_FALLBACK_FONT, null, null, useLigatures, context, false, false);
      if (result != null && result.getFont().canDisplayUpTo(remainingText, start, end) == -1) {
        return result;
      }
    }
    result = doGetFontAbleToDisplay(codePoint, remainingText, start, end, size, style, useLigatures, context);
    if (LOG.isTraceEnabled()) {
      LOG.trace("Fallback font: " + result.getFont().getFontName());
    }
    return result;
  }

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  public static @NotNull FontInfo getFontAbleToDisplay(int codePoint, @JdkConstants.FontStyle int style, @NotNull FontPreferences preferences,
                                              FontRenderContext context) {
    boolean tryDefaultFallback = true;
    List<String> fontFamilies = preferences.getEffectiveFontFamilies();
    boolean useLigatures = preferences.useLigatures();
    FontInfo result;
    for (int i = 0, len = fontFamilies.size(); i < len; ++i) { // avoid foreach, it instantiates ArrayList$Itr, this traversal happens very often
      final String fontFamily = fontFamilies.get(i);
      result = doGetFontAbleToDisplay(codePoint, preferences.getSize2D(fontFamily), style, fontFamily,
                                      i == 0 ? preferences.getRegularSubFamily() : null, i == 0 ? preferences.getBoldSubFamily() : null,
                                      useLigatures, context, true, true);
      if (result != null) {
        return result;
      }
      tryDefaultFallback &= !DEFAULT_FALLBACK_FONT.equals(fontFamily);
    }
    float size = FontPreferences.DEFAULT_FONT_SIZE;
    if (!fontFamilies.isEmpty()) {
      size = preferences.getSize2D(fontFamilies.get(0));
    }
    if (tryDefaultFallback) {
      result = doGetFontAbleToDisplay(codePoint, size, style, DEFAULT_FALLBACK_FONT, null, null, useLigatures, context, false, false);
      if (result != null) {
        return result;
      }
    }
    result = doGetFontAbleToDisplay(codePoint, null, 0, 0, size, style, useLigatures, context);
    if (LOG.isTraceEnabled()) {
      LOG.trace("Fallback font: " + result.getFont().getFontName());
    }
    return result;
  }

  /**
   * If you intend to use font metrics from returned {@link FontInfo} object,
   * pass not-null correct {@link FontRenderContext} to this method.
   */
  public static @NotNull FontInfo getFontAbleToDisplay(int codePoint, int size, @JdkConstants.FontStyle int style, @NotNull String defaultFontFamily,
                                              FontRenderContext context) {
    FontInfo result = doGetFontAbleToDisplay(codePoint, size, style, defaultFontFamily, null, null, false, context, false, false);
    if (result != null) {
      return result;
    }
    if (!DEFAULT_FALLBACK_FONT.equals(defaultFontFamily)) {
      result = doGetFontAbleToDisplay(codePoint, size, style, DEFAULT_FALLBACK_FONT, null, null, false, context, false, false);
      if (result != null) {
        return result;
      }
    }
    return doGetFontAbleToDisplay(codePoint, null, 0, 0, size, style, false, context);
  }

  private static @Nullable FontInfo doGetFontAbleToDisplay(int codePoint, float size, @JdkConstants.FontStyle int originalStyle,
                                                           @NotNull String defaultFontFamily, String regularSubFamily, String boldSubFamily,
                                                           boolean useLigatures, FontRenderContext context, boolean disableFontFallback,
                                                           boolean useTypographicNames) {
    if (originalStyle < 0 || originalStyle > 3) originalStyle = Font.PLAIN;
    synchronized (lock) {
      FallBackInfo fallBackInfo = null;
      boolean typographicNames = AppEditorFontOptions.NEW_FONT_SELECTOR && useTypographicNames;
      @JdkConstants.FontStyle int style = originalStyle;
      if (PATCH_FONT_NAMES && style != Font.PLAIN) {
        Pair<String, Integer>[] replacement = ourStyledFontMap.get(defaultFontFamily);
        if (replacement != null) {
          defaultFontFamily = replacement[style].first;
          style = replacement[style].second;
        }
      }
      if (typographicNames == ourLastTypographicNames &&
          defaultFontFamily.equals(ourLastFontFamily) &&
          (!typographicNames ||
           Objects.equals(regularSubFamily, ourLastRegularSubFamily) && Objects.equals(boldSubFamily, ourLastBoldSubFamily))) {
        fallBackInfo = ourLastFallBackInfo[style];
      }
      else {
        ourLastTypographicNames = typographicNames;
        ourLastFontFamily = defaultFontFamily;
        ourLastRegularSubFamily = regularSubFamily;
        ourLastBoldSubFamily = boldSubFamily;
        Arrays.fill(ourLastFallBackInfo, null);
      }
      if (fallBackInfo == null) {
        if (useTypographicNames) {
          Font font = FontFamilyService.getFont(defaultFontFamily, regularSubFamily, boldSubFamily, style);
          fallBackInfo = ourMainUsedFonts.computeIfAbsent(font, FallBackInfo::new);
        }
        else {
          LinkedHashMap<String, FallBackInfo> usedFonts = ourUsedFonts[style];
          fallBackInfo = usedFonts.get(defaultFontFamily);
          if (fallBackInfo == null) {
            fallBackInfo = new FallBackInfo(defaultFontFamily, style, originalStyle);
            usedFonts.put(defaultFontFamily, fallBackInfo);
          }
        }
        ourLastFallBackInfo[style] = fallBackInfo;
      }
      return fallBackInfo.canDisplay(codePoint, disableFontFallback) ? fallBackInfo.getFontInfo(size, useLigatures, context) : null;
    }
  }

  private static @NotNull FontInfo doGetFontAbleToDisplay(int codePoint, char[] remainingText, int start, int end,
                                                          float size, @JdkConstants.FontStyle int style, boolean useLigatures,
                                                          FontRenderContext context) {
    if (style < 0 || style > 3) style = Font.PLAIN;
    synchronized (lock) {
      FallBackInfo fallBackInfo = UNDISPLAYABLE_FONT_INFO;
      IntSet undisplayableChars = ourUndisplayableChars[style];
      if (!undisplayableChars.contains(codePoint)) {
        boolean canDisplayFirst = false;
        LinkedHashMap<String, FallBackInfo> usedFonts = ourUsedFonts[style];
        final Collection<FallBackInfo> descriptors = usedFonts.values();
        for (FallBackInfo info : descriptors) {
          if (info.myOriginalStyle == style && info.canDisplay(codePoint, false)) {
            canDisplayFirst = true;
            if (remainingText == null || info.myBaseFont.canDisplayUpTo(remainingText, start, end) == -1) {
              fallBackInfo = info;
              break;
            }
          }
        }
        if (fallBackInfo == UNDISPLAYABLE_FONT_INFO) {
          List<String> fontNames = ourFontNames[style];
          for (int i = 0; i < fontNames.size(); i++) {
            String name = fontNames.get(i);
            FallBackInfo info = new FallBackInfo(name, style, style);
            if (info.canDisplay(codePoint, false)) {
              canDisplayFirst = true;
              if (remainingText == null || info.myBaseFont.canDisplayUpTo(remainingText, start, end) == -1) {
                usedFonts.put(name, info);
                fontNames.remove(i);
                fallBackInfo = info;
                break;
              }
            }
          }
          if (fallBackInfo == UNDISPLAYABLE_FONT_INFO && !canDisplayFirst) {
            undisplayableChars.add(codePoint);
          }
        }
      }
      return fallBackInfo.getFontInfo(size, useLigatures, context);
    }
  }

  private static final class FontKey implements Cloneable {
    private float mySize;
    private boolean myUseLigatures;
    private FontRenderContext myContext;

    private FontKey(float size, boolean useLigatures, FontRenderContext context) {
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
      if (!Objects.equals(myContext, key.myContext)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = (mySize != 0.0f ? Float.floatToIntBits(mySize) : 0);
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

  private static final class FallBackInfo {
    private final Font myBaseFont;
    private final int myOriginalStyle;
    private final Map<FontKey, FontInfo> myFontInfoMap = new HashMap<>();
    private final FontKey myLastFontKey = new FontKey(-1, false, FontInfo.DEFAULT_CONTEXT);
    private FontInfo myLastFontInfo;

    private FallBackInfo(Font font) {
      myBaseFont = font;
      myOriginalStyle = -1;
    }

    private FallBackInfo(String familyName, @JdkConstants.FontStyle int style, int originalStyle) {
      myBaseFont = new Font(familyName, style, 1);
      myOriginalStyle = originalStyle;
    }

    private boolean canDisplay(int codePoint, boolean disableFontFallback) {
      return codePoint < 128 || FontInfo.canDisplay(myBaseFont, codePoint, disableFontFallback);
    }

    private FontInfo getFontInfo(float size, boolean useLigatures, FontRenderContext fontRenderContext) {
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
        fontInfo = AppEditorFontOptions.NEW_FONT_SELECTOR
                   ? new FontInfo(myBaseFont, size, useLigatures, fontRenderContext)
                   : new FontInfo(myBaseFont.getName(), size, myBaseFont.getStyle(), useLigatures, fontRenderContext);
        myFontInfoMap.put(myLastFontKey.clone(), fontInfo);
      }
      myLastFontInfo = fontInfo;
      return fontInfo;
    }
  }
}
