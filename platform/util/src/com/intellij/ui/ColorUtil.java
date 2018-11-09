// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
  public static Color dimmer(@NotNull final Color color) {
    NotNullProducer<Color> func = new NotNullProducer<Color>() {

      @NotNull
      @Override
      public Color produce() {
        float[] rgb = color.getRGBColorComponents(null);

        float alpha = 0.80f;
        float rem = 1 - alpha;
        return new Color(rgb[0] * alpha + rem, rgb[1] * alpha + rem, rgb[2] * alpha + rem);
      }
    };
    return wrap(color, func);
  }

  private static Color wrap(@NotNull Color color, NotNullProducer<Color> func) {
    return color instanceof JBColor ? new JBColor(func) : func.produce();
  }

  private static int shift(int colorComponent, double d) {
    final int n = (int)(colorComponent * d);
    return n > 255 ? 255 : n < 0 ? 0 : n;
  }

  @NotNull
  public static Color shift(@NotNull final Color c, final double d) {
    NotNullProducer<Color> func = new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        return new Color(shift(c.getRed(), d), shift(c.getGreen(), d), shift(c.getBlue(), d), c.getAlpha());
      }
    };
    return wrap(c, func);
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
  public static Color toAlpha(@Nullable Color color, final int a) {
    final Color c = color == null ? Color.black : color;
    NotNullProducer<Color> func = new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
      }
    };
    return wrap(c, func);
  }

  @NotNull
  public static String toHex(@NotNull final Color c) {
    return toHex(c, false);
  }

  @NotNull
  public static String toHex(@NotNull final Color c, final boolean withAlpha) {
    final String R = Integer.toHexString(c.getRed());
    final String G = Integer.toHexString(c.getGreen());
    final String B = Integer.toHexString(c.getBlue());

    final String rgbHex = (R.length() < 2 ? "0" : "") + R + (G.length() < 2 ? "0" : "") + G + (B.length() < 2 ? "0" : "") + B;
    if (!withAlpha){
      return rgbHex;
    }

    final String A = Integer.toHexString(c.getAlpha());
    return rgbHex + (A.length() < 2 ? "0" : "") + A;
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
  public static Color fromHex(@Nullable String str, @Nullable Color defaultValue) {
    if (str == null) return defaultValue;
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
      return new JBColor(new NotNullProducer<Color>() {
        @NotNull
        @Override
        public Color produce() {
          String colorString = UIUtil.isUnderDarcula() ? colored.darkVariant() : colored.color();
          Color color = fromHex(colorString, null);
          if (color == null) {
            throw new IllegalArgumentException("Can't parse " + colorString);
          }
          return color;
        }
      });
    }
    return null;
  }

  /**
   * @param c color to check
   * @return dark or not
   */
  public static boolean isDark(@NotNull Color c) {
    return ((getLuminance(c) + 0.05) / 0.05) < 4.5;
  }

  public static double getLuminance(@NotNull Color color) {
    return getLinearRGBComponentValue(color.getRed() / 255.0) * 0.2126 +
           getLinearRGBComponentValue(color.getGreen() / 255.0) * 0.7152 +
           getLinearRGBComponentValue(color.getBlue() / 255.0) * 0.0722;
  }

  public static double getLinearRGBComponentValue(double colorValue) {
    if (colorValue <= 0.03928) {
      return colorValue / 12.92;
    }
    return Math.pow(((colorValue + 0.055) / 1.055), 2.4);
  }

  @NotNull
  public static Color mix(@NotNull final Color c1, @NotNull final Color c2, double balance) {
    final double b = Math.min(1, Math.max(0, balance));
    NotNullProducer<Color> func = new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        return new Color((int)((1 - b) * c1.getRed() + c2.getRed() * b + .5),
                         (int)((1 - b) * c1.getGreen() + c2.getGreen() * b + .5),
                         (int)((1 - b) * c1.getBlue() + c2.getBlue() * b + .5),
                         (int)((1 - b) * c1.getAlpha() + c2.getAlpha() * b + .5));
      }
    };
    return c1 instanceof JBColor || c2 instanceof JBColor ? new JBColor(func) : func.produce();
  }
}
