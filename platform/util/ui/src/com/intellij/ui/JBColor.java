// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class JBColor extends Color {
  public static final Color PanelBackground = namedColor("Panel.background", 0xffffff);

  private static final class Lazy {
    private static volatile boolean DARK = StartupUiUtil.isUnderDarcula();
  }

  private final Color darkColor;
  private final NotNullProducer<? extends Color> func;

  public JBColor(int rgb, int darkRGB) {
    this(new Color(rgb, (rgb & 0xff000000) != 0), new Color(darkRGB, (rgb & 0xff000000) != 0));
  }

  public JBColor(@NotNull Color regular, @NotNull Color dark) {
    super(regular.getRGB(), regular.getAlpha() != 255);
    darkColor = dark;
    func = null;
  }

  public JBColor(@NotNull NotNullProducer<? extends Color> function) {
    super(0);
    darkColor = null;
    func = function;
  }

  @NotNull
  public static JBColor namedColor(@NonNls @NotNull String propertyName, int defaultValueRGB) {
    return namedColor(propertyName, new Color(defaultValueRGB));
  }

  @NotNull
  public static JBColor namedColor(@NonNls @NotNull String propertyName, int defaultValueRGB, int darkValueRGB) {
    return namedColor(propertyName, new JBColor(defaultValueRGB, darkValueRGB));
  }

  @NotNull
  public static JBColor namedColor(@NonNls @NotNull final String propertyName, @NotNull final Color defaultColor) {
    return new JBColor(() -> {
      Color color = notNull(UIManager.getColor(propertyName), () -> notNull(findPatternMatch(propertyName), defaultColor));
      if (UIManager.get(propertyName) == null) {
        if (Registry.is("ide.save.missing.jb.colors", false)) {
          return _saveAndReturnColor(propertyName, color);
        }
      }
      return color;
    });
  }

  // Let's find if namedColor can be overridden by *.propertyName rule in ui theme and apply it
  // We need to cache calculated results. Cache and rules will be reset after LaF change
  private static Color findPatternMatch(@NotNull String name) {
    Object value = UIManager.get("*");

    if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>)value;
      Object o = UIManager.get("*cache");
      if (!(o instanceof Map)) {
        o = new HashMap<String, Color>();
        UIManager.put("*cache", o);
      }
      @SuppressWarnings("unchecked")
      Map<String, Color> cache = (Map)o;
      if (cache.containsKey(name)) {
        return cache.get(name);
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
      cache.put(name, color);
      return color;
    }
    return null;
  }

  /**
   * @deprecated use {@link JBUI.CurrentTheme.Link.Foreground#ENABLED}
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static Color link() {
    //noinspection UnnecessaryFullyQualifiedName
    return com.intellij.util.ui.JBUI.CurrentTheme.Link.Foreground.ENABLED;
  }

  public static void setDark(boolean dark) {
    Lazy.DARK = dark;
  }

  public static boolean isBright() {
    return !Lazy.DARK;
  }

  @ApiStatus.Internal
  //For CodeWithMe only
  public Color getDarkVariant() {
    return darkColor;
  }

  @NotNull
  Color getColor() {
    return func != null ? func.produce() : Lazy.DARK ? getDarkVariant() : this;
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
  @NotNull
  public Color brighter() {
    if (func != null) {
      return new JBColor(() -> func.produce().brighter());
    }
    return new JBColor(super.brighter(), getDarkVariant().brighter());
  }

  @Override
  @NotNull
  public Color darker() {
    if (func != null) {
      return new JBColor(() -> func.produce().darker());
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
  @NotNull
  public ColorSpace getColorSpace() {
    final Color c = getColor();
    return c == this ? super.getColorSpace() : c.getColorSpace();
  }

  @Override
  @NotNull
  public synchronized PaintContext createContext(ColorModel cm, Rectangle r, Rectangle2D r2d, AffineTransform affineTransform, RenderingHints hints) {
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

  @NotNull
  public static Color foreground() {
    return new JBColor(UIUtil::getLabelForeground);
  }

  @NotNull
  public static Color background() {
    return new JBColor(UIUtil::getListBackground);
  }

  @NotNull
  public static Color border() {
    return namedColor("Borders.color", new JBColor(Gray._192, Gray._50));
  }

  private static final Map<String, Color> defaultThemeColors = new HashMap<>();

  @NotNull
  public static Color get(@NotNull final String colorId, @NotNull final Color defaultColor) {
    return new JBColor(() -> {
      Color color = defaultThemeColors.get(colorId);
      if (color != null) {
        return color;
      }

      defaultThemeColors.put(colorId, defaultColor);
      return defaultColor;
    });
  }

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
