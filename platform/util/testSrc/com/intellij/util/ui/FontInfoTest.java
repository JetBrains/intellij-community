// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import junit.framework.TestCase;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Locale;

public final class FontInfoTest extends TestCase {
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

  public void testDialog() {
    check(Font.DIALOG, true);
  }

  public void testDialogInput() {
    check(Font.DIALOG_INPUT, true);
  }

  public void testMonospaced() {
    check(Font.MONOSPACED, true);
  }

  public void testSansSerif() {
    check(Font.SANS_SERIF, true);
  }

  public void testSerif() {
    check(Font.SERIF, true);
  }

  public void testNonExistent() {
    check("Strange (very strange) nonexistent font", false);
  }

  public void testNull() {
    check(null, false);
  }
}
