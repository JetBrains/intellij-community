/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.util.ui.JBUI.Scaler;

import javax.swing.plaf.UIResource;
import java.awt.*;

import static java.lang.Math.ceil;

/**
 * @author Konstantin Bulenkov
 * @author tav
 */
public class JBDimension extends Dimension {
  protected Size2D size2D;
  private final MyScaler scaler = new MyScaler();

  private static class Size2D {
    double width;
    double height;

    Size2D(double width, double height) {
      this.width = width;
      this.height = height;
    }

    int intWidth() {
      return (int)ceil(width);
    }

    int intHeight() {
      return (int)ceil(height);
    }

    Size2D copy() {
      return new Size2D(width, height);
    }

    void set(double width, double height) {
      this.width = width;
      this.height = height;
    }
  }

  public JBDimension(int width, int height) {
    this(width, height, false);
  }

  public JBDimension(int width, int height, boolean preScaled) {
    this((double)width, (double)height, preScaled);
  }

  private JBDimension(double width, double height, boolean preScaled) {
    size2D = new Size2D(preScaled ? width : scale(width), preScaled ? height : scale(height));

    set(size2D);
  }

  private double scale(double size) {
    return Math.max(-1, JBUI.scale((float)size));
  }

  public static JBDimension create(Dimension from, boolean preScaled) {
    if (from instanceof JBDimension) {
      return ((JBDimension)from);
    }
    return new JBDimension(from.width, from.height, preScaled);
  }

  public static JBDimension create(Dimension from) {
    return create(from, false);
  }

  public JBDimensionUIResource asUIResource() {
    return new JBDimensionUIResource(this);
  }

  public static class JBDimensionUIResource extends JBDimension implements UIResource {
    public JBDimensionUIResource(JBDimension size) {
      super(0, 0);
      set(size.width, size.height);

      size2D = size.size2D.copy();
    }
  }

  public JBDimension withWidth(int width) {
    JBDimension size = new JBDimension(0, 0);
    size.size2D.set(scale(width), size2D.height);

    size.set(size.size2D.intWidth(), height);
    return size;
  }

  public JBDimension withHeight(int height) {
    JBDimension size = new JBDimension(0, 0);
    size.size2D.set(size2D.width, scale(height));

    size.set(width, size.size2D.intHeight());
    return size;
  }

  protected void set(int width, int height) {
    this.width = width;
    this.height = height;
  }

  protected void set(Size2D size2d) {
    set(size2d.intWidth(), size2d.intHeight());
  }

  /**
   * Updates the size according to current {@link JBUI.ScaleType#USR_SCALE} if necessary.
   * @return whether the size has been updated
   */
  public boolean update() {
    if (!scaler.needUpdate()) return false;

    size2D.set(scaler.scaleVal(size2D.width), scaler.scaleVal(size2D.height));

    set(size2D);

    scaler.update();
    return true;
  }

  /**
   * @return this JBDimension with updated size
   */
  public JBDimension size() {
    update();
    return this;
  }

  /**
   * @return new JBDimension with updated size
   */
  public JBDimension newSize() {
    update();
    return new JBDimension(size2D.width, size2D.height, true);
  }

  /**
   * @return updated width
   */
  public int width() {
    update();
    return width;
  }

  /**
   * @return updated height
   */
  public int height() {
    update();
    return height;
  }

  /**
   * @return updated double width
   */
  public double width2d() {
    update();
    return size2D.width;
  }

  /**
   * @return updated double height
   */
  public double height2d() {
    update();
    return size2D.height;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof JBDimension)) return false;

    JBDimension that = (JBDimension)obj;
    return size2D.equals(that.size2D);
  }
}

class MyScaler extends Scaler {
  @Override
  protected double currentScale() {
    return JBUI.scale(1f);
  }

  public boolean needUpdate() {
    return initialScale != JBUI.scale(1f);
  }

  public void update() {
    setPreScaled(true); // updates initialScale
  }
}
