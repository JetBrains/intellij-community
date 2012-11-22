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


  public static Color foreground = UIUtil.getLabelForeground();
  public static Color background = UIUtil.getListBackground();
}
