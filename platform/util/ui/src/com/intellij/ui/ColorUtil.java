// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.diagnostic.Checks;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public final class ColorUtil {
  private ColorUtil() {
  }

  public static @NotNull Color softer(@NotNull Color color) {
    if (color.getBlue() > 220 && color.getRed() > 220 && color.getGreen() > 220) return color;
    final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    return Color.getHSBColor(hsb[0], 0.6f * hsb[1], hsb[2]);
  }

  public static @NotNull Color darker(@NotNull Color color, int tones) {
    return hackBrightness(color, tones, 1 / 1.1F);
  }

  public static @NotNull Color brighter(@NotNull Color color, int tones) {
    return hackBrightness(color, tones, 1.1F);
  }

  public static @NotNull Color tuneHue(@NotNull Color color, int howMuch, float hackValue) {
    return tuneHSBComponent(color, 0, howMuch, hackValue);
  }

  public static @NotNull Color tuneSaturation(@NotNull Color color, int howMuch, float hackValue) {
    return tuneHSBComponent(color, 1, howMuch, hackValue);
  }

  /**
   * All grey tones (including black) have 0 saturation so its saturation won't change.
   * That is why we handle such tones in a special why. Suppose we want to decrease saturation, then:
   * <ul>
   *   <li>For black tones we will increase brightness, making it more grey-ish.</li>
   *   <li>For white tones we will decrease brightness, making it more grey-ish.</li>
   *   <li>For remaining grey tones we will do nothing.</li>
   * </ul>
   */
  public static @NotNull Color tuneSaturationEspeciallyGrey(@NotNull Color color, int howMuch, float hackValue) {
    if (color.getRed() == color.getBlue() && color.getBlue() == color.getGreen()) {
      return color.getGreen() <= 64 ? shiftHSBComponent(color, 2, howMuch * (1 - hackValue) / 1.5f) :
             color.getGreen() >= 192 ? shiftHSBComponent(color, 2, howMuch * (hackValue - 1) / 1.5f) :
             color;
    }
    return tuneHSBComponent(color, 1, howMuch, hackValue);
  }

  public static @NotNull Color hackBrightness(@NotNull Color color, int howMuch, float hackValue) {
    return tuneHSBComponent(color, 2, howMuch, hackValue);
  }

  private static @NotNull Color tuneHSBComponent(@NotNull Color color, int componentIndex, int howMuch, float factor) {
    Checks.checkIndex(componentIndex, 3);
    float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    float component = hsb[componentIndex];
    for (int i = 0; i < howMuch; i++) {
      component = MathUtil.clamp(factor * component, 0, 1);
      if (component == 0 || component == 1) break;
    }
    hsb[componentIndex] = component;
    return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
  }

  private static @NotNull Color shiftHSBComponent(@NotNull Color color, int componentIndex, float shift) {
    Checks.checkIndex(componentIndex, 3);
    float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    hsb[componentIndex] = MathUtil.clamp(hsb[componentIndex] + shift, 0, 1);
    return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
  }

  public static @NotNull Color saturate(@NotNull Color color, int tones) {
    final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    float saturation = hsb[1];
    for (int i = 0; i < tones; i++) {
      saturation = Math.min(1, saturation * 1.1F);
      if (saturation == 1) break;
    }
    return Color.getHSBColor(hsb[0], saturation, hsb[2]);
  }

  public static @NotNull Color desaturate(@NotNull Color color, int tones) {
    final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    float saturation = hsb[1];
    for (int i = 0; i < tones; i++) {
      saturation = Math.max(0, saturation / 1.1F);
      if (saturation == 0) break;
    }
    return Color.getHSBColor(hsb[0], saturation, hsb[2]);
  }

  public static @NotNull Color dimmer(final @NotNull Color color) {
    return wrap(color, () -> {
      float[] rgb = color.getRGBColorComponents(null);

      float alpha = 0.80f;
      float rem = 1 - alpha;
      return new Color(rgb[0] * alpha + rem, rgb[1] * alpha + rem, rgb[2] * alpha + rem);
    });
  }

  private static Color wrap(@NotNull Color color, Supplier<? extends Color> func) {
    return color instanceof JBColor ? JBColor.lazy(func) : func.get();
  }

  private static int shift(int colorComponent, double d) {
    final int n = (int)(colorComponent * d);
    return n > 255 ? 255 : Math.max(n, 0);
  }

  public static @NotNull Color shift(final @NotNull Color c, final double d) {
    Supplier<Color> func = () -> new Color(shift(c.getRed(), d), shift(c.getGreen(), d), shift(c.getBlue(), d), c.getAlpha());
    return wrap(c, func);
  }

  public static @NotNull Color withAlpha(@NotNull Color c, double a) {
    return toAlpha(c, (int)(255 * a));
  }

  public static @NotNull Color withPreAlpha(@NotNull Color c, double a) {
    float[] rgba = new float[4];

    rgba = withAlpha(c, a).getRGBComponents(rgba);
    return new Color(rgba[0] * rgba[3], rgba[1] * rgba[3], rgba[2] * rgba[3], 1.0f);
  }

  public static @NotNull Color toAlpha(@Nullable Color color, final int a) {
    final Color c = color == null ? Color.black : color;
    Supplier<Color> func = () -> new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    return wrap(c, func);
  }

  public static @NotNull String toHex(final @NotNull Color c) {
    return toHex(c, false);
  }

  public static @NotNull String toHex(final @NotNull Color c, final boolean withAlpha) {
    final String R = Integer.toHexString(c.getRed());
    final String G = Integer.toHexString(c.getGreen());
    final String B = Integer.toHexString(c.getBlue());

    final String rgbHex = (R.length() < 2 ? "0" : "") + R + (G.length() < 2 ? "0" : "") + G + (B.length() < 2 ? "0" : "") + B;
    if (!withAlpha) {
      return rgbHex;
    }

    final String A = Integer.toHexString(c.getAlpha());
    return rgbHex + (A.length() < 2 ? "0" : "") + A;
  }

  public static @NotNull @NlsSafe String toHtmlColor(final @NotNull Color c) {
    return "#" + toHex(c);
  }

  /**
   * Return Color object from string. The following formats are allowed:
   * {@code 0xA1B2C3},
   * {@code #abc123},
   * {@code ABC123},
   * {@code ab5},
   * {@code #FFF}.
   *
   * @param str hex string
   * @return Color object
   */
  public static @NotNull Color fromHex(@NotNull String str) {
    return ColorHexUtil.fromHex(str);
  }

  public static @Nullable Color fromHex(@Nullable String str, @Nullable Color defaultValue) {
    return ColorHexUtil.fromHex(str, defaultValue);
  }

  /**
   * @param c color to check
   * @return dark or not
   */
  public static boolean isDark(@NotNull Color c) {
    return ((getLuminance(c) + 0.05) / 0.05) < 4.5;
  }

  public static boolean areContrasting(@NotNull Color c1, @NotNull Color c2) {
    return Double.compare(getContrast(c1, c2), 4.5) >= 0;
  }

  /**
   * Contrast ratios can range from 1 to 21 (commonly written 1:1 to 21:1).
   * Text foreground and background colors shall have contrast ration of at least 4.5:1, large-scale text - of at least 3:1.
   *
   * @see <a href="https://www.w3.org/TR/2008/REC-WCAG20-20081211/#contrast-ratiodef">W3C contrast ratio definition<a/>
   */
  public static double getContrast(@NotNull Color c1, @NotNull Color c2) {
    double l1 = getLuminance(c1);
    double l2 = getLuminance(c2);
    return (Math.max(l1, l2) + 0.05) / (Math.min(l2, l1) + 0.05);
  }

  /**
   * @see <a href="https://www.w3.org/TR/2008/REC-WCAG20-20081211/#relativeluminancedef">W3C relative luminance definition<a/>
   */
  public static double getLuminance(@NotNull Color color) {
    return getLinearRGBComponentValue(color.getRed() / 255.0) * 0.2126 +
           getLinearRGBComponentValue(color.getGreen() / 255.0) * 0.7152 +
           getLinearRGBComponentValue(color.getBlue() / 255.0) * 0.0722;
  }

  private static double getLinearRGBComponentValue(double colorValue) {
    if (colorValue <= 0.03928) return colorValue / 12.92;
    return Math.pow(((colorValue + 0.055) / 1.055), 2.4);
  }

  public static @NotNull Color mix(final @NotNull Color c1, final @NotNull Color c2, double balance) {
    if (balance <= 0) return c1;
    if (balance >= 1) return c2;
    Supplier<Color> func = new MixedColorProducer(c1, c2, balance);
    return c1 instanceof JBColor || c2 instanceof JBColor ? JBColor.lazy(func) : func.get();
  }
}
