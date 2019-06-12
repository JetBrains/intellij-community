// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("SuspiciousPackagePrivateAccess")
public class TreeControlPainterTest {
  @ClassRule
  public static final RestoreScaleRule MANAGE_STATE = new RestoreScaleRule();

  @Test
  public void testClassicDefault() {
    JBUIScale.setUserScaleFactor((float)1);
    Control.Painter painter = new ClassicPainter(true, 7, 11, null);

    Control even = new TestControl(10, 10);
    testRendererOffset(painter, even, false, 18, 36, 54, 72, 90, 108);
    testRendererOffset(painter, even, true, 18, 36, 54, 72, 90, 108);
    testControlOffset(painter, even, 2, 20, 38, 56, 74, 92);

    Control odd = new TestControl(11, 11);
    testRendererOffset(painter, odd, false, 18, 36, 54, 72, 90, 108);
    testRendererOffset(painter, odd, true, 18, 36, 54, 72, 90, 108);
    testControlOffset(painter, odd, 2, 20, 38, 56, 74, 92);
  }

  @Test
  public void testClassicDefaultLeafIndent() {
    JBUIScale.setUserScaleFactor((float)1);
    Control.Painter painter = new ClassicPainter(false, 7, 11, 0);

    Control even = new TestControl(10, 10);
    testRendererOffset(painter, even, false, 18, 36, 54, 72, 90, 108);
    testRendererOffset(painter, even, true, 0, 18, 36, 54, 72, 90);
    testControlOffset(painter, even, 2, 20, 38, 56, 74, 92);

    Control odd = new TestControl(11, 11);
    testRendererOffset(painter, odd, false, 18, 36, 54, 72, 90, 108);
    testRendererOffset(painter, odd, true, 0, 18, 36, 54, 72, 90);
    testControlOffset(painter, odd, 2, 20, 38, 56, 74, 92);
  }

  @Test
  public void testClassicCompact() {
    JBUIScale.setUserScaleFactor((float)1);
    Control.Painter painter = new ClassicPainter(true, 0, 0, null);

    Control even = new TestControl(10, 10);
    testRendererOffset(painter, even, false, 12, 17, 22, 27, 32, 37);
    testRendererOffset(painter, even, true, 12, 17, 22, 27, 32, 37);
    testControlOffset(painter, even, 0, 5, 10, 15, 20, 25);

    Control odd = new TestControl(11, 11);
    testRendererOffset(painter, odd, false, 13, 18, 23, 28, 33, 38);
    testRendererOffset(painter, odd, true, 13, 18, 23, 28, 33, 38);
    testControlOffset(painter, odd, 0, 5, 10, 15, 20, 25);
  }

  @Test
  public void testClassicCompactLeafIndent() {
    JBUIScale.setUserScaleFactor((float)1);
    Control.Painter painter = new ClassicPainter(false, 0, 0, 0);

    Control even = new TestControl(10, 10);
    testRendererOffset(painter, even, false, 12, 17, 22, 27, 32, 37);
    testRendererOffset(painter, even, true, 0, 5, 10, 15, 20, 25);
    testControlOffset(painter, even, 0, 5, 10, 15, 20, 25);

    Control odd = new TestControl(11, 11);
    testRendererOffset(painter, odd, false, 13, 18, 23, 28, 33, 38);
    testRendererOffset(painter, odd, true, 0, 5, 10, 15, 20, 25);
    testControlOffset(painter, odd, 0, 5, 10, 15, 20, 25);
  }

  @Test
  public void testCompact() {
    JBUIScale.setUserScaleFactor((float)1);
    for (int i = 0; i < 4; i++) {
      Control.Painter painter = new CompactPainter(true, i, i, -1);

      Control even = new TestControl(10, 10);
      testRendererOffset(painter, even, false, 10 + 2 * i, 12 + 3 * i, 14 + 4 * i, 16 + 5 * i, 18 + 6 * i, 20 + 7 * i);
      testRendererOffset(painter, even, true, 10 + 2 * i, 12 + 3 * i, 14 + 4 * i, 16 + 5 * i, 18 + 6 * i, 20 + 7 * i);
      testControlOffset(painter, even, i, 2 + 2 * i, 4 + 3 * i, 6 + 4 * i, 8 + 5 * i, 10 + 6 * i);

      Control odd = new TestControl(11, 11);
      testRendererOffset(painter, odd, false, 11 + 2 * i, 13 + 3 * i, 15 + 4 * i, 17 + 5 * i, 19 + 6 * i, 21 + 7 * i);
      testRendererOffset(painter, odd, true, 11 + 2 * i, 13 + 3 * i, 15 + 4 * i, 17 + 5 * i, 19 + 6 * i, 21 + 7 * i);
      testControlOffset(painter, odd, i, 2 + 2 * i, 4 + 3 * i, 6 + 4 * i, 8 + 5 * i, 10 + 6 * i);
    }
  }

