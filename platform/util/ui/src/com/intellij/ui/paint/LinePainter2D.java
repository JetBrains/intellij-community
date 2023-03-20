// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.paint;

import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.paint.PaintUtil.ParityMode;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleType;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.EnumSet;

/**
 * Draws a line with a stroke defined by {@link StrokeType}, if the graphics stroke is {@link BasicStroke}),
 * otherwise defaults to {@code Graphics2D.draw(Line2D.Double)}.
 * <p>
 * It's assumed that the {@link ScaleType#USR_SCALE} factor is already applied to the values (given in the user space)
 * passed to the methods of this class. So the user scale factor is not taken into account.
 *
 * @author tav
 */
public final class LinePainter2D {
  /**
   * Defines the way the stroke is painted relative to the vector [x1, y1] -> [x2, y2].
   */
  public enum StrokeType {
    /**
     * The stroke is painted with the line in the center, caps butt.
     */
    CENTERED,
    /**
     * The stroke is painted with the line in the center, caps square.
     */
    CENTERED_CAPS_SQUARE,
    /**
     * The stroke is painted on the right side of the vector if the angle is in (-PI/2, PI/2]
     * and on the left side of the vector if the angle is in (-PI, -PI/2] or (PI/2, PI].
     */
    INSIDE,
    /**
     * The stroke is painted opposite to INSIDE.
     */
    OUTSIDE
  }

  /**
   * A enum bit in a flag which defines alignment for a line or a rectangle.
   */
  public enum Align {
    /**
     * Align by a provided center x.
     */
    CENTER_X,
    /**
     * Align by a provided center y.
     */
    CENTER_Y
  }

  /**
   * @see #paint(Graphics2D, double, double, double, double, StrokeType, double, Object)
   */
  public static void paint(@NotNull Graphics2D g, double x1, double y1, double x2, double y2) {
    double sw = g.getStroke() instanceof BasicStroke ? ((BasicStroke)g.getStroke()).getLineWidth() : 1;
    paint(g, x1, y1, x2, y2, StrokeType.INSIDE, sw);
  }

