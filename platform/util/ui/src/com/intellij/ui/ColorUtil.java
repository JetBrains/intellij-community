// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.diagnostic.Checks;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

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
    return new TuneHSBComponent(color, componentIndex, howMuch, factor).createColor(false);
  }

  private static class TuneHSBComponent extends ColorMixture {
    @NotNull private final Color color;
    private final int componentIndex;
    private final int howMuch;
    private final float factor;

    private TuneHSBComponent(@NotNull Color color, int componentIndex, int howMuch, float factor) {
      super("tuneHSBComponent");
      this.color = color;
      this.componentIndex = componentIndex;
      this.howMuch = howMuch;
      this.factor = factor;
    }

    @Override
    public @NotNull List<@NotNull Object> getArgs() {
      return List.of(color, componentIndex, howMuch, factor);
    }

    @Override
    public @NotNull Color get() {
      float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
      float component = hsb[componentIndex];
      for (int i = 0; i < howMuch; i++) {
        component = MathUtil.clamp(factor * component, 0, 1);
        if (component == 0 || component == 1) break;
      }
      hsb[componentIndex] = component;
      return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }
  }

  private static @NotNull Color shiftHSBComponent(@NotNull Color color, int componentIndex, float shift) {
    Checks.checkIndex(componentIndex, 3);
    float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    hsb[componentIndex] = MathUtil.clamp(hsb[componentIndex] + shift, 0, 1);
    return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
  }

  public static @NotNull Color saturate(@NotNull Color color, int tones) {
    return new Saturate(color, tones).createColor(false);
  }

  private static class Saturate extends ColorMixture {
    @NotNull private final Color color;
    private final int tones;

    private Saturate(@NotNull Color color, int tones) {
      super("saturate");
      this.color = color;
      this.tones = tones;
    }

    @Override
    public @NotNull List<@NotNull Object> getArgs() {
      return List.of(color, tones);
    }

    @Override
    public @NotNull Color get() {
      final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
      float saturation = hsb[1];
      for (int i = 0; i < tones; i++) {
        saturation = Math.min(1, saturation * 1.1F);
        if (saturation == 1) break;
      }
      return Color.getHSBColor(hsb[0], saturation, hsb[2]);
    }
  }

  public static @NotNull Color desaturate(@NotNull Color color, int tones) {
    return new Desaturate(color, tones).createColor(false);
  }

  private static class Desaturate extends ColorMixture {
    @NotNull private final Color color;
    private final int tones;

    private Desaturate(@NotNull Color color, int tones) {
      super("desaturate");
      this.color = color;
      this.tones = tones;
    }

    @Override
    public @NotNull List<@NotNull Object> getArgs() {
      return List.of(color, tones);
    }

    @Override
    public @NotNull Color get() {
      final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
      float saturation = hsb[1];
      for (int i = 0; i < tones; i++) {
        saturation = Math.max(0, saturation / 1.1F);
        if (saturation == 0) break;
      }
      return Color.getHSBColor(hsb[0], saturation, hsb[2]);
    }
  }

  public static @NotNull Color dimmer(final @NotNull Color color) {
    return new Dimmer(color).createColor(true);
  }

  private static class Dimmer extends ColorMixture {
    @NotNull private final Color color;

    private Dimmer(@NotNull Color color) {
      super("dimmer");
      this.color = color;
    }

    @Override
    public @NotNull List<@NotNull Object> getArgs() {
      return List.of(color);
    }

    @Override
    public @NotNull Color get() {
      float[] rgb = color.getRGBColorComponents(null);

      float alpha = 0.80f;
      float rem = 1 - alpha;
      return new Color(rgb[0] * alpha + rem, rgb[1] * alpha + rem, rgb[2] * alpha + rem);
    }
  }

  private static int shift(int colorComponent, double d) {
    final int n = (int)(colorComponent * d);
    return n > 255 ? 255 : Math.max(n, 0);
  }

  public static @NotNull Color shift(final @NotNull Color c, final double d) {
    return new Shift(c, d).createColor(true);
  }

  private static class Shift extends ColorMixture {
    @NotNull private final Color color;
    private final double d;

    private Shift(@NotNull Color color, double d) {
      super("shift");
      this.color = color;
      this.d = d;
    }

    @Override
    public @NotNull List<@NotNull Object> getArgs() {
      return List.of(color, d);
    }

    @Override
    public @NotNull Color get() {
      return new Color(shift(color.getRed(), d), shift(color.getGreen(), d), shift(color.getBlue(), d), color.getAlpha());
    }
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
    return new ToAlpha(c, a).createColor(true);
  }

  private static class ToAlpha extends ColorMixture {
    @NotNull private final Color color;
    private final int alpha;

    private ToAlpha(@NotNull Color color, int alpha) {
      super("toAlpha");
      this.color = color;
      this.alpha = alpha;
    }

    @Override
    public @NotNull List<@NotNull Object> getArgs() {
      return List.of(color, alpha);
    }

    @Override
    public @NotNull Color get() {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
  }

  public static @NotNull String toHex(final @NotNull Color c) {
    return toHex(c, false);
  }

  public static @NotNull String toHex(final @NotNull Color c, final boolean withAlpha) {
    String result = withAlpha
                    ? Integer.toHexString(((c.getRGB() & 0xFFFFFF) << 8) /*RGB*/ + c.getAlpha())
                    : Integer.toHexString(c.getRGB() & 0xFFFFFF);
    int expectedLength = 3 * 2 + (withAlpha ? 2 : 0);
    return "0".repeat(expectedLength - result.length()) + result;
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
    return new Mix(c1, c2, balance).createColor(true);
  }

  private static class Mix extends ColorMixture {
    @NotNull private final Color color1;
    @NotNull private final Color color2;
    private final double balance;

    private final MixedColorProducer producer;

    private Mix(@NotNull Color color1, @NotNull Color color2, double balance) {
      super("mix");
      this.color1 = color1;
      this.color2 = color2;
      this.balance = balance;
      producer = new MixedColorProducer(color1, color2);
    }

    @Override
    public @NotNull List<@NotNull Object> getArgs() {
      return List.of(color1, color2, balance);
    }

    @Override
    public @NotNull Color get() {
      return producer.produce(balance);
    }
  }

  /**
   * Returns the color that is the result of having a foreground color on top of a background color
   */
  public static @NotNull Color alphaBlending(@NotNull Color foreground, @NotNull Color background) {
    return new AlphaBlending(foreground, background).createColor(false);
  }

  private static class AlphaBlending extends ColorMixture {
    @NotNull private final Color foreground;
    @NotNull private final Color background;

    private AlphaBlending(@NotNull Color foreground, @NotNull Color background) {
      super("alphaBlending");
      this.foreground = foreground;
      this.background = background;
    }

    @Override
    public @NotNull List<@NotNull Object> getArgs() {
      return List.of(foreground, background);
    }

    @Override
    public @NotNull Color get() {
      return new Color(
        alphaBlendingComponent(foreground.getRed(), foreground.getAlpha(), background.getRed(), background.getAlpha()),
        alphaBlendingComponent(foreground.getGreen(), foreground.getAlpha(), background.getGreen(), background.getAlpha()),
        alphaBlendingComponent(foreground.getBlue(), foreground.getAlpha(), background.getBlue(), background.getAlpha()),
        (255 * 255 - (255 - background.getAlpha()) * (255 - foreground.getAlpha())) / 255);
    }
  }

  private static int alphaBlendingComponent(int foregroundComponent,
                                            int foregroundAlpha,
                                            int backgroundComponent,
                                            int backgroundAlpha) {

    int denominator = 255 * 255 - (255 - foregroundAlpha) * (255 - backgroundAlpha);
    if (denominator == 0) {
      return 0;
    }
    return (255 * foregroundAlpha * foregroundComponent + (255 - foregroundAlpha) * backgroundAlpha * backgroundComponent) / denominator;
  }

  /**
   * Can be useful in case if you need to simulate transparency of the text.
   *
   * @param value coefficient of blend normalized 0..1
   * @return the mixed color of bg and fg with given coefficient.
   */
  public static @NotNull Color blendColorsInRgb(@NotNull Color bg, @NotNull Color fg, double value) {
    return new BlendColorsInRgb(bg, fg, value).createColor(false);
  }

  private static class BlendColorsInRgb extends ColorMixture {
    @NotNull private final Color background;
    @NotNull private final Color foreground;
    private final double value;

    private BlendColorsInRgb(@NotNull Color background, @NotNull Color foreground, double value) {
      super("colorBlendInRgb");
      this.background = background;
      this.foreground = foreground;
      this.value = value;
    }

    @Override
    public @NotNull List<@NotNull Object> getArgs() {
      return List.of(background, foreground, value);
    }

    @Override
    public @NotNull Color get() {
      Color bg = background;
      Color fg = foreground;
      int red = blendRgb(bg.getRed(), fg.getRed(), value);
      int green = blendRgb(bg.getGreen(), fg.getGreen(), value);
      int blue = blendRgb(bg.getBlue(), fg.getBlue(), value);
      return new Color(MathUtil.clamp(red, 0, 255),
                       MathUtil.clamp(green, 0, 255),
                       MathUtil.clamp(blue, 0, 255));
    }
  }

  /**
   * @param bg    background color value normalized 0..255
   * @param fg    foreground color value normalized 0..255
   * @param value coefficient of blend normalized 0..1
   * @return interpolation of bg and fg values with given coefficient normalized to 0..255
   */
  private static int blendRgb(int bg, int fg, double value) {
    return (int)Math.round(Math.sqrt(bg * bg * (1 - value) + fg * fg * value));
  }

  /**
   * Returns the color that, placed underneath the colors background and foreground, would result in the worst contrast
   */
  public static @NotNull Color worstContrastColor(@NotNull Color foreground, @NotNull Color background) {
    int backgroundAlpha = background.getAlpha();
    int r = worstContrastComponent(foreground.getRed(), background.getRed(), backgroundAlpha);
    int g = worstContrastComponent(foreground.getGreen(), background.getGreen(), backgroundAlpha);
    int b = worstContrastComponent(foreground.getBlue(), background.getBlue(), backgroundAlpha);
    return new Color(r, g, b);
  }

  private static int worstContrastComponent(int foregroundComponent, int backgroundComponent, int backgroundAlpha) {
    if (backgroundAlpha == 255) {
      // Irrelevant since background is completely opaque in this case
      return 0;
    }
    int component = (255 * foregroundComponent - backgroundAlpha * backgroundComponent) / (255 - backgroundAlpha);

    return Math.max(0, Math.min(component, 255));
  }

  /**
   * Provides the contrast ratio between two colors. For general text, the minimum recommended value is 7
   * For large text, the recommended minimum value is 4.5
   * <a href="http://www.w3.org/TR/WCAG20/#contrast-ratiodef">Source</a>
   */
  public static double calculateContrastRatio(@NotNull Color color1, @NotNull Color color2) {
    double color1Luminance = calculateColorLuminance(color1);
    double color2Luminance = calculateColorLuminance(color2);
    return (Math.max(color1Luminance, color2Luminance) + 0.05) / (Math.min(color2Luminance, color1Luminance) + 0.05);
  }

  private static double calculateColorLuminance(@NotNull Color color) {
    return calculateLuminanceContribution(color.getRed() / 255.0) * 0.2126 +
           calculateLuminanceContribution(color.getGreen() / 255.0) * 0.7152 +
           calculateLuminanceContribution(color.getBlue() / 255.0) * 0.0722;
  }

  private static double calculateLuminanceContribution(double colorValue) {
    if (colorValue <= 0.03928) {
      return colorValue / 12.92;
    }
    return Math.pow(((colorValue + 0.055) / 1.055), 2.4);
  }

  /**
   * taken from: http://www.compuphase.com/cmetric.htm
   */
  public static double getColorDistance(Color c1, Color c2) {
    double rmean = (c1.getRed() + c2.getRed()) / 2.0;
    int r = c1.getRed() - c2.getRed();
    int g = c1.getGreen() - c2.getGreen();
    int b = c1.getBlue() - c2.getBlue();
    double weightR = 2 + rmean / 256;
    double weightG = 4.0;
    double weightB = 2 + (255 - rmean) / 256;
    return Math.sqrt(weightR * r * r + weightG * g * g + weightB * b * b);
  }

  public static @NotNull Color faded(@NotNull Color color) {
    return withAlpha(color, 0.45f);
  }

  @ApiStatus.Experimental
  public static @NotNull Color editorFaded(@NotNull Color color, boolean darkEditor) {
    /*
      For my taste the optimal alpha depends on the theme:
      - For dark themes it is 0.6; if lower the text is difficult to read.
      - For light themes it is 0.5; if higher it is not clear that the text is semi-transparent.
    */

    return withAlpha(color, darkEditor ? 0.6f : 0.5f);
  }
}
