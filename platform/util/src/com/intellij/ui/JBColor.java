/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  public JBColor(int rgb, int darkRGB) {
    this(new Color(rgb), new Color(darkRGB));
  }

  public JBColor(Color regular, Color dark) {
    super(regular.getRGB(), regular.getAlpha() != 255);
    darkColor = dark;
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    DARK = UIUtil.isUnderDarcula(); //Double check. Sometimes DARK != isDarcula() after dialogs appear on splash screen
  }

  public static void setDark(boolean dark) {
    DARK = dark;
  }

  Color getDarkVariant() {
    return darkColor;
  }

  @Override
  public int getRed() {
    return DARK ? getDarkVariant().getRed() : super.getRed();
  }

  @Override
  public int getGreen() {
    return DARK ? getDarkVariant().getGreen() : super.getGreen();
  }

  @Override
  public int getBlue() {
    return DARK ? getDarkVariant().getBlue() : super.getBlue();
  }

  @Override
  public int getAlpha() {
    return DARK ? getDarkVariant().getAlpha() : super.getAlpha();
  }

  @Override
  public int getRGB() {
    return DARK ? getDarkVariant().getRGB() : super.getRGB();
  }

  @Override
  public Color brighter() {
    return new JBColor(super.brighter(), getDarkVariant().brighter());
  }

  @Override
  public Color darker() {
    return new JBColor(super.darker(), getDarkVariant().darker());
  }

  @Override
  public int hashCode() {
    return DARK ? getDarkVariant().hashCode() : super.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return DARK ? getDarkVariant().equals(obj) : super.equals(obj);
  }

  @Override
  public String toString() {
    return DARK ? getDarkVariant().toString() : super.toString();
  }

  @Override
  public float[] getRGBComponents(float[] compArray) {
    return DARK ? getDarkVariant().getRGBComponents(compArray) : super.getRGBComponents(compArray);
  }

  @Override
  public float[] getRGBColorComponents(float[] compArray) {
    return DARK ? getDarkVariant().getRGBColorComponents(compArray) : super.getRGBComponents(compArray);
  }

  @Override
  public float[] getComponents(float[] compArray) {
    return DARK ? getDarkVariant().getComponents(compArray) : super.getComponents(compArray);
  }

  @Override
  public float[] getColorComponents(float[] compArray) {
    return DARK ? getDarkVariant().getColorComponents(compArray) : super.getColorComponents(compArray);
  }

  @Override
  public float[] getComponents(ColorSpace cspace, float[] compArray) {
    return DARK ? getDarkVariant().getComponents(cspace, compArray) : super.getComponents(cspace, compArray);
  }

  @Override
  public float[] getColorComponents(ColorSpace cspace, float[] compArray) {
    return DARK ? getDarkVariant().getColorComponents(cspace, compArray) : super.getColorComponents(cspace, compArray);
  }

  @Override
  public ColorSpace getColorSpace() {
    return DARK ? getDarkVariant().getColorSpace() : super.getColorSpace();
  }

  @Override
  public synchronized PaintContext createContext(ColorModel cm, Rectangle r, Rectangle2D r2d, AffineTransform xform, RenderingHints hints) {
    return DARK ? getDarkVariant().createContext(cm, r, r2d, xform, hints) : super.createContext(cm, r, r2d, xform, hints);
  }

  @Override
  public int getTransparency() {
    return DARK ? getDarkVariant().getTransparency() : super.getTransparency();
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

  public static final JBColor black = new JBColor(Color.black, UIUtil.getListForeground()) {
    @Override
    Color getDarkVariant() {
      return UIUtil.getListForeground();
    }
  };
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

  public static Color foreground() {return UIUtil.getLabelForeground();}

  public static Color background() {return UIUtil.getListBackground();}
}
