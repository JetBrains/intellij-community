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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntHashSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
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
  private static final FontRenderContext DUMMY_CONTEXT = new FontRenderContext(null, false, false);

  private final Font myFont;
  private final int mySize;
  @JdkConstants.FontStyle private final int myStyle;
  private final boolean myUseLigatures;
  private final TIntHashSet mySafeCharacters = new TIntHashSet();
  private FontMetrics myFontMetrics = null;

  public FontInfo(final String familyName, final int size, @JdkConstants.FontStyle int style) {
    this(familyName, size, style, false);    
  }
  
  public FontInfo(final String familyName, final int size, @JdkConstants.FontStyle int style, boolean useLigatures) {
    this(familyName, size, style, style, useLigatures);    
  }
  
  FontInfo(final String familyName, final int size, 
           @JdkConstants.FontStyle int style, @JdkConstants.FontStyle int realStyle, boolean useLigatures) {
    mySize = size;
    myStyle = style;
    myUseLigatures = useLigatures;
    Font font = new Font(familyName, style, size);
    myFont = useLigatures ? getFontWithLigaturesEnabled(font, realStyle) : font;
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
      return myFont.createGlyphVector(DUMMY_CONTEXT, new String(new int[]{codePoint}, 0, 1)).getGlyphCode(0) > 0;
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

  public FontMetrics fontMetrics() {
    if (myFontMetrics == null) {
      // We need to use antialising-aware font metrics because we've alrady encountered a situation when non-antialiased symbol
      // width is not equal to the antialiased one (IDEA-81539).
      final Graphics graphics = createReferenceGraphics();
      graphics.setFont(myFont);
      myFontMetrics = graphics.getFontMetrics();
    }
    return myFontMetrics;
  }

  public static Graphics2D createReferenceGraphics() {
    Graphics2D graphics = (Graphics2D)UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_RGB).getGraphics();
    EditorUIUtil.setupAntialiasing(graphics);
    return graphics;
  }
  
  void reset() {
    myFontMetrics = null;
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
