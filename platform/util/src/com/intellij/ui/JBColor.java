// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class JBColor extends Color {

  private static volatile boolean DARK = UIUtil.isUnderDarcula();

  private final Color darkColor;
  private final NotNullProducer<Color> func;

  public JBColor(int rgb, int darkRGB) {
    this(new Color(rgb), new Color(darkRGB));
  }

  public JBColor(Color regular, Color dark) {
    super(regular.getRGB(), regular.getAlpha() != 255);
    darkColor = dark;
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    DARK = UIUtil.isUnderDarcula(); //Double check. Sometimes DARK != isDarcula() after dialogs appear on splash screen
    func = null;
  }

  public JBColor(NotNullProducer<Color> function) {
    super(0);
    darkColor = null;
    func = function;
  }

  public static Color link() {
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        Color linkColor = UIManager.getColor("link.foreground");
        return linkColor == null ? new Color(0x589df6) : linkColor;
      }
    });
  }

  public static Color linkHover() {
    Color hoverColor = UIManager.getColor("link.hover.foreground");
    return hoverColor == null ? link() : hoverColor;
  }

  public static Color linkPressed() {
    Color pressedColor = UIManager.getColor("link.pressed.foreground");
    return pressedColor == null ? new JBColor(0xf00000, 0xba6f25) : pressedColor;
  }

  public static Color linkVisited() {
    Color visitedColor = UIManager.getColor("link.visited.foreground");
    return visitedColor == null ? new JBColor(0x800080, 0x9776a9) : visitedColor;
  }

  public static void setDark(boolean dark) {
    DARK = dark;
  }

  public static boolean isBright() {
    return !DARK;
  }

  Color getDarkVariant() {
    return darkColor;
  }

  Color getColor() {
    if (func != null) {
      return func.produce();
    } else {
      return DARK ? getDarkVariant() : this;
    }
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
  public float[] getRGBComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getRGBComponents(compArray) : c.getRGBComponents(compArray);
  }

  @Override
  public float[] getRGBColorComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getRGBComponents(compArray) : c.getRGBColorComponents(compArray);
  }

  @Override
  public float[] getComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getComponents(compArray) : c.getComponents(compArray);
  }

  @Override
  public float[] getColorComponents(float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getColorComponents(compArray) : c.getColorComponents(compArray);
  }

  @Override
  public float[] getComponents(ColorSpace cspace, float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getComponents(cspace, compArray) : c.getComponents(cspace, compArray);
  }

  @Override
  public float[] getColorComponents(ColorSpace cspace, float[] compArray) {
    final Color c = getColor();
    return c == this ? super.getColorComponents(cspace, compArray) : c.getColorComponents(cspace, compArray);
  }

  @Override
  public ColorSpace getColorSpace() {
    final Color c = getColor();
    return c == this ? super.getColorSpace() : c.getColorSpace();
  }

  @Override
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

  public static final JBColor white = new JBColor(Color.white, UIUtil.getListBackground()) {
    @Override
    Color getDarkVariant() {
      return UIUtil.getListBackground();
    }
  };
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

  public static Color foreground() {
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        return UIUtil.getLabelForeground();
      }
    });
  }

  public static Color background() {
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        return UIUtil.getListBackground();
      }
    });
  }

  public static Color border() {
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        //noinspection deprecation
        return UIUtil.getBorderColor();
      }
    });
  }
}
