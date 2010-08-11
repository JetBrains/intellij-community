/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class ColorUtil {
  private ColorUtil() {
  }

  public static Color shift(Color c, double d) {
    return new Color((int)(c.getRed() * d), (int)(c.getGreen() * d), (int)(c.getBlue() * d), c.getAlpha());
  }

  public static Color withAlpha(Color c, double a) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(255 * a));
  }

  public static Color withAlphaAdjustingDarkness(Color c, double d) {
    return shift(withAlpha(c, d), d);
  }

  public static String toHex(@NotNull final Color c) {
    final String R = Integer.toHexString(c.getRed());
    final String G = Integer.toHexString(c.getGreen());
    final String B = Integer.toHexString(c.getBlue());
    return new StringBuffer()
      .append(R.length() < 2 ? "0" : "").append(R)
      .append(G.length() < 2 ? "0" : "").append(G)
      .append(B.length() < 2 ? "0" : "").append(B)
      .toString();
  }

  /**
   * Return Color object from string. The following formats are allowed:
   * <code>#abc123</code>,
   * <code>ABC123</code>,
   * <code>ab5</code>,
   * <code>#FFF</code>.
   *
   * @param str hex string
   * @return Color object
   */
  public static Color fromHex(String str) {
    if (str.startsWith("#")) {
      str = str.substring(1);
    }
    if (str.length() == 3) {
      return new Color(
        17 * Integer.valueOf(String.valueOf(str.charAt(0)), 16).intValue(),
        17 * Integer.valueOf(String.valueOf(str.charAt(1)), 16).intValue(),
        17 * Integer.valueOf(String.valueOf(str.charAt(2)), 16).intValue());
    } else if (str.length() == 6) {
      return Color.decode("0x" + str);
    } else {
      throw new IllegalArgumentException("Should be String of 3 or 6 chars length.");
    }
  }

  public static Color fromHex(String str, Color defaultValue) {
    try {
      return fromHex(str);
    } catch (Exception e) {
      return defaultValue;
    }
  }
}
