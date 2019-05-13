package com.intellij.util.ui;

import junit.framework.TestCase;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Locale;

/**
 * @author Sergey.Malenkov
 */
public final class FontInfoTest extends TestCase {
  private static void check(String name, boolean exist) {
    if (GraphicsEnvironment.isHeadless()) return;
    FontInfo info = FontInfo.get(name);
    assertEquals(exist, null != info);
    if (name != null) {
      assertEquals(info, FontInfo.get(name.toUpperCase(Locale.ENGLISH)));
      assertEquals(info, FontInfo.get(name.toLowerCase(Locale.ENGLISH)));
    }
    exist = false; // because predefined names are not real font names
    assertEquals(exist, null != FontInfo.get(new Font(name, Font.PLAIN, 12)));
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
