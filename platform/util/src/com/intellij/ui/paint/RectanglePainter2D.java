// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.paint;

import com.intellij.ui.paint.LinePainter2D.StrokeType;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.ScaleContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ui.paint.PaintUtil.alignToInt;

/**
 * Draws or fills a rectangle with a stroke defined by {@link StrokeType}. The size of the rectangle is exactly
 * of the requested width/height (unlike in {@link Graphics#drawRect(int, int, int, int)}).
 * <p>
 * It's assumed that the {@link JBUI.ScaleType#USR_SCALE} factor is already applied to the values (given in the user space)
 * passed to the methods of this class. So the user scale factor is not taken into account.
 *
 * @author Sergey.Malenkov
 * @author tav
 */
public enum RectanglePainter2D implements RegionPainter2D<Double> {
  DRAW {
    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull Graphics2D g, double x, double y, double width, double height) {
      paint(g, x, y, width, height, null);
    }

    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull Graphics2D g, double x, double y, double width, double height, @Nullable Double arc) {
      paint(g, x, y, width, height, arc, StrokeType.INSIDE, 1);
    }

    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull Graphics2D g,
                      double x, double y, double width, double height,
                      @Nullable Double arc,
                      @NotNull StrokeType strokeType,
                      double strokeWidth)
    {
      paint(g, x, y, width, height, arc, strokeType, strokeWidth, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /**
     * Draws a rectangle.
     *
     * @param g the {@code Graphics2D} object to paint on
     * @param x x
     * @param y y
     * @param width width
     * @param height height
     * @param arc the arc of the rounding rectangle, or null
     * @param strokeType the stroke type
     * @param strokeWidth the stroke width
     * @param valueAA overrides current {@link RenderingHints#KEY_ANTIALIASING} to {@code valueAA},
     *                affecting a rounding rectangle only
     */
    @Override
    public void paint(@NotNull final Graphics2D g,
                      double x, double y, double width, double height,
                      @Nullable final Double arc,
                      @NotNull StrokeType strokeType,
                      double strokeWidth,
                      @NotNull Object valueAA)
    {
      if (width < 0 || height < 0) return;

      double sw = alignToInt(strokeWidth, g);
      double dsw = sw * 2;
      double sw_1 = 0, sw_2 = 0;
      double a_out = 0;

      if (width > dsw && height > dsw) {
        // align conforms to LinePainter2D
        x = alignToInt(x, g);
        y = alignToInt(y, g);

        if (strokeType == StrokeType.CENTERED) {
          double _sw = sw - 1;
          sw_1 = alignToInt(Math.max(_sw / 2, 0), g);
          //if (sw_1 * 2 < _sw) sw_1 = _sw - sw_1; // bias to left/top of the line
          sw_2 = Math.max(1 + (_sw - sw_1), 0);

          a_out = sw;
        }
        else if (strokeType == StrokeType.OUTSIDE) {
          a_out = dsw;
          sw_1 = sw;
          sw_2 = sw + 1; // add the pixel itself
        }

        double x_out = x - sw_1;
        double y_out = y - sw_1;
        double w_out = sw_1 + width + Math.max(sw_2 - 1, 0);
        double h_out = sw_1 + height + Math.max(sw_2 - 1, 0);

        final Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        if (arc != null) {
          path.append(new RoundRectangle2D.Double(x_out, y_out, w_out, h_out, arc + a_out, arc + a_out), false);
          path.append(new RoundRectangle2D.Double(x_out + sw, y_out + sw, w_out - dsw, h_out - dsw, arc - dsw, arc - dsw), false);
        }
        else {
          path.append(new Rectangle2D.Double(x_out, y_out, w_out, h_out), false);
          path.append(new Rectangle2D.Double(x_out + sw, y_out + sw, w_out - dsw, h_out - dsw), false);
        }
        PaintUtil.paintWithAA(g, arc != null ? valueAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT,
                              new Runnable() {
                                       @Override
                                       public void run() {
                                         g.fill(path);
                                       }
                                     });
      }
      else {
        FILL.paint(g, x, y, width, height, arc, strokeType, strokeWidth, valueAA);
      }
    }
  },

  FILL {
    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull Graphics2D g, double x, double y, double width, double height) {
      paint(g, x, y, width, height, null);
    }

    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull Graphics2D g, double x, double y, double width, double height, @Nullable Double arc) {
      paint(g, x, y, width, height, arc, StrokeType.INSIDE, 1);
    }

    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull Graphics2D g,
                      double x, double y, double width, double height,
                      @Nullable Double arc,
                      @NotNull StrokeType strokeType,
                      double strokeWidth)
    {
      paint(g, x, y, width, height, arc, strokeType, strokeWidth, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /**
     * Fills a rectangle.
     *
     * @param g the {@code Graphics2D} object to paint on
     * @param x x
     * @param y y
     * @param width width
     * @param height height
     * @param arc the arc of the rounding rectangle, or null
     * @param strokeType the stroke type
     * @param strokeWidth the stroke width
     * @param valueAA overrides current {@link RenderingHints#KEY_ANTIALIASING} to {@code valueAA},
     *                affecting a rounding rectangle only
     */
    @Override
    public void paint(@NotNull final Graphics2D g,
                      double x, double y, double width, double height,
                      @Nullable Double arc,
                      @NotNull StrokeType strokeType,
                      double strokeWidth,
                      @NotNull Object valueAA)
    {
      if (width < 0 || height < 0) return;

      double sw = alignToInt(strokeWidth, g);
      double dsw = sw * 2;
      double sw_1 = 0, sw_2 = 0;

      // align conforms to LinePainter2D
      x = alignToInt(x, g);
      y = alignToInt(y, g);

      if (strokeType == StrokeType.CENTERED) {
        double _sw = sw - 1;
        sw_1 = alignToInt(Math.max(_sw / 2, 0), g);
        //if (sw_1 * 2 < _sw) sw_1 = _sw - sw_1; // bias to left/top of the line
        sw_2 = Math.max(1 + (_sw - sw_1), 0);
      }
      else if (strokeType == StrokeType.OUTSIDE) {
        sw_1 = sw;
        sw_2 = sw + 1; // add the pixel itself
      }
      else if (strokeType == StrokeType.INSIDE) {
        dsw = 0;
      }

      double x_out = x - sw_1;
      double y_out = y - sw_1;
      double w_out = sw_1 + width + Math.max(sw_2 - 1, 0);
      double h_out = sw_1 + height + Math.max(sw_2 - 1, 0);

      final Shape rect = arc != null ?
                         new RoundRectangle2D.Double(x_out, y_out, w_out, h_out, arc + dsw, arc + dsw) :
                         new Rectangle2D.Double(x_out, y_out, w_out, h_out);

      PaintUtil.paintWithAA(g, arc != null ? valueAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT,
                            new Runnable() {
                                     @Override
                                     public void run() {
                                       g.fill(rect);
                                     }
                                   });
    }
  };

  /**
   * Returns the rectangle size which includes the stroke.
   *
   * @param g the graphics
   * @param size the size of the rectangle without the stroke
   * @param strokeType the stroke type
   * @param strokeWidth the stroke width
   * @return the size with the stroke
   */
  public static double getStrokedSize(@NotNull Graphics2D g, double size, StrokeType strokeType, double strokeWidth) {
    return getStrokedSize(ScaleContext.create(g), size, strokeType, strokeWidth);
  }

  /**
   * @see #getStrokedSize(Graphics2D, double, StrokeType, double)
   */
  public static double getStrokedSize(@NotNull ScaleContext ctx, double size, StrokeType strokeType, double strokeWidth) {
    if (strokeType == StrokeType.INSIDE) return size;
    if (strokeType == StrokeType.OUTSIDE) return size + strokeWidth * 2;

    double _sw = strokeWidth - 1;
    double sw_1 = alignToInt(Math.max(_sw / 2, 0), ctx, RoundingMode.ROUND, null);
    double sw_2 = Math.max(1 + (_sw - sw_1), 0);
    return sw_1 + size + Math.max(sw_2 - 1, 0);
  }
}
