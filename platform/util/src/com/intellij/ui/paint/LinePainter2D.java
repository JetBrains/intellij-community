// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.paint;

import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import static com.intellij.ui.paint.PaintUtil.alignToInt;

/**
 * Draws a line with a stroke defined by {@link StrokeType}, provided that the graphics stroke is {@link BasicStroke}),
 * otherwise defaults to {@code Graphics2D.draw(Line2D.Double)}.
 * <p>
 * It's assumed that the {@link JBUI.ScaleType#USR_SCALE} factor is already applied to the values (given in the user space)
 * passed to the methods of this class. So the user scale factor is not taken into account.
 *
 * @author tav
 */
public class LinePainter2D {
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
  @SuppressWarnings("Duplicates")
  public static void paint(@NotNull final Graphics2D g,
                           double x1, double y1, double x2, double y2,
                           @NotNull StrokeType strokeType,
                           double strokeWidth,
                           @NotNull Object valueAA)
  {
    boolean horizontal = y1 == y2;
    boolean vertical = x1 == x2;
    boolean straight = horizontal || vertical;
    boolean centered = strokeType == StrokeType.CENTERED || strokeType == StrokeType.CENTERED_CAPS_SQUARE;
    boolean thickStroke = PaintUtil.devValue(strokeWidth, g) > 1;

    if (g.getStroke() instanceof BasicStroke && (thickStroke || (straight && !centered))) {
      double sw = alignToInt(strokeWidth, g);
      double swx_2 = 0, swx_1 = 0, swy_2 = 0, swy_1 = 0; // stroke offsets
      double capy_1 = 0, capy_2 = 0, capx_1 = 0, capx_2 = 0; // caps offsets

      double angle = Math.atan2(y1 - y2, x2 - x1); // invert the sign of Y-axis, directing it bottom to top
      double sin = Math.sin(angle);
      double cos = Math.cos(angle);
      if (straight) {
        sin = Math.abs(sin);
        cos = Math.abs(cos);
      }

      if (strokeType == StrokeType.CENTERED_CAPS_SQUARE) {
        double cap_1 = strokeWidth > 1 ? sw / 2 : 0;
        double cap_2 = Math.max(cap_1 - 1, 0);
        double y_sign = straight ? 1 : -1;
        // cap_1 >= cap_2 for x1/y1 < x2/y2, otherwise swap for diagonal line
        capx_1 = (straight || x1 <= x2 ? cap_1 : cap_2) * cos;
        capx_2 = (straight || x1 <= x2 ? cap_2 : cap_1) * cos;
        capy_1 = (straight || y1 <= y2 ? cap_1 : cap_2) * sin * y_sign;
        capy_2 = (straight || y1 <= y2 ? cap_2 : cap_1) * sin * y_sign;
      }

      // below x/y are aligned to int dev pixels to conform to RectanglePainter2D edges painting

      if (vertical) {
        double y_min = Math.min(y1, y2);
        double y_max = Math.max(y1, y2);
        y1 = alignToInt(y_min, g);
        y2 = y1 + y_max - y_min + 1;
        x1 = x2 = alignToInt(x2, g);
      }
      else if (horizontal) {
        double x_min = Math.min(x1, x2);
        double x_max = Math.max(x1, x2);
        x1 = alignToInt(x_min, g);
        x2 = x1 + x_max - x_min + 1;
        y1 = y2 = alignToInt(y2, g);
      }
      else {
        x1 = alignToInt(x1, g);
        x2 = alignToInt(x2, g);
        y1 = alignToInt(y1, g);
        y2 = alignToInt(y2, g);

        if (Math.abs(angle) > Math.PI / 2) {
          sin *= -1;
          cos *= -1;
        }
      }
      if (strokeType == StrokeType.OUTSIDE) {
        swx_1 = sw * sin;
        swy_1 = sw * cos;
      }
      else if (strokeType == StrokeType.INSIDE) {
        swx_2 = sw * sin;
        swy_2 = sw * cos;
      }
      else if (centered) {
        // The stroke is painted around the center of a user pixel (or in a user pixel when smaller)
        // for straight lines, and around the math line itself otherwise.
        int usr_pix = straight ? 1 : 0;
        double _sw = sw - usr_pix;
        double sw_1 = alignToInt(Math.max(_sw / 2, 0), g);
        //if (sw_1 * 2 < _sw) sw_1 = _sw - sw_1; // bias to left/top of the line
        double sw_2 = Math.max(usr_pix + (_sw - sw_1), 0);

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
                            new Runnable() {
          @Override
          public void run() {
            g.fill(path);
          }
        });
    }
    else {
      final Line2D line = new Line2D.Double(x1, y1, x2, y2);
      PaintUtil.paintWithAA(g, valueAA,
                            new Runnable() {
          @Override
          public void run() {
            g.draw(line);
          }
        });
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
  @ApiStatus.Experimental
  public static void fillPolygon(@NotNull final Graphics2D g,
                                 double[] xPoints, double[] yPoints,
                                 int nPoints,
                                 StrokeType strokeType, double strokeWidth,
                                 @NotNull Object valueAA)
  {
    // [tav] todo: mind strokeWidth and strokeType
    final Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
    path.moveTo(xPoints[0], yPoints[0]);
    for (int p = 1; p < nPoints; p++) {
      path.lineTo(xPoints[p], yPoints[p]);
    }
    path.closePath();
    PaintUtil.paintWithAA(g, valueAA,
                          new Runnable() {
                            @Override
                            public void run() {
                              g.fill(path);
                            }
                          });
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
  @ApiStatus.Experimental
  public static void paintPolygon(@NotNull Graphics2D g,
                                  double[] xPoints, double[] yPoints,
                                  int nPoints,
                                  StrokeType strokeType, double strokeWidth,
                                  @NotNull Object valueAA)
  {
    double x1, x2, y1, y2;
    boolean thickPixel = UIUtil.isJreHiDPIEnabled() && PaintUtil.devValue(strokeWidth, g) > 1;
    boolean prevStraight = nPoints <= 1 || isStraightLine(xPoints, yPoints, nPoints, nPoints);

    for (int p = 0; p < nPoints; p++) {
      x1 = xPoints[p];
      y1 = yPoints[p];
      x2 = xPoints[(p + 1) % nPoints];
      y2 = yPoints[(p + 1) % nPoints];
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

  private static boolean isStraightLine(double[] xPoints, double[] yPoints, int nLine, int nPoints) {
    double x1 = xPoints[nLine % nPoints];
    double y1 = yPoints[nLine % nPoints];
    double x2 = xPoints[(nLine + 1) % nPoints];
    double y2 = yPoints[(nLine + 1) % nPoints];
    return x1 == x2 || y1 == y2;
  }

  /**
   * @see #getStrokeCenter(Graphics2D, double, StrokeType, double)
   */
  public static double getStrokeCenter(ScaleContext ctx, double coord, StrokeType strokeType, double strokeWidth) {
    if (strokeType == StrokeType.INSIDE) {
      return coord + strokeWidth / 2;
    }
    if (strokeType == StrokeType.OUTSIDE) {
      return coord - strokeWidth / 2;
    }
    double _sw = strokeWidth - 1;
    double sw_1 = alignToInt(Math.max(_sw / 2, 0), ctx, RoundingMode.ROUND, null);
    double sw_2 = Math.max(1 + (_sw - sw_1), 0);
    return coord - sw_1 / 2 + sw_2 / 2;
  }

  /**
   * Returns the coordinate of the center of the stroke.
   *
   * @param g           the graphics
   * @param coord       the x or y coordinate
   * @param strokeType  the stroke type
   * @param strokeWidth the stroke width
   * @return the coordinate of the center of the stroke
   */
  public static double getStrokeCenter(Graphics2D g, double coord, StrokeType strokeType, double strokeWidth) {
    return getStrokeCenter(ScaleContext.create(g), coord, strokeType, strokeWidth);
  }
}