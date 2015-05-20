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
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.SystemProperties;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBUI {
  private static boolean IS_HIDPI = calculateHiDPI();

  private static boolean calculateHiDPI() {
    if (SystemInfo.isMac) {
      return false;
    }

    if (SystemProperties.is("hidpi")) {
      return true;
    }

    if (SystemProperties.has("hidpi") && !SystemProperties.is("hidpi")) {
      return false;
    }

    if (SystemInfo.isWindows && getSystemDPI() > 144) {
      return true;
    }

    return false;
  }

  private static int getSystemDPI() {
    try {
      return Toolkit.getDefaultToolkit().getScreenResolution();
    } catch (HeadlessException e) {
      return 96;
    }
  }

  public static int scale(int i) {
    return isHiDPI() ? 2 * i : i;
  }

  public static JBDimension size(int width, int height) {
    return new JBDimension(width, height);
  }

  public static JBDimension size(int widthAndHeight) {
    return new JBDimension(widthAndHeight, widthAndHeight);
  }

  public static JBDimension size(Dimension size) {
    return size instanceof JBDimension ? ((JBDimension)size) : new JBDimension(size.width, size.height);
  }

  public static JBInsets insets(int top, int left, int bottom, int right) {
    return new JBInsets(top, left, bottom, right);
  }

  public static JBInsets insets(int all) {
    return insets(all, all, all, all);
  }

  public static JBInsets insets(int topBottom, int leftRight) {
    return insets(topBottom, leftRight, topBottom, leftRight);
  }

  public static JBInsets emptyInsets() {
    return new JBInsets(0, 0, 0, 0);
  }

  public static JBInsets insetsTop(int t) {
    return insets(t, 0, 0, 0);
  }

  public static JBInsets insetsLeft(int l) {
    return insets(0, l, 0, 0);
  }

  public static JBInsets insetsBottom(int b) {
    return insets(0, 0, b, 0);
  }

  public static JBInsets insetsRight(int r) {
    return insets(0, 0, 0, r);
  }

  public static EmptyIcon emptyIcon(int i) {
    return (EmptyIcon)EmptyIcon.create(scale(i));
  }

  public static JBDimension emptySize() {
    return new JBDimension(0, 0);
  }

  public static float scale(float f) {
    return f * scale(1);
  }

  public static JBInsets insets(Insets insets) {
    return JBInsets.create(insets);
  }

  public static boolean isHiDPI() {
    return IS_HIDPI;
  }

  public static class Fonts {
    public static JBFont label() {
      return JBFont.create(UIManager.getFont("Label.font"), false);
    }

    public static JBFont label(float size) {
      return label().deriveFont(scale(size));
    }

    public static JBFont smallFont() {
      return label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
    }

    public static JBFont miniFont() {
      return label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.MINI));
    }

    public static JBFont create(String fontFamily, int size) {
      return JBFont.create(new Font(fontFamily, Font.PLAIN, size));
    }
  }

  public static class Borders {
    public static JBEmptyBorder empty(int top, int left, int bottom, int right) {
      return new JBEmptyBorder(top, left, bottom, right);
    }

    public static JBEmptyBorder empty(int topAndBottom, int leftAndRight) {
      return new JBEmptyBorder(topAndBottom, leftAndRight, topAndBottom, leftAndRight);
    }

    public static JBEmptyBorder emptyTop(int offset) {
      return new JBEmptyBorder(offset, 0, 0, 0);
    }

    public static JBEmptyBorder emptyLeft(int offset) {
      return new JBEmptyBorder(0, offset,  0, 0);
    }

    public static JBEmptyBorder emptyBottom(int offset) {
      return new JBEmptyBorder(0, 0, offset, 0);
    }

    public static JBEmptyBorder emptyRight(int offset) {
      return new JBEmptyBorder(0, 0, 0, offset);
    }

    public static JBEmptyBorder empty() {
      return new JBEmptyBorder(0);
    }

    public static Border customLine(Color color, int top, int left, int bottom, int right) {
      return new CustomLineBorder(color, insets(top, left, bottom, right));
    }
  }
}
