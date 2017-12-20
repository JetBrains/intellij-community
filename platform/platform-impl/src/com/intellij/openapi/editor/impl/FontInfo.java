/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.Patches;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntHashSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.font.FontDesignMetrics;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.io.File;
import java.io.FilenameFilter;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class FontInfo {
  private static final Logger LOG = Logger.getInstance(FontInfo.class);
  
  private static final boolean USE_ALTERNATIVE_CAN_DISPLAY_PROCEDURE = Registry.is("ide.mac.fix.font.fallback");
  private static final FontRenderContext DEFAULT_CONTEXT = new FontRenderContext(null, false, false);
  private static final Font DUMMY_FONT = new Font(null);

  private final Font myFont;
  private final int mySize;
  @JdkConstants.FontStyle private final int myStyle;
  private final boolean myUseLigatures;
  private final TIntHashSet mySafeCharacters = new TIntHashSet();
  private final FontRenderContext myContext;
  private FontMetrics myFontMetrics = null;

  /**
   * @deprecated Use {@link #FontInfo(String, int, int, boolean, FontRenderContext)} instead.
   */
  public FontInfo(final String familyName, final int size, @JdkConstants.FontStyle int style) {
    this(familyName, size, style, style, false, null);
  }

  /**
   * @deprecated Use {@link #FontInfo(String, int, int, boolean, FontRenderContext)} instead.
   */
  public FontInfo(final String familyName, final int size, @JdkConstants.FontStyle int style, boolean useLigatures) {
    this(familyName, size, style, useLigatures, null);
  }

  /**
   * To get valid font metrics from this {@link FontInfo} instance, pass valid {@link FontRenderContext} here as a parameter.
   */
  public FontInfo(final String familyName, final int size, @JdkConstants.FontStyle int style, boolean useLigatures,
                  FontRenderContext fontRenderContext) {
    this(familyName, size, style, style, useLigatures, fontRenderContext);
  }

  FontInfo(final String familyName, final int size,
           @JdkConstants.FontStyle int style, @JdkConstants.FontStyle int realStyle, boolean useLigatures, FontRenderContext context) {
    mySize = size;
    myStyle = style;
    myUseLigatures = useLigatures;
    Font font = new Font(familyName, style, size);
    myFont = useLigatures ? getFontWithLigaturesEnabled(font, realStyle) : font;
    myContext = context;
  }

  @NotNull
  private static Font getFontWithLigaturesEnabled(Font font, @JdkConstants.FontStyle int fontStyle) {
    if (Patches.JDK_BUG_ID_7162125) {
      // Ligatures don't work on Mac for fonts loaded natively, so we need to locate and load font manually
      String familyName = font.getFamily();
      File fontFile = findFileForFont(familyName, fontStyle);
      if (fontFile == null) {
        LOG.info(font + "(style=" + fontStyle + ") not located");
        return font;
      }
      LOG.info(font + "(style=" + fontStyle + ") located at " + fontFile);
      try {
        font = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(fontStyle, font.getSize());
      }
      catch (Exception e) {
        LOG.warn("Couldn't load font", e);
        return font;
      }
    }
    return font.deriveFont(Collections.singletonMap(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON));
  }

  private static final Comparator<File> BY_NAME = Comparator.comparing(File::getName);

  @Nullable
  private static File findFileForFont(@NotNull String familyName, int style) {
    File fontFile = doFindFileForFont(familyName, style);
    if (fontFile == null && style != Font.PLAIN) fontFile = doFindFileForFont(familyName, Font.PLAIN);
    if (fontFile == null) fontFile = doFindFileForFont(familyName, -1);
    return fontFile;
  }

  @Nullable
  private static File doFindFileForFont(@NotNull String familyName, final int style) {
    final String normalizedFamilyName = familyName.toLowerCase(Locale.getDefault()).replace(" ", "");
    FilenameFilter filter = (file, name) -> {
      String normalizedName = name.toLowerCase(Locale.getDefault());
      return normalizedName.startsWith(normalizedFamilyName) &&
             (normalizedName.endsWith(".otf") || normalizedName.endsWith(".ttf")) &&
             (style == -1 || style == getFontStyle(normalizedName));
    };
    List<File> files = new ArrayList<>();
    
    File[] userFiles = new File(System.getProperty("user.home"), "Library/Fonts").listFiles(filter);
    if (userFiles != null) files.addAll(Arrays.asList(userFiles));
    
    File[] localFiles = new File("/Library/Fonts").listFiles(filter);
    if (localFiles != null) files.addAll(Arrays.asList(localFiles));
    
    if (files.isEmpty()) return null;
    
    if (style == Font.PLAIN) {
      // prefer font containing 'regular' in its name
      List<File> regulars = ContainerUtil.filter(files, file -> file.getName().toLowerCase(Locale.getDefault()).contains("regular"));
      if (!regulars.isEmpty()) return Collections.min(regulars, BY_NAME);
    }
    
    return Collections.min(files, BY_NAME);
  }

  private static int getFontStyle(@NotNull String fontFileNameLowercase) {
    String baseName = fontFileNameLowercase.substring(0, fontFileNameLowercase.length() - 4);
    if (baseName.endsWith("-it")) return Font.ITALIC;
    else if (baseName.endsWith("-boldit")) return Font.BOLD | Font.ITALIC;
    else return ComplementaryFontsRegistry.getFontStyle(fontFileNameLowercase);
  }

  public boolean canDisplay(int codePoint) {
    try {
      if (codePoint < 128) return true;
      if (mySafeCharacters.contains(codePoint)) return true;
      if (canDisplayImpl(codePoint)) {
        mySafeCharacters.add(codePoint);
        return true;
      }
      return false;
    }
    catch (Exception e) {
      // JRE has problems working with the font. Just skip.
      return false;
    }
  }

  private boolean canDisplayImpl(int codePoint) {
    if (!Character.isValidCodePoint(codePoint)) return false;
    if (USE_ALTERNATIVE_CAN_DISPLAY_PROCEDURE) {
      return myFont.createGlyphVector(DEFAULT_CONTEXT, new String(new int[]{codePoint}, 0, 1)).getGlyphCode(0) > 0;
    }
    else {
      return myFont.canDisplay(codePoint);
    }
  }

  public Font getFont() {
    return myFont;
  }

  public int charWidth(int codePoint) {
    final FontMetrics metrics = fontMetrics();
    return FontLayoutService.getInstance().charWidth(metrics, codePoint);
  }

  public float charWidth2D(int codePoint) {
    FontMetrics metrics = fontMetrics();
    return FontLayoutService.getInstance().charWidth2D(metrics, codePoint);
  }

  public synchronized FontMetrics fontMetrics() {
    if (myFontMetrics == null) {
      myFontMetrics = getFontMetrics(myFont, myContext == null ? getFontRenderContext(null) : myContext);
    }
    return myFontMetrics;
  }

  @NotNull
  public static FontMetrics getFontMetrics(@NotNull Font font, @NotNull FontRenderContext fontRenderContext) {
    return FontDesignMetrics.getMetrics(font, fontRenderContext);
  }

  public static FontRenderContext getFontRenderContext(Component component) {
    if (component == null) {
        return DEFAULT_CONTEXT;
    }
    return component.getFontMetrics(DUMMY_FONT).getFontRenderContext();
  }

  public int getSize() {
    return mySize;
  }

  @JdkConstants.FontStyle
  public int getStyle() {
    return myStyle;
  }

  public boolean areLigaturesEnabled() {
    return myUseLigatures;
  }

  public FontRenderContext getFontRenderContext() {
    return myContext;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FontInfo fontInfo = (FontInfo)o;

    if (!myFont.equals(fontInfo.myFont)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFont.hashCode();
  }
}
