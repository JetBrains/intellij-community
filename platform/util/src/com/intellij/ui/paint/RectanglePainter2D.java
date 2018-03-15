// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.paint;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.paint.LinePainter2D.Align;
import com.intellij.ui.paint.LinePainter2D.StrokeType;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.ScaleContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumSet;

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
    @Override
    public void paint(Graphics2D g, double x, double y, double width, double height, @Nullable Double arc) {
      paint(g, x, y, width, height, arc, StrokeType.INSIDE, 1, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull Graphics2D g, double x, double y, double width, double height) {
      paint(g, x, y, width, height, null, StrokeType.INSIDE, 1, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull final Graphics2D g,
                      Rectangle2D rect,
                      @Nullable Double arc,
                      @NotNull StrokeType strokeType,
                      double strokeWidth,
                      @NotNull Object valueAA)
    {
      paint(g, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), arc, strokeType, strokeWidth, valueAA);
    }

    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull Graphics2D g,
                      double x, double y, double width, double height,
                      @NotNull StrokeType strokeType,
                      double strokeWidth)
    {
      paint(g, x, y, width, height, null, strokeType, strokeWidth, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
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
      double sw_1, sw_2;
      double a_out;

      if (width > dsw && height > dsw) {
        // align conforms to LinePainter2D
        x = alignToInt(x, g);
        y = alignToInt(y, g);

        Pair<Double, Double> strokeSplit = LinePainter2D.getStrokeSplit(ScaleContext.create(g), strokeType, sw, false);
        sw_1 = strokeSplit.first;
        sw_2 = strokeSplit.second;

        a_out = strokeType == StrokeType.CENTERED ? sw : strokeType == StrokeType.OUTSIDE ? dsw : 0;

        double x_out = x - sw_1;
        double y_out = y - sw_1;
        double w_out = sw_1 + width + sw_2;
        double h_out = sw_1 + height + sw_2;

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
    @Override
    public void paint(Graphics2D g, double x, double y, double width, double height, @Nullable Double arc) {
      paint(g, x, y, width, height, arc, StrokeType.INSIDE, 1, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull Graphics2D g, double x, double y, double width, double height) {
      paint(g, x, y, width, height, null, StrokeType.INSIDE, 1, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull final Graphics2D g,
                      Rectangle2D rect,
                      @Nullable Double arc,
                      @NotNull StrokeType strokeType,
                      double strokeWidth,
                      @NotNull Object valueAA)
    {
      paint(g, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), arc, strokeType, strokeWidth, valueAA);
    }

    /**
     * @see #paint(Graphics2D, double, double, double, double, Double, StrokeType, double, Object)
     */
    @Override
    public void paint(@NotNull Graphics2D g,
                      double x, double y, double width, double height,
                      @NotNull StrokeType strokeType,
                      double strokeWidth)
    {
      paint(g, x, y, width, height, null, strokeType, strokeWidth, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
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
      double sw_1, sw_2;

      // align conforms to LinePainter2D
      x = alignToInt(x, g);
      y = alignToInt(y, g);

      Pair<Double, Double> strokeSplit = LinePainter2D.getStrokeSplit(ScaleContext.create(g), strokeType, sw, false);
      sw_1 = strokeSplit.first;
      sw_2 = strokeSplit.second;

      if (strokeType == StrokeType.INSIDE) dsw = 0;

      double x_out = x - sw_1;
      double y_out = y - sw_1;
      double w_out = sw_1 + width + sw_2;
      double h_out = sw_1 + height + sw_2;

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
   * Aligns the rectangle relative to the provided {@code x, y} according to the provided {@code align}.
   * If {@code align} contains {@code CENTER_X}, the provided {@code x} is treated as the x center.
   * If {@code align} contains {@code CENTER_Y}, the provided {@code y} is treated as the y center.
   * Otherwise, x and/or y is not changed.
   * <p>
   * As the center x (y) coordinate it's expected either a value equal to integer in the device space,
   * in which case the {@code prefWidth (prefHeight)} is adjusted to a closed even value in the device space,
   * or a value b/w two integers in the device space, in which case the {@code prefWidth (prefHeight)} is adjusted
   * to a closed odd value in the device space.
   *
   * @param g           the graphics
   * @param align       the align
   * @param x           x obeying {@code align}
   * @param y           y obeying {@code align}
   * @param prefWidth   the preferred width
   * @param prefHeight  the preferred height
   * @param strokeType  the stroke type
   * @param strokeWidth the stroke width
   * @return the rectangle with aligned coordinates and size with adjusted parity
   */
  public static @NotNull Rectangle2D align(@NotNull Graphics2D g,
                                           @NotNull EnumSet<Align> align,
                                           double x, double y, double prefWidth, double prefHeight,
                                           @NotNull StrokeType strokeType, double strokeWidth)
  {
    if (align.contains(Align.CENTER_X) && prefWidth >= strokeWidth * 2) {
      Pair<Double, Double> p = LinePainter2D.alignSizeXY(g, x, prefWidth, strokeType, strokeWidth, true);
      x = p.first;
      prefWidth = p.second;
    }
    if (align.contains(Align.CENTER_Y) && prefHeight >= strokeWidth * 2) {
      Pair<Double, Double> p = LinePainter2D.alignSizeXY(g, y, prefHeight, strokeType, strokeWidth, true);
      y = p.first;
      prefHeight = p.second;
    }
    return new Rectangle2D.Double(x, y, prefWidth, prefHeight);
  }

  /**
   * Paints on the given {@link Graphics2D} object.
   * Renders to the given {@link Graphics2D} object.
   *
   * @param g the {@code Graphics2D} object to render to
   * @param x X position of the area to paint
   * @param y Y position of the area to paint
   * @param width width of the area to paint
   * @param g      the {@code Graphics2D} object to render to
   * @param x      X position of the area to paint
   * @param y      Y position of the area to paint
   * @param width  width of the area to paint
   * @param height height of the area to paint
   * @param object an optional configuration parameter
   * @param strokeType the stroke type
   * @param strokeWidth the stroke width
   * @param valueAA overrides current {@link RenderingHints#KEY_ANTIALIASING} to {@code valueAA}
   */
  public abstract void paint(@NotNull Graphics2D g,
                             double x, double y, double width, double height,
                             @Nullable Double arc,
                             @NotNull StrokeType strokeType,
                             double strokeWidth,
                             @NotNull Object valueAA);

  public abstract void paint(@NotNull Graphics2D g,
                             double x, double y, double width, double height,
                             @NotNull StrokeType strokeType,
                             double strokeWidth);

  public abstract void paint(@NotNull Graphics2D g, double x, double y, double width, double height);

  public abstract void paint(@NotNull final Graphics2D g,
                             Rectangle2D rect,
                             @Nullable Double arc,
                             @NotNull StrokeType strokeType,
                             double strokeWidth,
                             @NotNull Object valueAA);
}
