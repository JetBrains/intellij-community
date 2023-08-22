// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.ui.ColorUtil.toHex;

public final class TextAttributesReaderTest extends LightPlatformTestCase {
  private final TextAttributesReader myReader = new TextAttributesReader();

  public void testBackgroundColor() throws Exception {
    assertNull(readBackgroundColor(null));
    assertNull(readBackgroundColor(""));
    assertNull(readBackgroundColor("WRONG"));
    String hex = toHex(Color.WHITE);
    assertEquals(Color.WHITE, readBackgroundColor(hex));
    assertEquals(Color.WHITE, readBackgroundColor('#' + hex));
    assertEquals(Color.WHITE, readBackgroundColor("0x" + hex));
  }

  private Color readBackgroundColor(Object value) throws Exception {
    return read(value("BACKGROUND", value)).getBackgroundColor();
  }

  public void testForegroundColor() throws Exception {
    assertNull(readForegroundColor(null));
    assertNull(readForegroundColor(""));
    assertNull(readForegroundColor("WRONG"));
    String hex = toHex(Color.BLACK);
    assertEquals(Color.BLACK, readForegroundColor(hex));
    assertEquals(Color.BLACK, readForegroundColor('#' + hex));
    assertEquals(Color.BLACK, readForegroundColor("0x" + hex));
  }

  private Color readForegroundColor(Object value) throws Exception {
    return read(value("FOREGROUND", value)).getForegroundColor();
  }

  public void testErrorStripeColor() throws Exception {
    assertNull(readErrorStripeColor(null));
    assertNull(readErrorStripeColor(""));
    assertNull(readErrorStripeColor("WRONG"));
    String hex = toHex(Color.RED);
    assertEquals(Color.RED, readErrorStripeColor(hex));
    assertEquals(Color.RED, readErrorStripeColor('#' + hex));
    assertEquals(Color.RED, readErrorStripeColor("0x" + hex));
  }

  private Color readErrorStripeColor(Object value) throws Exception {
    return read(value("ERROR_STRIPE_COLOR", value)).getErrorStripeColor();
  }

  public void testEffectColor() throws Exception {
    assertNull(readEffectColor(null));
    assertNull(readEffectColor(""));
    assertNull(readEffectColor("WRONG"));
    String hex = toHex(Color.YELLOW);
    assertEquals(Color.YELLOW, readEffectColor(hex));
    assertEquals(Color.YELLOW, readEffectColor('#' + hex));
    assertEquals(Color.YELLOW, readEffectColor("0x" + hex));
  }

  private Color readEffectColor(Object value) throws Exception {
    return read(value("EFFECT_COLOR", value)).getEffectColor();
  }

  public void testEffectType() throws Exception {
    assertEquals(EffectType.BOXED, readEffectType(null));
    assertEquals(EffectType.BOXED, readEffectType(""));
    assertEquals(EffectType.BOXED, readEffectType("WRONG"));
    assertEquals(EffectType.BOXED, readEffectType("0"));
    assertEquals(EffectType.BOXED, readEffectType("BORDER"));
    assertEquals(EffectType.LINE_UNDERSCORE, readEffectType("1"));
    assertEquals(EffectType.LINE_UNDERSCORE, readEffectType("LINE"));
    assertEquals(EffectType.WAVE_UNDERSCORE, readEffectType("2"));
    assertEquals(EffectType.WAVE_UNDERSCORE, readEffectType("WAVE"));
    assertEquals(EffectType.STRIKEOUT, readEffectType("3"));
    assertEquals(EffectType.STRIKEOUT, readEffectType("STRIKEOUT"));
    assertEquals(EffectType.BOLD_LINE_UNDERSCORE, readEffectType("4"));
    assertEquals(EffectType.BOLD_LINE_UNDERSCORE, readEffectType("BOLD_LINE"));
    assertEquals(EffectType.BOLD_DOTTED_LINE, readEffectType("5"));
    assertEquals(EffectType.BOLD_DOTTED_LINE, readEffectType("BOLD_DOTTED_LINE"));
    assertEquals(EffectType.BOXED, readEffectType("6"));
  }

  private EffectType readEffectType(Object value) throws Exception {
    return read(value("EFFECT_TYPE", value)).getEffectType();
  }

  public void testFontType() throws Exception {
    assertEquals(Font.PLAIN, readFontType(null));
    assertEquals(Font.PLAIN, readFontType(""));
    assertEquals(Font.PLAIN, readFontType("WRONG"));
    assertEquals(Font.PLAIN, readFontType("0"));
    assertEquals(Font.PLAIN, readFontType("PLAIN"));
    assertEquals(Font.PLAIN, readFontType(Font.PLAIN));
    assertEquals(Font.BOLD, readFontType("1"));
    assertEquals(Font.BOLD, readFontType("BOLD"));
    assertEquals(Font.BOLD, readFontType(Font.BOLD));
    assertEquals(Font.ITALIC, readFontType("2"));
    assertEquals(Font.ITALIC, readFontType("ITALIC"));
    assertEquals(Font.ITALIC, readFontType(Font.ITALIC));
    assertEquals(Font.BOLD | Font.ITALIC, readFontType("3"));
    assertEquals(Font.BOLD | Font.ITALIC, readFontType("BOLD_ITALIC"));
    assertEquals(Font.BOLD | Font.ITALIC, readFontType(Font.BOLD | Font.ITALIC));
    assertEquals(Font.PLAIN, readFontType("4"));
  }

  private int readFontType(Object value) throws Exception {
    return read(value("FONT_TYPE", value)).getFontType();
  }

  public void testTextAttributesCompatibility() throws Exception {
    TextAttributes actual = read((Element)null);
    assertEquals(new TextAttributes(), actual);
    compare(value());
    compare(value("UNSUPPORTED", null));
    compare(value("UNSUPPORTED", "OPTION"));
    compare(value(
      new Option("BACKGROUND", "000000"),
      new Option("BACKGROUND", "FFFFFF")
    ));
    compare(value(
      new Option("BACKGROUND", "000000"),
      new Option("FOREGROUND", "111111"),
      new Option("ERROR_STRIPE_COLOR", "222222"),
      new Option("EFFECT_COLOR", "333333"),
      new Option("EFFECT_TYPE", "1"),
      new Option("FONT_TYPE", "2")
    ));
    compare(value("EFFECT_TYPE", null));
    compare(value("FONT_TYPE", null));
    for (int i = 0; i < 6; i++) {
      compare(value("EFFECT_TYPE", i));
      compare(value("FONT_TYPE", i));
    }
  }

  private TextAttributes read(@NotNull String value) throws Exception {
    return read(JDOMUtil.load(value));
  }

  private TextAttributes read(Element element) {
    return myReader.read(TextAttributes.class, element);
  }

  private void compare(@NotNull String value) throws Exception {
    Element element = JDOMUtil.load(value);
    TextAttributes expected = new TextAttributes(element);
    TextAttributes actual = read(element);
    assertEquals(expected, actual);
  }

  @NotNull
  private static String value(String name, Object value) {
    return value(new Option(name, value == null ? null : value.toString()));
  }

  @NotNull
  private static String value(Option... options) {
    StringBuilder sb = new StringBuilder("<value>");
    for (Option option : options) {
      sb.append("\n\t").append(option);
    }
    return sb.append("\n</value>").toString();
  }
}
