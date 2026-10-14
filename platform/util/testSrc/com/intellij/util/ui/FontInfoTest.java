// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class FontInfoTest {
  private static void check(String name, boolean exist) {
    if (GraphicsEnvironment.isHeadless()) return;
    FontInfo info = FontInfo.get(name);
    assertEquals(exist, null != info);
    if (name != null) {
      assertEquals(info, FontInfo.get(name.toUpperCase(Locale.ENGLISH)));
      assertEquals(info, FontInfo.get(name.toLowerCase(Locale.ENGLISH)));
    }
    // because predefined names are not real font names
    assertNull(FontInfo.get(new Font(name, Font.PLAIN, 12)));
  }

  @Test
  public void testDialog() {
    check(Font.DIALOG, true);
  }

  @Test
  public void testDialogInput() {
    check(Font.DIALOG_INPUT, true);
  }

  @Test
  public void testMonospaced() {
    check(Font.MONOSPACED, true);
  }

  @Test
  public void testSansSerif() {
    check(Font.SANS_SERIF, true);
  }

  @Test
  public void testSerif() {
    check(Font.SERIF, true);
  }

  @Test
  public void testNonExistent() {
    check("Strange (very strange) nonexistent font", false);
  }

  @Test
  public void testNull() {
    check(null, false);
  }
}
