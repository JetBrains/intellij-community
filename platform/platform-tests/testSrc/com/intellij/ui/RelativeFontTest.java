/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui;

import junit.framework.TestCase;

import java.awt.Font;
import java.awt.FontMetrics;
import java.beans.PropertyChangeListener;
import javax.swing.JLabel;

/**
 * @author Sergey.Malenkov
 */
public final class RelativeFontTest extends TestCase {
  private static final RelativeFont BOLD_ITALIC_FONT = RelativeFont.NORMAL.style(Font.BOLD | Font.ITALIC);
  private static final RelativeFont MONOSPACED_FONT = RelativeFont.NORMAL.family(Font.MONOSPACED);

  public void testInstallUninstall() {
    JLabel label = new JLabel();
    checkInstallUninstall(1, BOLD_ITALIC_FONT.install(label));
    checkInstallUninstall(1, MONOSPACED_FONT.install(label));
    checkInstallUninstall(0, RelativeFont.uninstallFrom(label));
    checkInstallUninstall(0, RelativeFont.uninstallFrom(label));
  }

  private static void checkInstallUninstall(int expected, JLabel label) {
    int actual = 0;
    for (PropertyChangeListener listener : label.getPropertyChangeListeners("font")) {
      if (listener instanceof RelativeFont) {
        actual++;
      }
    }
    assertEquals(expected, actual);
  }

  public void testFamily() {
    checkMonospaced(toMonospaced(new Font(Font.DIALOG, Font.PLAIN, 12), false));
  }

  private static void checkMonospaced(Font font) {
    assertSame(font, toMonospaced(font, true));
  }

  private static Font toMonospaced(Font font, boolean monospaced) {
    FontMetrics fm = new JLabel().getFontMetrics(font);
    assertEquals(monospaced, fm.charWidth('i') == fm.charWidth('m'));
    return MONOSPACED_FONT.derive(font);
  }

  public void testStylePlain() {
    checkFontStyle(Font.PLAIN);
  }

  public void testStyleBold() {
    checkFontStyle(Font.BOLD);
  }

  public void testStyleItalic() {
    checkFontStyle(Font.ITALIC);
  }

  public void testStyleBoldItalic() {
    checkFontStyle(Font.BOLD | Font.ITALIC);
  }

  private static void checkFontStyle(int style) {
    Font font = new Font(Font.DIALOG, style, 12);
    assertSame(font, RelativeFont.NORMAL.derive(font));
    assertEquals(Font.PLAIN, RelativeFont.PLAIN.derive(font).getStyle());
    assertEquals(Font.BOLD, RelativeFont.BOLD.derive(font).getStyle());
    assertEquals(Font.ITALIC, RelativeFont.ITALIC.derive(font).getStyle());
    assertEquals(Font.BOLD | Font.ITALIC, BOLD_ITALIC_FONT.derive(font).getStyle());
  }

  public void testSize12() {
    checkFontSize(12, 10, 11, 13, 14);
  }

  public void testSize16() {
    checkFontSize(16, 13, 15, 17, 19);
  }

  public void testSize24() {
    checkFontSize(24, 20, 22, 26, 29);
  }

  public void testSize32() {
    checkFontSize(32, 27, 29, 35, 38);
  }

  private static void checkFontSize(int size, int tiny, int small, int large, int huge) {
    Font font = new Font(Font.DIALOG, Font.PLAIN, size);
    assertEquals(tiny, RelativeFont.TINY.derive(font).getSize());
    assertEquals(small, RelativeFont.SMALL.derive(font).getSize());
    assertEquals(large, RelativeFont.LARGE.derive(font).getSize());
    assertEquals(huge, RelativeFont.HUGE.derive(font).getSize());
  }

  public void testAutoUpdate() {
    JLabel label = new JLabel();
    label.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
    MONOSPACED_FONT.install(label);
    checkAutoUpdate(label.getFont(), Font.PLAIN, 12);
    label.setFont(new Font(Font.DIALOG, Font.BOLD, 16));
    checkAutoUpdate(label.getFont(), Font.BOLD, 16);
  }

  public void testAutoUpdateClash() {
    JLabel label = new JLabel();
    label.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
    BOLD_ITALIC_FONT.install(label);
    MONOSPACED_FONT.install(label); // modifies bold-italic font
    checkAutoUpdate(label.getFont(), Font.BOLD | Font.ITALIC, 12);
    label.setFont(new Font(Font.DIALOG, Font.BOLD, 16));
    checkAutoUpdate(label.getFont(), Font.BOLD, 16);
  }

  private static void checkAutoUpdate(Font font, int style, int size) {
    checkMonospaced(font);
    assertEquals(style, font.getStyle());
    assertEquals(size, font.getSize());
  }
}