  /**
   * @see #paint(Graphics2D, double, double, double, double, StrokeType, double, Object)
   */
  public static void paint(@NotNull Graphics2D g,
                           double x1, double y1, double x2, double y2,
                           @NotNull StrokeType strokeType,
                           double strokeWidth) {
    paint(g, x1, y1, x2, y2, strokeType, strokeWidth, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
  }

  /**
   * @see #paint(Graphics2D, double, double, double, double, StrokeType, double, Object)
   */
  public static void paint(@NotNull final Graphics2D g,
                           @NotNull Line2D line,
                           @NotNull StrokeType strokeType,
                           double strokeWidth,
                           @NotNull Object valueAA) {
    paint(g, line.getX1(), line.getY1(), line.getX2(), line.getY2(), strokeType, strokeWidth, valueAA);
  }

  /**
   * Draws a line.
   *
   * @param g           the graphics to paint on
   * @param x1          x1
   * @param y1          y1
   * @param x2          x2
   * @param y2          y2
   * @param strokeType  the stroke type
   * @param strokeWidth the stroke width
   * @param valueAA     overrides current {@link RenderingHints#KEY_ANTIALIASING} to {@code valueAA}
   */
  public static void paint(@NotNull final Graphics2D g,
                           double x1, double y1, double x2, double y2,
                           @NotNull StrokeType strokeType,
                           double strokeWidth,
                           @NotNull Object valueAA) {
    boolean horizontal = y1 == y2;
    boolean vertical = x1 == x2;
    boolean straight = horizontal || vertical;
    boolean centered = strokeType == StrokeType.CENTERED || strokeType == StrokeType.CENTERED_CAPS_SQUARE;
    boolean thickStroke = PaintUtil.devValue(strokeWidth, g) > 1;

    if (g.getStroke() instanceof BasicStroke && (straight || thickStroke)) {
      double sw = PaintUtil.alignToInt(strokeWidth, g);

      boolean dot = horizontal && vertical;
      if (dot) y2 += strokeWidth - 1; // draw a dot as [strokeWidth x strokeWidth] vertical line

      double angle = dot ? 0 : Math.atan2(y1 - y2, x2 - x1); // invert the sign of Y-axis, directing it bottom to top
      double sin = dot ? 1 : Math.sin(angle);
      double cos = dot ? 0 : Math.cos(angle);
      if (straight && !dot) {
        sin = Math.abs(sin);
        cos = Math.abs(cos);
      }

      // caps offsets
      double capy_1 = 0;
      double capy_2 = 0;
      double capx_1 = 0;
      double capx_2 = 0;
      if (strokeType == StrokeType.CENTERED_CAPS_SQUARE) {
        // the caps repeat the stroke split logic
        double[] strokeSplit = getStrokeSplit(ScaleContext.create(g), strokeType, sw, false);
        double cap_1 = strokeSplit[0];
        double cap_2 = strokeSplit[1];
        double y_sign = straight ? 1 : -1;
        // cap_1 >= cap_2 for x1/y1 < x2/y2, otherwise swap for diagonal line
        capx_1 = (straight || x1 <= x2 ? cap_1 : cap_2) * cos;
        capx_2 = (straight || x1 <= x2 ? cap_2 : cap_1) * cos;
        capy_1 = (straight || y1 <= y2 ? cap_1 : cap_2) * sin * y_sign;
        capy_2 = (straight || y1 <= y2 ? cap_2 : cap_1) * sin * y_sign;
      }

      // below x/y are aligned to int dev pixels to conform to RectanglePainter2D edges painting

      if (vertical/*|| dot*/) {
        double y_min = Math.min(y1, y2);
        double y_max = Math.max(y1, y2);
        y1 = PaintUtil.alignToInt(y_min, g);
        y2 = y1 + y_max - y1 + 1;
        x1 = x2 = PaintUtil.alignToInt(x2, g);
      }
      else if (horizontal) {
        double x_min = Math.min(x1, x2);
        double x_max = Math.max(x1, x2);
        x1 = PaintUtil.alignToInt(x_min, g);
        x2 = x1 + x_max - x1 + 1;
        y1 = y2 = PaintUtil.alignToInt(y2, g);
      }
      else {
        x1 = PaintUtil.alignToInt(x1, g);
        x2 = PaintUtil.alignToInt(x2, g);
        y1 = PaintUtil.alignToInt(y1, g);
        y2 = PaintUtil.alignToInt(y2, g);

        if (Math.abs(angle) > Math.PI / 2) {
          sin *= -1;
          cos *= -1;
        }
      }
      // stroke offsets
      double swx_2 = 0;
      double swx_1 = 0;
      double swy_2 = 0;
      double swy_1 = 0;
      if (strokeType == StrokeType.OUTSIDE) {
        swx_1 = sw * sin;
        swy_1 = sw * cos;
      }
      else if (strokeType == StrokeType.INSIDE) {
        swx_2 = sw * sin;
        swy_2 = sw * cos;
      }
      else if (centered) {
        double sw_1;
        double sw_2;
        if (straight) {
          // stroke is painted around the center of the line pixel (or within the line pixel, when the stroke is smaller)
          double[] strokeSplit = getStrokeSplit(ScaleContext.create(g), strokeType, sw);
          sw_1 = strokeSplit[0];
          sw_2 = strokeSplit[1];
        }
        else {
          // stroke is painted around the line
          sw_1 = PaintUtil.alignToInt(Math.max(sw / 2, 0), g);
          sw_2 = Math.max(sw - sw_1, 0);
        }
        swx_1 = sw_1 * sin;
        swx_2 = sw_2 * sin;
        swy_1 = sw_1 * cos;
        swy_2 = sw_2 * cos;
      }
      final Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
      path.moveTo(x1 - swx_1 - capx_1, y1 - swy_1 - capy_1);
      path.lineTo(x2 - swx_1 + capx_2, y2 - swy_1 + capy_2);
      path.lineTo(x2 + swx_2 + capx_2, y2 + swy_2 + capy_2);
      path.lineTo(x1 + swx_2 - capx_1, y1 + swy_2 - capy_1);
      path.closePath();

      PaintUtil.paintWithAA(g, valueAA,
                            () -> g.fill(path));
    }
    else {
      final Line2D line = new Line2D.Double(x1, y1, x2, y2);
      PaintUtil.paintWithAA(g, valueAA,
                            () -> g.draw(line));
    }
  }

  /**
   * Fills a polygon.
   *
   * @param g           the graphics
   * @param xPoints     the x polygon points
   * @param yPoints     the y polygon points
   * @param nPoints     the number of points
   * @param strokeType  the stroke type
   * @param strokeWidth the stroke width
   * @param valueAA     overrides current {@link RenderingHints#KEY_ANTIALIASING} to {@code valueAA}
   */
  public static void fillPolygon(@NotNull final Graphics2D g,
                                 double @NotNull [] xPoints, double @NotNull [] yPoints,
                                 int nPoints,
                                 @NotNull StrokeType strokeType, double strokeWidth,
                                 @NotNull Object valueAA) {
    // [tav] todo: mind strokeWidth and strokeType
    final Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
    path.moveTo(xPoints[0], yPoints[0]);
    for (int p = 1; p < nPoints; p++) {
      path.lineTo(xPoints[p], yPoints[p]);
    }
    path.closePath();
    PaintUtil.paintWithAA(g, valueAA,
                          () -> g.fill(path));
  }

  /**
   * Draws a polygon.
   *
   * @param g           the graphics
   * @param xPoints     the x polygon points
   * @param yPoints     the y polygon points
   * @param nPoints     the number of points
   * @param strokeType  the stroke type
   * @param strokeWidth the stroke width
   * @param valueAA     overrides current {@link RenderingHints#KEY_ANTIALIASING} to {@code valueAA}
   */
  public static void paintPolygon(@NotNull Graphics2D g,
                                  double @NotNull [] xPoints, double @NotNull [] yPoints,
                                  int nPoints,
                                  @NotNull StrokeType strokeType, double strokeWidth,
                                  @NotNull Object valueAA) {
    boolean thickPixel = JreHiDpiUtil.isJreHiDPIEnabled() && PaintUtil.devValue(strokeWidth, g) > 1;
    boolean prevStraight = nPoints <= 1 || isStraightLine(xPoints, yPoints, nPoints, nPoints);

    for (int p = 0; p < nPoints; p++) {
      double x1 = xPoints[p];
      double y1 = yPoints[p];
      double x2 = xPoints[(p + 1) % nPoints];
      double y2 = yPoints[(p + 1) % nPoints];
      boolean thisStraight = x1 == x2 || y1 == y2;
      // [tav] todo: mind the angle, the strokeWidth and the strokeType
      if (thickPixel && !thisStraight) {
        if (prevStraight) {
          x1 += 0.5;
          y1 += 0.5;
        }
        if (isStraightLine(xPoints, yPoints, p + 1, nPoints)) {
          x2 += 0.5;
          y2 += 0.5;
        }
      }
      prevStraight = thisStraight;
      paint(g, x1, y1, x2, y2, strokeType, strokeWidth, valueAA);
    }
  }

  private static boolean isStraightLine(double @NotNull [] xPoints, double @NotNull [] yPoints, int nLine, int nPoints) {
    double x1 = xPoints[nLine % nPoints];
    double y1 = yPoints[nLine % nPoints];
    double x2 = xPoints[(nLine + 1) % nPoints];
    double y2 = yPoints[(nLine + 1) % nPoints];
    return x1 == x2 || y1 == y2;
  }

  /**
   * Aligns the line relative to the provided {@code x, y} according to the provided {@code align}.
   * If {@code align} contains {@code CENTER_X}, the provided {@code x} is treated as the x center.
   * If {@code align} contains {@code CENTER_Y}, the provided {@code y} is treated as the y center.
   * Otherwise, x and/or y is not changed.
   * <p>
   * As the center x (y) coordinate it's expected either a value equal to integer in the device space,
   * in which case the {@code prefLength} is adjusted to a closed even value in the device space,
   * or a value b/w two integers in the device space, in which case the {@code prefLength} is adjusted
   * to a closed odd value in the device space.
   *
   * @param g           the graphics
   * @param align       the align
   * @param x           x obeying {@code align}
   * @param y           y obeying {@code align}
   * @param prefLength  the preferred length of the line
   * @param vertical    whether the line is vertical or horizontal
   * @param strokeType  the stroke type
   * @param strokeWidth the stroke width
   * @return the line with aligned coordinates and length with adjusted parity
   */
  @NotNull
  public static Line2D align(@NotNull Graphics2D g,
                             @NotNull EnumSet<Align> align,
                             double x, double y, double prefLength, boolean vertical,
                             @NotNull StrokeType strokeType, double strokeWidth) {
    if (align.contains(Align.CENTER_X)) {
      if (vertical) {
        x = alignStrokeXY(g, x, strokeType, strokeWidth);
      }
      else {
        double[] p = alignSizeXY(g, x, prefLength, strokeType, strokeWidth, false);
        x = p[0];
        prefLength = p[1];
      }
    }
    if (align.contains(Align.CENTER_Y)) {
      if (!vertical) {
        y = alignStrokeXY(g, y, strokeType, strokeWidth);
      }
      else {
        double[] p = alignSizeXY(g, y, prefLength, strokeType, strokeWidth, false);
        y = p[0];
        prefLength = p[1];
      }
    }
    double x2 = !vertical ? x + prefLength - 1 : x;
    double y2 = vertical ? y + prefLength - 1 : y;
    return new Line2D.Double(x, y, x2, y2);
  }

  /**
   * Returns new x (y) of a line so that its stroke is centered in the provided x (y)
   */
  private static double alignStrokeXY(@NotNull Graphics2D g, double xy, @NotNull StrokeType strokeType, double strokeWidth) {
    double[] strokeSplit = getStrokeSplit(ScaleContext.create(g), strokeType, strokeWidth, false);
    return xy - strokeWidth / 2 + strokeSplit[0];
  }

  /**
   * Returns two-element array:
   * 1) new x (y), aligned along the provided center x (y)
   * 2) new size with adjusted parity
   */
  static double @NotNull [] alignSizeXY(@NotNull Graphics2D g,
                                        double xy, double prefSize,
                                        @NotNull StrokeType strokeType, double strokeWidth, boolean rectangle) {
    prefSize = PaintUtil.alignToInt(prefSize, g);
    // if xy is (close to) dev int the resulting size should be EVEN, otherwise ODD - to compensate the middle dev pixel
    double _xy = PaintUtil.alignToInt(xy + 0.000001, g, RoundingMode.FLOOR);
    ParityMode pm = Double.compare(_xy, xy) == 0 ? ParityMode.EVEN : ParityMode.ODD;
    double sw_1 = 0; // stroke split for rect, and caps for line with CENTERED_CAPS_SQUARE
    double sw_2 = 0;
    if (rectangle || strokeType == StrokeType.CENTERED_CAPS_SQUARE) {
      double[] strokeSplit = getStrokeSplit(ScaleContext.create(g), strokeType, strokeWidth, false);
      sw_1 = strokeSplit[0];
      sw_2 = strokeSplit[0];
    }
    double sizeWithStroke = sw_1 + prefSize + sw_2;
    if (PaintUtil.getParityMode(sizeWithStroke, g) != pm) {
      prefSize = PaintUtil.alignToInt(prefSize, g, ParityMode.invert(PaintUtil.getParityMode(prefSize, g)));
      sizeWithStroke = sw_1 + prefSize + sw_2;
    }
    _xy -= (pm == ParityMode.ODD ? sizeWithStroke - PaintUtil.devPixel(g) : sizeWithStroke) / 2 - sw_1;
    return new double[]{_xy, prefSize};
  }

  /**
   * @see #getStrokeCenter(Graphics2D, double, StrokeType, double)
   */
  private static double getStrokeCenter(@NotNull ScaleContext ctx, double xy, @NotNull StrokeType strokeType, double strokeWidth) {
    if (strokeType == StrokeType.INSIDE) {
      return xy + strokeWidth / 2;
    }
    if (strokeType == StrokeType.OUTSIDE) {
      return xy - strokeWidth / 2;
    }
    double[] strokeSplit = getStrokeSplit(ctx, strokeType, strokeWidth);
    return xy - strokeSplit[0] / 2 + strokeSplit[1] / 2;
  }

  /**
   * Returns the x (y) coordinate of the center of the stroke.
   *
   * @param g           the graphics
   * @param xy          the x or y coordinate
   * @param strokeType  the stroke type
   * @param strokeWidth the stroke width
   * @return the coordinate of the center of the stroke
   */
  public static double getStrokeCenter(@NotNull Graphics2D g, double xy, @NotNull StrokeType strokeType, double strokeWidth) {
    return getStrokeCenter(ScaleContext.create(g), xy, strokeType, strokeWidth);
  }

  /**
   * @see #getStrokeSplit(ScaleContext, StrokeType, double, boolean)
   */
  private static double @NotNull [] getStrokeSplit(@NotNull ScaleContext ctx, @NotNull StrokeType strokeType, double strokeWidth) {
    return getStrokeSplit(ctx, strokeType, strokeWidth, true);
  }

  /**
   * Returns left (top) and right (bottom) parts of the stroke which is to be painted along the line or rectangle side.
   *
   * @param ctx              the scale context
   * @param strokeType       the stroke type
   * @param strokeWidth      the stroke width
   * @param includeLinePixel should the line pixel (in user space) be included in the right (bottom) part of the split
   * @return two-element array with the stroke split
   */
  static double @NotNull [] getStrokeSplit(@NotNull ScaleContext ctx, @NotNull StrokeType strokeType, double strokeWidth, boolean includeLinePixel) {
    if (strokeType == StrokeType.OUTSIDE) {
      return new double[]{strokeWidth, strokeWidth};
    }
    if (strokeType == StrokeType.INSIDE) {
      return new double[]{0d, 0d};
    }
    // StrokeType.CENTERED || StrokeType.CENTERED_CAPS_SQUARE
    int linePixel = includeLinePixel ? 1 : 0;
    double _sw = strokeWidth - 1;
    double sw_1 = PaintUtil.alignToInt(Math.max(_sw / 2, 0), ctx, RoundingMode.ROUND, null);
    double sw_2 = Math.max(linePixel + (_sw - sw_1), 0);
    return new double[]{sw_1, sw_2};
  }
}