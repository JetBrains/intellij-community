// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.LightPlatformTestCase;

import java.awt.Color;
import java.util.Locale;

public final class ValueElementReaderTest extends LightPlatformTestCase {
  private final ValueElementReader myReader = new ValueElementReader();

  private enum TestEnum {VALUE}

  private <T> T read(Class<T> type) throws Exception {
    return read(type, null, null);
  }

  private <T> T read(Class<T> type, String value) throws Exception {
    return read(type, null, new Option(value));
  }

  private <T> T read(Class<T> type, String attribute, Option option) throws Exception {
    myReader.setAttribute(attribute);
    return myReader.read(type, option == null ? null : JDOMUtil.load(option.toString()));
  }

  public void testString() throws Exception {
    assertNull(read(String.class));
    assertNull(read(String.class, null));
    assertNull(read(String.class, ""));
    assertEquals("CAFE", read(String.class, "CAFE"));
  }

  public void testInteger() throws Exception {
    assertNull(read(Integer.class));
    assertNull(read(Integer.class, null));
    assertNull(read(Integer.class, ""));
    assertNull(read(Integer.class, "+"));
    assertNull(read(Integer.class, "CAFE"));
    assertNull(read(Integer.class, "123456789012345678901234567890"));
    assertEquals(Integer.valueOf(123), read(Integer.class, "123"));
    assertEquals(Integer.valueOf(-45), read(Integer.class, "-45"));
  }

  public void testFloat() throws Exception {
    assertNull(read(Float.class));
    assertNull(read(Float.class, null));
    assertNull(read(Float.class, ""));
    assertNull(read(Float.class, "+"));
    assertNull(read(Float.class, "CAFE"));
    assertEquals(123.4f, read(Float.class, "123.4"));
    assertEquals(-5678f, read(Float.class, "-5678"));
  }

  public void testColor() throws Exception {
    assertNull(read(Color.class));
    assertNull(read(Color.class, null));
    assertNull(read(Color.class, ""));
    assertNull(read(Color.class, "Z"));
    assertEquals(new Color(0x00CAFE), read(Color.class, "00CAFE"));
    assertEquals(new Color(0x123456), read(Color.class, "0x123456"));
    assertEquals(new Color(0x78123456, true), read(Color.class, "12345678"));
  }

  public void testEnum() throws Exception {
    assertNull(read(TestEnum.class));
    assertNull(read(TestEnum.class, null));
    assertNull(read(TestEnum.class, ""));
    assertNull(read(TestEnum.class, "1"));
    assertEquals(TestEnum.VALUE, read(TestEnum.class, "0"));
    assertEquals(TestEnum.VALUE, read(TestEnum.class, "value"));
    assertEquals(TestEnum.VALUE, read(TestEnum.class, "VALUE"));
  }

  public void testFontSize() throws Exception {
    assertNull(read(FontSize.class));
    assertNull(read(FontSize.class, null));
    assertNull(read(FontSize.class, ""));
    for (FontSize size : FontSize.values()) {
      assertEquals(size, read(FontSize.class, size.name()));
      assertEquals(size, read(FontSize.class, size.name().toLowerCase(Locale.ENGLISH)));
      assertEquals(size, read(FontSize.class, String.valueOf(size.ordinal())));
    }
  }

  public void testPriority() throws Exception {
    priority(3.3f, 2.2f, "1.1", "2.2", "3.3");
    priority(3.3f, 2.2f, "X.X", "2.2", "3.3");
    priority(3.3f, 1.1f, "1.1", "X.X", "3.3");
    priority(2.2f, 2.2f, "1.1", "2.2", "X.X");
    priority(1.1f, 1.1f, "1.1", "X.X", "X.X");
    priority(2.2f, 2.2f, "X.X", "2.2", "X.X");
    priority(3.3f, null, "X.X", "X.X", "3.3");
    priority(null, null, "X.X", "X.X", "X.X");
  }

  private void priority(Float with, Float without, String value, String os, String extra) throws Exception {
    Option option = new Option(value).os(os).put("extra", extra);
    assertEquals(with, read(Float.class, "extra", option));
    assertEquals(without, read(Float.class, null, option));
  }
}
