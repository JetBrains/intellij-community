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

/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class ColorUtil {
  private ColorUtil() {
  }

  @NotNull
  public static Color marker(@NotNull final String name) {
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        throw new AssertionError(name);
      }
    }) {
      @Override
      public boolean equals(Object obj) {
        return this == obj;
      }

      @Override
      public String toString() {
        return name;
      }
    };
  }

  @NotNull
  public static Color softer(@NotNull Color color) {
    if (color.getBlue() > 220 && color.getRed() > 220 && color.getGreen() > 220) return color;
    final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    return Color.getHSBColor(hsb[0], 0.6f * hsb[1], hsb[2]);
  }

  @NotNull
  public static Color darker(@NotNull Color color, int tones) {
    return hackBrightness(color, tones, 1 / 1.1F);
  }

  @NotNull
  public static Color brighter(@NotNull Color color, int tones) {
    return hackBrightness(color, tones, 1.1F);
  }

  @NotNull
  public static Color hackBrightness(@NotNull Color color, int howMuch, float hackValue) {
    return hackBrightness(color.getRed(), color.getGreen(), color.getBlue(), howMuch, hackValue);
  }

  @NotNull
  public static Color hackBrightness(int r, int g, int b, int howMuch, float hackValue) {
    final float[] hsb = Color.RGBtoHSB(r, g, b, null);
    float brightness = hsb[2];
    for (int i = 0; i < howMuch; i++) {
      brightness = Math.min(1, Math.max(0, brightness * hackValue));
      if (brightness == 0 || brightness == 1) break;
    }
    return Color.getHSBColor(hsb[0], hsb[1], brightness);
  }

  @NotNull
  public static Color saturate(@NotNull Color color, int tones) {
    final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    float saturation = hsb[1];
    for (int i = 0; i < tones; i++) {
      saturation = Math.min(1, saturation * 1.1F);
      if (saturation == 1) break;
    }
    return Color.getHSBColor(hsb[0], saturation, hsb[2]);
  }

  @NotNull
  public static Color desaturate(@NotNull Color color, int tones) {
    final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    float saturation = hsb[1];
    for (int i = 0; i < tones; i++) {
      saturation = Math.max(0, saturation / 1.1F);
      if (saturation == 0) break;
    }
    return Color.getHSBColor(hsb[0], saturation, hsb[2]);
  }

  @NotNull
  public static Color dimmer(@NotNull Color color) {
    float[] rgb = color.getRGBColorComponents(null);

    float alpha = 0.80f;
    float rem = 1 - alpha;
    return new Color(rgb[0] * alpha + rem, rgb[1] * alpha + rem, rgb[2] * alpha + rem);
  }

  private static int shift(int colorComponent, double d) {
    final int n = (int)(colorComponent * d);
    return n > 255 ? 255 : n < 0 ? 0 : n;
  }

  @NotNull
  public static Color shift(@NotNull Color c, double d) {
    return new Color(shift(c.getRed(), d), shift(c.getGreen(), d), shift(c.getBlue(), d), c.getAlpha());
  }

  @NotNull
  public static Color withAlpha(@NotNull Color c, double a) {
    return toAlpha(c, (int)(255 * a));
  }

  @NotNull
  static Color srcOver(@NotNull Color c, @NotNull Color b) {
    float [] rgba = new float[4];
    rgba = c.getRGBComponents(rgba);
    float[] brgba = new float[4];
    brgba = b.getRGBComponents(brgba);
    float dsta = 1.0f - rgba[3];
    // Applying SrcOver rule
    return new Color(rgba[0]*rgba[3] + dsta*brgba[0],
                     rgba[1]*rgba[3] + dsta*brgba[1],
                     rgba[2]*rgba[3] + dsta*brgba[2], 1.0f);
  }

  @NotNull
  public static Color withPreAlpha(@NotNull Color c, double a) {
    float [] rgba = new float[4];

    rgba = withAlpha(c, a).getRGBComponents(rgba);
    return new Color(rgba[0]*rgba[3], rgba[1]*rgba[3], rgba[2]*rgba[3], 1.0f);
  }

  @NotNull
  public static Color toAlpha(@Nullable Color color, int a) {
    Color c = color == null ? Color.black : color;
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
  }

  @NotNull
  public static String toHex(@NotNull final Color c) {
    final String R = Integer.toHexString(c.getRed());
    final String G = Integer.toHexString(c.getGreen());
    final String B = Integer.toHexString(c.getBlue());
    return (R.length() < 2 ? "0" : "") + R + (G.length() < 2 ? "0" : "") + G + (B.length() < 2 ? "0" : "") + B;
  }

  @NotNull
  public static String toHtmlColor(@NotNull final Color c) {
    return "#"+toHex(c);
  }

  /**
   * Return Color object from string. The following formats are allowed:
   * {@code #abc123},
   * {@code ABC123},
   * {@code ab5},
   * {@code #FFF}.
   *
   * @param str hex string
   * @return Color object
   */
  @NotNull
  public static Color fromHex(@NotNull String str) {
    str = StringUtil.trimStart(str, "#");
    if (str.length() == 3) {
      return new Color(
        17 * Integer.valueOf(String.valueOf(str.charAt(0)), 16).intValue(),
        17 * Integer.valueOf(String.valueOf(str.charAt(1)), 16).intValue(),
        17 * Integer.valueOf(String.valueOf(str.charAt(2)), 16).intValue());
    }
    else if (str.length() == 6) {
      return Color.decode("0x" + str);
    }
    else {
      throw new IllegalArgumentException("Should be String of 3 or 6 chars length.");
    }
  }

  @Nullable
  public static Color fromHex(@NotNull String str, @Nullable Color defaultValue) {
    try {
      return fromHex(str);
    }
    catch (Exception e) {
      return defaultValue;
    }
  }

  @Nullable
  public static Color getColor(@NotNull Class<?> cls) {
    final Colored colored = cls.getAnnotation(Colored.class);
    if (colored != null) {
      return fromHex(UIUtil.isUnderDarcula() ? colored.darkVariant() : colored.color(), null);
    }
    return null;
  }

  /**
   * Checks whether color is dark or not based on perceptional luminosity
   * http://stackoverflow.com/questions/596216/formula-to-determine-brightness-of-rgb-color
   *
   * @param c color to check
   * @return dark or not
   */
  public static boolean isDark(@NotNull Color c) {
    // based on perceptional luminosity, see
    return 1 - (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255 >= 0.5;
  }

  @NotNull
  public static Color mix(@NotNull Color c1, @NotNull Color c2, double balance) {
    balance = Math.min(1, Math.max(0, balance));
    return new Color((int)((1 - balance) * c1.getRed() + c2.getRed() * balance + .5),
                     (int)((1 - balance) * c1.getGreen() + c2.getGreen() * balance + .5),
                     (int)((1 - balance) * c1.getBlue() + c2.getBlue() * balance + .5),
                     (int)((1 - balance) * c1.getAlpha() + c2.getAlpha() * balance + .5));
  }
}
