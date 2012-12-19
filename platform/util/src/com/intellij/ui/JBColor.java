/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class JBColor extends Color {
  public JBColor(int rgb, int darkRGB) {
    super(isDark() ? darkRGB : rgb);
  }

  public JBColor(Color regular, Color dark) {
    super(isDark() ? dark.getRGB() : regular.getRGB(), (isDark() ? dark : regular).getAlpha() != 255);
  }

  private static boolean isDark() {
    return UIUtil.isUnderDarcula();
  }

  public final static JBColor red = new JBColor(Color.red, DarculaColors.RED);
  public final static JBColor RED = red;

  public final static JBColor blue = new JBColor(Color.blue, DarculaColors.BLUE);
  public final static JBColor BLUE = blue;

  public final static JBColor white = new JBColor(Color.white, UIUtil.getListBackground());
  public final static JBColor WHITE = white;

  public final static JBColor black = new JBColor(Color.black, UIUtil.getListForeground());
  public final static JBColor BLACK = black;

  public final static JBColor gray = new JBColor(Gray._128, Gray._128);
  public final static JBColor GRAY = gray;

  public final static JBColor lightGray = new JBColor(Gray._192, Gray._64);
  public final static JBColor LIGHT_GRAY = lightGray;

  public final static JBColor darkGray = new JBColor(Gray._64, Gray._192);
  public final static JBColor DARK_GRAY = darkGray;

  public final static JBColor pink = new JBColor(Color.pink, Color.pink);
  public final static JBColor PINK = pink;

  public final static JBColor orange = new JBColor(Color.orange, new Color(159, 107, 0));
  public final static JBColor ORANGE = orange;

  public final static JBColor yellow = new JBColor(Color.yellow, new Color(138, 138, 0));
  public final static JBColor YELLOW = yellow;

  public final static JBColor green = new JBColor(Color.green, new Color(98, 150, 85));
  public final static JBColor GREEN = green;

  public final static Color magenta = new JBColor(Color.magenta, new Color(151, 118, 169));
  public final static Color MAGENTA = magenta;

  public final static Color cyan = new JBColor(Color.cyan, new Color(0, 137, 137));
  public final static Color CYAN = cyan;

  public static Color foreground = UIUtil.getLabelForeground();
  public static Color FOREGROUND = foreground;

  public static Color background = UIUtil.getListBackground();
  public static Color BACKGROUND = background;
}
