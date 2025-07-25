// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.NotNullProducer;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.ui.ComparableColor;
import com.intellij.util.ui.JBUI.CurrentTheme;
import com.intellij.util.ui.PresentableColor;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.intellij.ui.ColorMixture.ENABLE_RUNTIME_COLOR_MIXTURE_WRAPPER_OPTION;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class JBColor extends Color implements PresentableColor, ComparableColor {
  public static final Color PanelBackground = new JBColor("Panel.background", new Color(0xffffff));

  // do not use method reference here - StartupUiUtil class should be loaded lazy
  private static final SynchronizedClearableLazy<Boolean> DARK = new SynchronizedClearableLazy<>(() -> StartupUiUtil.INSTANCE.isDarkTheme());

  private static final Color NAMED_COLOR_FALLBACK_MARKER = marker("NAMED_COLOR_FALLBACK_MARKER");

  private final String name;
  private final Color darkColor;
  private final Color defaultColor;
  private final Supplier<? extends Color> func;

  public JBColor(int rgb, int darkRGB) {
    this(new Color(rgb, (rgb & 0xff000000) != 0), new Color(darkRGB, (rgb & 0xff000000) != 0));
  }

  public JBColor(@NotNull Color regular, @NotNull Color dark) {
    super(regular.getRGB(), regular.getAlpha() != 255);
    name = null;
    defaultColor = null;
    darkColor = dark;
    func = null;
  }

  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  protected JBColor(@NotNull Supplier<? extends @NotNull Color> function) {
    super(0);
    name = null;
    defaultColor = null;
    darkColor = null;
    func = function;
  }

  /**
   * @deprecated Use {@link #lazy(Supplier)}
   */
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  @Deprecated
  public JBColor(@NotNull NotNullProducer<? extends Color> function) {
    super(0);
    name = null;
    defaultColor = null;
    darkColor = null;
    func = function::produce;
  }

  public JBColor(@NotNull String name, @Nullable Color defaultColor) {
    super(0);
    this.name = name;
    this.defaultColor = defaultColor;
    darkColor = null;
    func = null;
  }

  public static @NotNull JBColor lazy(@NotNull Supplier<? extends @NotNull Color> supplier) {
    return new JBColor(supplier);
  }

  public static @NotNull Color marker(@NotNull String name) {
    return new JBColor((Supplier<? extends Color>)() -> {
      throw new AssertionError(name);
    }) {
      @Override
      public boolean equals(Object obj) {
        return this == obj;
      }

      @Override
      public int hashCode() {
        return System.identityHashCode(this);
      }

      @Override
      public String getPresentableName() {
        return "Marker: " + name;
      }

      @Override
      public String toString() {
        return name;
      }
    };
  }

  public static @NotNull JBColor namedColor(@NonNls @NotNull String propertyName, int defaultValueRGB) {
    return namedColor(propertyName, new Color(defaultValueRGB));
  }

  public static @NotNull JBColor namedColor(@NonNls @NotNull String propertyName, int defaultValueRGB, int darkValueRGB) {
    return namedColor(propertyName, new JBColor(defaultValueRGB, darkValueRGB));
  }

  public static @NotNull JBColor namedColor(@NonNls @NotNull String propertyName) {
    return namedColor(propertyName, NAMED_COLOR_FALLBACK_MARKER);
  }

  public static JBColor namedColorOrNull(@NonNls @NotNull String propertyName) {
    JBColor color = new JBColor(propertyName, NAMED_COLOR_FALLBACK_MARKER);
    if (color.getColorOrNull() == null) return null;
    return color;
  }

  public static @NotNull JBColor namedColor(@NonNls @NotNull String propertyName, @NotNull Color defaultColor) {
    return new JBColor(propertyName, defaultColor);
  }

  private static @NotNull Color calculateColor(@NonNls @NotNull String name, @Nullable Color defaultColor) {
    Color color = calculateColorOrNull(name);
    if (color == null) {
      return defaultColor == NAMED_COLOR_FALLBACK_MARKER || defaultColor == null ? Gray.TRANSPARENT : defaultColor;
    }
    return color;
  }

  private static Color calculateColorOrNull(@NonNls @NotNull String propertyName) {
    Color color = UIManager.getColor(propertyName);
    if (color == null) {
      color = findPatternMatch(propertyName);
    }
    if (UIManager.get(propertyName) == null && Registry.is("ide.save.missing.jb.colors", false)) {
      return _saveAndReturnColor(propertyName, color == null ? Gray.TRANSPARENT : color);
    }
    return color;
  }

  // Let's find if namedColor can be overridden by *.propertyName rule in ui theme and apply it
  // We need to cache calculated results. Cache and rules will be reset after LaF change
  private static Color findPatternMatch(@NotNull String name) {
    Object value = UIManager.get("*");

    if (value instanceof Map<?, ?> map) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Map<String, Color> cache = (Map)UIManager.get("*cache");
      if (cache != null && cache.containsKey(name)) {
        Color cached = cache.get(name);
        return cached == CACHED_NULL ? null : cached;
      }
      Color color = null;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() instanceof String && name.endsWith((String)entry.getKey())) {
          Object result = map.get(entry.getKey());
          if (result instanceof Color) {
            color = (Color)result;
            break;
          }
        }
      }
      if (cache != null) {
        cache.put(name, color == null ? CACHED_NULL : color);
      }
      return color;
    }
    return null;
  }

  private static final Color CACHED_NULL = marker("CACHED_NULL");

  public static void setDark(boolean dark) {
    DARK.setValue(dark);
  }

  public static boolean isBright() {
    return !DARK.getValue();
  }

  @ApiStatus.Internal
  //For CodeWithMe only
  public Color getDarkVariant() {
    return darkColor;
  }

  @NotNull
  Color getColor() {
    if (func != null) {
      return func.get();
    }
    if (name != null) {
      return calculateColor(name, defaultColor);
    }

    return DARK.getValue() ? getDarkVariant() : this;
  }

  Color getColorOrNull() {
    if (func != null) {
      return func.get();
    }
    if (name != null) {
      return calculateColorOrNull(name);
    }

    return DARK.getValue() ? getDarkVariant() : this;
  }

  @ApiStatus.Internal
  public @Nullable String getName() {
    return name;
  }

  @Override
  @ApiStatus.Internal
  public @NlsSafe @Nullable String getPresentableName() {
    if (name != null) return name;
    if (func != null) {
      if (func instanceof PresentableColor presentableFun) {
        return "Lazy: " + presentableFun.getPresentableName();
      }
      Color color = func.get();
      return "Lazy: " + PresentableColor.toPresentableString(color) + " (" + func + ")";
    }
    if (defaultColor != null && darkColor != null) {
      return "Color pair: (" + PresentableColor.toPresentableString(defaultColor) + ", " +
             PresentableColor.toPresentableString(darkColor) + ")";
    }
    return null;
  }

  @ApiStatus.Internal
  public @Nullable Color getDefaultColor() {
    return defaultColor == NAMED_COLOR_FALLBACK_MARKER ? null : defaultColor;
  }

  @Override
  public int getRed() {
    final Color c = getColor();
    return c == this ? super.getRed() : c.getRed();
  }

  @Override
  public int getGreen() {
    final Color c = getColor();
    return c == this ? super.getGreen() : c.getGreen();
  }

  @Override
  public int getBlue() {
    final Color c = getColor();
    return c == this ? super.getBlue() : c.getBlue();
  }

  @Override
  public int getAlpha() {
    final Color c = getColor();
    return c == this ? super.getAlpha() : c.getAlpha();
  }

  @Override
  public int getRGB() {
    final Color c = getColor();
    return c == this ? super.getRGB() : c.getRGB();
  }

  @Override
  public @NotNull Color brighter() {
    if (SystemProperties.getBooleanProperty(ENABLE_RUNTIME_COLOR_MIXTURE_WRAPPER_OPTION, false)) {
      return new SwingTuneBrighter(this).createColor(true);
    }
    if (func != null) {
      return lazy(() -> func.get().brighter());
    }
    if (name != null) {
      return calculateColor(name, defaultColor).brighter();
    }

    return new JBColor(super.brighter(), getDarkVariant().brighter());
  }

  @Override
  public @NotNull Color darker() {
    if (SystemProperties.getBooleanProperty(ENABLE_RUNTIME_COLOR_MIXTURE_WRAPPER_OPTION, false)) {
      return new SwingTuneDarker(this).createColor(true);
    }
    if (func != null) {
      return lazy(() -> func.get().darker());
    }
    if (name != null) {
      return calculateColor(name, defaultColor).darker();
    }

    return new JBColor(super.darker(), getDarkVariant().darker());
  }

  @Override
  public int hashCode() {
    final Color c = getColor();
    return c == this ? super.hashCode() : c.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    final Color c = getColor();
    return c == this ? super.equals(obj) : c.equals(obj);
  }

  @Override
  @ApiStatus.Internal
  public boolean colorEquals(@NotNull ComparableColor otherColor) {
    if (otherColor.getClass() == this.getClass()) {
      JBColor other = (JBColor)otherColor;
      if (func != null) return ComparableColor.equalComparable(func, other.func);
      if (name != null) return name.equals(other.name);
      return UIUtil.equalColors(defaultColor, other.defaultColor) &&
             UIUtil.equalColors(darkColor, other.darkColor);
    }
    return false;
  }

  @Override
  @ApiStatus.Internal
  public int colorHashCode() {
    if (func != null) return ComparableColor.comparableHashCode(func);
    if (name != null) return name.hashCode();
    return UIUtil.colorHashCode(defaultColor) +
           31 * UIUtil.colorHashCode(darkColor);
  }

  @Override
  public String toString() {
    final Color c = getColor();
    return c == this ? super.toString() : c.toString();
  }

  @Override
  public float @NotNull [] getRGBComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getRGBComponents(compArray) : c.getRGBComponents(compArray);
  }

  @Override
  public float @NotNull [] getRGBColorComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getRGBComponents(compArray) : c.getRGBColorComponents(compArray);
  }

  @Override
  public float @NotNull [] getComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getComponents(compArray) : c.getComponents(compArray);
  }

  @Override
  public float @NotNull [] getColorComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getColorComponents(compArray) : c.getColorComponents(compArray);
  }

  @Override
  public float @NotNull [] getComponents(@NotNull ColorSpace colorSpace, float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getComponents(colorSpace, compArray) : c.getComponents(colorSpace, compArray);
  }

  @Override
  public float @NotNull [] getColorComponents(@NotNull ColorSpace colorSpace, float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getColorComponents(colorSpace, compArray) : c.getColorComponents(colorSpace, compArray);
  }

  @Override
  public @NotNull ColorSpace getColorSpace() {
    final Color c = getColor();
    return c == this ? super.getColorSpace() : c.getColorSpace();
  }

  @Override
  public synchronized @NotNull PaintContext createContext(ColorModel cm, Rectangle r, Rectangle2D r2d, AffineTransform affineTransform, RenderingHints hints) {
    final Color c = getColor();
    return c == this ? super.createContext(cm, r, r2d, affineTransform, hints) : c.createContext(cm, r, r2d, affineTransform, hints);
  }

  @Override
  public int getTransparency() {
    final Color c = getColor();
    return c == this ? super.getTransparency() : c.getTransparency();
  }

  public static final JBColor red = new JBColor(Color.red, DarculaColors.RED);
  public static final JBColor RED = red;

  public static final JBColor blue = new JBColor(Color.blue, DarculaColors.BLUE);
  public static final JBColor BLUE = blue;

  public static final JBColor white = new JBColor(Color.white, background());
  public static final JBColor WHITE = white;

  public static final JBColor black = new JBColor(Color.black, foreground());
  public static final JBColor BLACK = black;

  public static final JBColor gray = new JBColor(Gray._128, Gray._128);
  public static final JBColor GRAY = gray;

  public static final JBColor lightGray = new JBColor(Gray._192, Gray._64);
  public static final JBColor LIGHT_GRAY = lightGray;

  public static final JBColor darkGray = new JBColor(Gray._64, Gray._192);
  public static final JBColor DARK_GRAY = darkGray;

  public static final JBColor pink = new JBColor(Color.pink, Color.pink);
  public static final JBColor PINK = pink;

  public static final JBColor orange = new JBColor(Color.orange, new Color(159, 107, 0));
  public static final JBColor ORANGE = orange;

  public static final JBColor yellow = new JBColor(Color.yellow, new Color(138, 138, 0));
  public static final JBColor YELLOW = yellow;

  public static final JBColor green = new JBColor(Color.green, new Color(98, 150, 85));
  public static final JBColor GREEN = green;

  public static final Color magenta = new JBColor(Color.magenta, new Color(151, 118, 169));
  public static final Color MAGENTA = magenta;

  public static final Color cyan = new JBColor(Color.cyan, new Color(0, 137, 137));
  public static final Color CYAN = cyan;

  public static @NotNull Color foreground() {
    return namedColor("Label.foreground", new JBColor(Gray._0, Gray.xBB));
  }

  public static @NotNull Color background() {
    return lazy(() -> CurrentTheme.List.BACKGROUND);
  }

  public static @NotNull Color border() {
    return namedColor("Borders.color", new JBColor(Gray._192, Gray._50));
  }

  private static final Map<String, Color> defaultThemeColors = new HashMap<>();

  public static @NotNull Color get(final @NotNull String colorId, final @NotNull Color defaultColor) {
    return lazy(() -> {
      Color color = defaultThemeColors.get(colorId);
      if (color != null) {
        return color;
      }

      defaultThemeColors.put(colorId, defaultColor);
      return defaultColor;
    });
  }

  @SuppressWarnings("unused")
  private static void saveMissingColorInUIDefaults(String propertyName, Color color) {
    if (Registry.is("ide.save.missing.jb.colors", false)) {
      String key = propertyName + "!!!";
      if (UIManager.get(key) == null) {
        UIManager.put(key, color);
      }
    }
  }

  @ApiStatus.Internal
  private static Color _saveAndReturnColor(@NonNls @NotNull String propertyName, Color color) {
    String key = propertyName + "!!!";
    Object saved = UIManager.get(key);
    if (saved instanceof Color) {
      //in case a designer changed the key
      return (Color)saved;
    }
    UIManager.put(key, color);
    return color;
  }
}
