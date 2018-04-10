// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.util.Locale.ENGLISH;

/**
 * @author Sergey.Malenkov
 */
public final class FontInfo {
  private static final FontInfoComparator COMPARATOR = new FontInfoComparator();
  private static final FontRenderContext DEFAULT_CONTEXT = new FontRenderContext(null, false, false);
  private static final String[] WRONG_SUFFIX = {".plain", ".bold", ".italic", ".bolditalic"};
  private static final String[] DEFAULT = {Font.DIALOG, Font.DIALOG_INPUT, Font.MONOSPACED, Font.SANS_SERIF, Font.SERIF};
  private static final int DEFAULT_SIZE = 12;

  private final String myName;
  private final int myDefaultSize;
  private final boolean myMonospaced;
  private volatile Font myFont;

  private FontInfo(String name, Font font, boolean monospaced) {
    myName = name;
    myFont = font;
    myDefaultSize = font.getSize();
    myMonospaced = monospaced;
  }

  /**
   * @return {@code true} if font is monospaced, {@code false} otherwise
   */
  public boolean isMonospaced() {
    return myMonospaced;
  }

  /**
   * @return a font with the default size
   */
  public Font getFont() {
    return getFont(myDefaultSize);
  }

  /**
   * @param size required font size
   * @return a font with the specified size
   */
  public Font getFont(int size) {
    Font font = myFont;
    if (size != font.getSize()) {
      font = font.deriveFont((float)size);
      myFont = font;
    }
    return font;
  }

  /**
   * @return a font name
   */
  @Override
  public String toString() {
    return myName != null ? myName : myFont.getFontName(ENGLISH);
  }

  /**
   * @param name the font name to validate
   * @return an information about the specified font name,
   * or {@code null} a font cannot render english letters
   * @see GraphicsEnvironment#isHeadless
   */
  public static FontInfo get(String name) {
    return name == null || GraphicsEnvironment.isHeadless() ? null : find(LazyListByName.LIST, name);
  }

  /**
   * @param font the font to validate
   * @return an information about the specified font name,
   * or {@code null} a font cannot render english letters
   * @see GraphicsEnvironment#isHeadless
   */
  public static FontInfo get(Font font) {
    return font == null || GraphicsEnvironment.isHeadless() ? null : find(LazyListByFont.LIST, font.getFontName(ENGLISH));
  }

  /**
   * @param withAllStyles {@code true} - all fonts, {@code false} - all plain fonts
   * @return a shared list of fonts according to the specified parameter
   * @see GraphicsEnvironment#isHeadless
   */
  public static List<FontInfo> getAll(boolean withAllStyles) {
    return GraphicsEnvironment.isHeadless()
           ? Collections.<FontInfo>emptyList()
           : withAllStyles
             ? LazyListByFont.LIST
             : LazyListByName.LIST;
  }

  private static FontInfo find(List<FontInfo> list, String name) {
    for (FontInfo info : list) {
      if (info.toString().equalsIgnoreCase(name)) {
        return info;
      }
    }
    return null;
  }

  private static FontInfo byName(String name) {
    return isWrongSuffix(name) ? null : create(name, null);
  }

  private static List<FontInfo> byName() {
    String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(ENGLISH);
    List<FontInfo> list = new ArrayList<FontInfo>(names.length);
    for (String name : names) {
      FontInfo info = byName(name);
      if (info != null) list.add(info);
    }
    for (String name : DEFAULT) {
      if (find(list, name) == null) {
        FontInfo info = byName(name);
        if (info != null) list.add(info);
      }
    }
    Collections.sort(list, COMPARATOR);
    return Collections.unmodifiableList(list);
  }

  private static FontInfo byFont(Font font) {
    return isWrongSuffix(font.getFontName(ENGLISH)) ? null : create(null, font);
  }

  private static List<FontInfo> byFont() {
    Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
    List<FontInfo> list = new ArrayList<FontInfo>(fonts.length);
    for (Font font : fonts) {
      FontInfo info = byFont(font);
      if (info != null) list.add(info);
    }
    for (String name : DEFAULT) {
      FontInfo info = find(list, name);
      if (info != null) list.remove(info);
    }
    Collections.sort(list, COMPARATOR);
    return Collections.unmodifiableList(list);
  }

  private static FontInfo create(String name, Font font) {
    boolean plainOnly = name == null;
    try {
      if (font == null) {
        font = new Font(name, Font.PLAIN, DEFAULT_SIZE);
        // Java uses Dialog family for nonexistent fonts 
        if (!Font.DIALOG.equals(name) && UIUtil.isDialogFont(font)) {
          throw new IllegalArgumentException("not supported " + font);
        }
      }
      else if (DEFAULT_SIZE != font.getSize()) {
        font = font.deriveFont((float)DEFAULT_SIZE);
        name = font.getFontName(ENGLISH);
      }
      int width = getFontWidth(font, Font.PLAIN);
      if (!plainOnly) {
        if (width != 0 && width != getFontWidth(font, Font.BOLD)) width = 0;
        if (width != 0 && width != getFontWidth(font, Font.ITALIC)) width = 0;
        if (width != 0 && width != getFontWidth(font, Font.BOLD | Font.ITALIC)) width = 0;
      }
      return new FontInfo(name, font, width > 0);
    }
    catch (Throwable ignored) {
      return null; // skip font that cannot be processed
    }
  }

  private static boolean isWrongSuffix(String name) {
    for (String suffix : WRONG_SUFFIX) {
      if (name.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private static int getFontWidth(Font font, int mask) {
    if (mask != Font.PLAIN) {
      //noinspection MagicConstant
      font = font.deriveFont(mask ^ font.getStyle());
    }
    int width = getCharWidth(font, ' ');
    return width == getCharWidth(font, 'l') && width == getCharWidth(font, 'W') ? width : 0;
  }

  public static boolean isMonospaced(Font font) {
    return getFontWidth(font, Font.PLAIN) > 0;
  }

  private static int getCharWidth(Font font, char ch) {
    if (font.canDisplay(ch)) {
      Rectangle bounds = font.getStringBounds(new char[]{ch}, 0, 1, DEFAULT_CONTEXT).getBounds();
      if (!bounds.isEmpty()) return bounds.width;
    }
    return 0;
  }

  private static final class LazyListByName {
    private static final List<FontInfo> LIST = byName();
  }

  private static final class LazyListByFont {
    private static final List<FontInfo> LIST = byFont();
  }

  private static final class FontInfoComparator implements Comparator<FontInfo> {
    @Override
    public int compare(FontInfo one, FontInfo two) {
      if (one.isMonospaced() && !two.isMonospaced()) return -1;
      if (!one.isMonospaced() && two.isMonospaced()) return 1;
      return one.toString().compareToIgnoreCase(two.toString());
    }
  }
}