  @Test
  public void testCompactLeafIndent() {
    JBUIScale.setUserScaleFactor((float)1);
    for (int i = 0; i < 4; i++) {
      Control.Painter painter = new CompactPainter(false, i, i, 0);

      Control even = new TestControl(10, 10);
      testRendererOffset(painter, even, false, 10 + 2 * i, 12 + 3 * i, 14 + 4 * i, 16 + 5 * i, 18 + 6 * i, 20 + 7 * i);
      testRendererOffset(painter, even, true, 0, 2 + i, 4 + 2 * i, 6 + 3 * i, 8 + 4 * i, 10 + 5 * i);
      testControlOffset(painter, even, i, 2 + 2 * i, 4 + 3 * i, 6 + 4 * i, 8 + 5 * i, 10 + 6 * i);

      Control odd = new TestControl(11, 11);
      testRendererOffset(painter, odd, false, 11 + 2 * i, 13 + 3 * i, 15 + 4 * i, 17 + 5 * i, 19 + 6 * i, 21 + 7 * i);
      testRendererOffset(painter, odd, true, 0, 2 + i, 4 + 2 * i, 6 + 3 * i, 8 + 4 * i, 10 + 5 * i);
      testControlOffset(painter, odd, i, 2 + 2 * i, 4 + 3 * i, 6 + 4 * i, 8 + 5 * i, 10 + 6 * i);
    }
  }


  private static void assertRendererOffset(Control.Painter painter, Control control, int depth, boolean leaf) {
    Assert.assertTrue("depth=" + depth, painter.getRendererOffset(control, depth, leaf) < 0);
  }

  private static void assertRendererOffset(Control.Painter painter, Control control, int depth, boolean leaf, int expected) {
    Assert.assertEquals("depth=" + depth, expected, painter.getRendererOffset(control, depth, leaf));
  }

  private static void testRendererOffset(Control.Painter painter, Control control, boolean leaf, int... expected) {
    for (int depth : new int[]{Integer.MIN_VALUE, -1, -10}) {
      assertRendererOffset(painter, control, depth, leaf);
    }
    assertRendererOffset(painter, control, 0, leaf, 0);
    for (int depth = 0; depth < expected.length; depth++) {
      assertRendererOffset(painter, control, depth + 1, leaf, expected[depth]);
    }
  }

  private static void assertControlOffset(Control.Painter painter, Control control, int depth, boolean leaf) {
    Assert.assertTrue("depth=" + depth, painter.getControlOffset(control, depth, leaf) < 0);
  }

  private static void assertControlOffset(Control.Painter painter, Control control, int depth, int expected) {
    Assert.assertEquals("depth=" + depth, expected, painter.getControlOffset(control, depth, false));
  }

  private static void testControlOffset(Control.Painter painter, Control control, int... expected) {
    for (int depth : new int[]{Integer.MIN_VALUE, -1, -10, 0}) {
      assertControlOffset(painter, control, depth, true);
      assertControlOffset(painter, control, depth, false);
    }
    for (int depth = 0; depth < expected.length; depth++) {
      assertControlOffset(painter, control, depth + 1, true);
      assertControlOffset(painter, control, depth + 1, expected[depth]);
    }
  }


  private static final class TestControl implements Control {
    private final Icon icon;

    private TestControl(int width, int height) {
      icon = EmptyIcon.create(width, height);
    }

    @NotNull
    @Override
    public Icon getIcon(boolean expanded, boolean selected) {
      return icon;
    }

    @Override
    public int getWidth() {
      return icon.getIconWidth();
    }

    @Override
    public int getHeight() {
      return icon.getIconHeight();
    }

    @Override
    public void paint(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height, boolean expanded, boolean selected) {
    }
  }
}
