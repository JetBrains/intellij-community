// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.NotNullProducer;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.util.Map;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class JBColor extends Color {
  public final static Color PanelBackground = namedColor("Panel.background", 0xffffff);

  private static class Lazy {
    private static volatile boolean DARK = UIUtil.isUnderDarcula();
  }

  private final Color darkColor;
  private final NotNullProducer<? extends Color> func;

  public JBColor(int rgb, int darkRGB) {
    this(new Color(rgb), new Color(darkRGB));
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

  public static JBColor namedColor(@NotNull String propertyName, int defaultValueRGB) {
    return namedColor(propertyName, new Color(defaultValueRGB));
  }


  public static JBColor namedColor(@NotNull final String propertyName, @NotNull final Color defaultColor) {
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        Color color = notNull(UIManager.getColor(propertyName),
                              notNull(findPatternMatch(propertyName), defaultColor));
        if (UIManager.get(propertyName) == null) {
          UIManager.put(propertyName, color);
        }
        return color;
      }
    });
  }

  // Let's find if namedColor can be overridden by *.propertyName rule in ui theme and apply it
  // We need to cache calculated results. Cache and rules will be reset after LaF change
  private static Color findPatternMatch(String name) {
    Object value = UIManager.get("*");

    if (value instanceof Map) {
      Color color = null;
      Map<?,?> map = (Map<?, ?>)value;
      Object o = UIManager.get("*cache");
      if (! (o instanceof Map)) {
        o = new java.util.HashMap<String, Color>();
        UIManager.put("*cache", o);
      }
      Map<String,Color> cache = (Map)o;
      if (cache.containsKey(name)) {
        return cache.get(name);
      }
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() instanceof String && name.endsWith(((String)entry.getKey()))) {
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

  @NotNull
  @Deprecated
  public static Color link() {
    return JBUI.CurrentTheme.Link.linkColor();
  }

  @NotNull
  @Deprecated
  public static Color linkHover() {
    return JBUI.CurrentTheme.Link.linkHoverColor();
  }

  @NotNull
  @Deprecated
  public static Color linkPressed() {
    return JBUI.CurrentTheme.Link.linkPressedColor();
  }

  @NotNull
  @Deprecated
  public static Color linkVisited() {
    return JBUI.CurrentTheme.Link.linkVisitedColor();
  }

  public static void setDark(boolean dark) {
    Lazy.DARK = dark;
  }

  public static boolean isBright() {
    return !Lazy.DARK;
  }

  Color getDarkVariant() {
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
      return new JBColor(new NotNullProducer<Color>() {
        @NotNull
        @Override
        public Color produce() {
          return func.produce().brighter();
        }
      });
    }
    return new JBColor(super.brighter(), getDarkVariant().brighter());
  }

  @Override
  @NotNull
  public Color darker() {
    if (func != null) {
      return new JBColor(new NotNullProducer<Color>() {
        @NotNull
        @Override
        public Color produce() {
          return func.produce().darker();
        }
      });
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
  @NotNull
  public float[] getRGBComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getRGBComponents(compArray) : c.getRGBComponents(compArray);
  }

  @Override
  @NotNull
  public float[] getRGBColorComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getRGBComponents(compArray) : c.getRGBColorComponents(compArray);
  }

  @Override
  @NotNull
  public float[] getComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getComponents(compArray) : c.getComponents(compArray);
  }

  @Override
  @NotNull
  public float[] getColorComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getColorComponents(compArray) : c.getColorComponents(compArray);
  }

  @Override
  @NotNull
  public float[] getComponents(@NotNull ColorSpace cspace, float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getComponents(cspace, compArray) : c.getComponents(cspace, compArray);
  }

  @Override
  @NotNull
  public float[] getColorComponents(@NotNull ColorSpace cspace, float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getColorComponents(cspace, compArray) : c.getColorComponents(cspace, compArray);
  }

  @Override
  @NotNull
  public ColorSpace getColorSpace() {
    final Color c = getColor();
    return c == this ? super.getColorSpace() : c.getColorSpace();
  }

  @Override
  @NotNull
  public synchronized PaintContext createContext(ColorModel cm, Rectangle r, Rectangle2D r2d, AffineTransform xform, RenderingHints hints) {
    final Color c = getColor();
    return c == this ? super.createContext(cm, r, r2d, xform, hints) : c.createContext(cm, r, r2d, xform, hints);
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
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        return UIUtil.getLabelForeground();
      }
    });
  }

  @NotNull
  public static Color background() {
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        return UIUtil.getListBackground();
      }
    });
  }

  @NotNull
  public static Color border() {
    return namedColor("Borders.color", new JBColor(Gray._192, Gray._50));
  }

  private static final Map<String, Color> defaultThemeColors = new HashMap<String, Color>();

  @NotNull 
  public static Color get(@NotNull final String colorId, @NotNull final Color defaultColor) {
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        Color color = defaultThemeColors.get(colorId);
        if (color != null) {
          return color;
        }

        defaultThemeColors.put(colorId, defaultColor);
        return defaultColor;
      }
    });
  }
}
