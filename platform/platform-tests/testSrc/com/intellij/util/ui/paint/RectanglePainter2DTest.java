// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.paint;

import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.LinePainter2D.StrokeType;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.junit.Test;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static com.intellij.util.ui.JBUI.scale;

/**
 * Tests the {@link RectanglePainter2D} and {@link LinePainter2D} painting.
 *
 * @author tav
 */
public class RectanglePainter2DTest extends AbstractPainter2D {
  private static final int RECT_SIZE = 10;
  private static final double ARC_SIZE = RECT_SIZE / 3;

  @Test
  @Override
  public void testGoldenImages() {
    super.testGoldenImages();
  }

  /**
   * Tests that a rectangle can be outlined with lines with pixel-perfect accuracy.
   */
  @Test
  public void testRectOutlining() {
    /*
     * On some scale factors (more precisely, on combination of factor and coordinates) the outlining has
     * subtle (one device pixel) difference from the rectangle. These factors are commented for the test.
     * The test thus represents a defacto behaviour.
     */

    // IDE-HiDPI
    for (double scale : new double[]{1, 1.25, /*1.5,*/ 1.75, 2, 2.25, 2.5, /*2.75,*/ 3}) {
      testRectOutline(scale, false, StrokeType.CENTERED);
      if (scale == 2.5) continue;
      testRectOutline(scale, false, StrokeType.INSIDE);
    }
    // JRE-HiDPI
    for (double scale : new double[]{1, 1.25, 1.5, /*1.75,*/ 2, 2.25, 2.5, /*2.75,*/ 3}) {
      testRectOutline(scale, true, StrokeType.CENTERED);
      testRectOutline(scale, true, StrokeType.INSIDE);
    }
  }

  @Override
  public Void paint(Graphics2D g) {
    paintRects(g, StrokeType.CENTERED, 2, 2);

    paintRects(g, StrokeType.INSIDE, RECT_SIZE + 2, 0);

    paintRects(g, StrokeType.OUTSIDE, RECT_SIZE + 2, 0);

    return null;
  }

  private void paintRects(Graphics2D g, StrokeType type, float trX, float trY) {
    g.translate(scale(trX), scale(trY));
    Object aa = RenderingHints.VALUE_ANTIALIAS_ON;
    paintRect(g, 0, 0, RECT_SIZE, RECT_SIZE, null, type, 1, aa, false);
    paintRect(g, 0, RECT_SIZE + 3, RECT_SIZE, RECT_SIZE, ARC_SIZE, type, 1, aa, false);
    paintRect(g, 0, (RECT_SIZE + 3) * 2, RECT_SIZE, RECT_SIZE, null, type, 1, aa, true);
    paintRect(g, 0, (RECT_SIZE + 3) * 3, RECT_SIZE, RECT_SIZE, ARC_SIZE, type, 1, aa, true);
  }

  public void paintRect(Graphics2D g,
                    double x, double y, double w, double h,
                    Double arc,
                    StrokeType strokeType,
                    double strokeWidth,
                    Object valueAA,
                    boolean fill)
  {
    strokeWidth = scale((float)strokeWidth);
    if (arc != null) arc = Double.valueOf(scale(arc.floatValue()));
    x = scale((float)x);
    y = scale((float)y);
    w = scale((float)w);
    h = scale((float)h);
    if (fill) {
      RectanglePainter2D.FILL.paint(g, x, y, w, h, arc, strokeType, strokeWidth, valueAA);
    }
    else {
      RectanglePainter2D.DRAW.paint(g, x, y, w, h, arc, strokeType, strokeWidth, valueAA);
    }
  }

  @Override
  protected void paintBoundaries(Graphics2D g, double[] values) {
    RectanglePainter2D.DRAW.paint(g, values[0], values[1], values[2], values[3]);
    RectanglePainter2D.FILL.paint(g, values[0], values[1], values[2], values[3]);
  }

  private void testRectOutline(double scale, boolean jreHiDPIEnabled, StrokeType strokeType) {
    PaintUtilTest.overrideJreHiDPIEnabled(jreHiDPIEnabled);
    JBUI.setUserScaleFactor(jreHiDPIEnabled ? 1 : (float)scale);

    BufferedImage rect = paintImage(scale, 15, 15,
                                    strokeType == StrokeType.INSIDE ? this::paintRectInside : this::paintRectCentered);
    BufferedImage outline = paintImage(scale, 15, 15,
                                       strokeType == StrokeType.INSIDE ? this::outlineRectInside : this::outlineRectCentered);
    compare(rect, outline, scale, false);
  }

  private Rectangle2D rectBounds() {
    return new Rectangle2D.Float(scale(3f), scale(3f), scale(10f), scale(10f));
  }

  private Void paintRectInside(Graphics2D g) {
    return _paintRect(g, true);
  }

  private Void paintRectCentered(Graphics2D g) {
    return _paintRect(g, false);
  }

  private Void _paintRect(Graphics2D g, boolean inside) {
    Rectangle2D b = rectBounds();
    RectanglePainter2D.DRAW.paint(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), null,
                                  inside ? StrokeType.INSIDE : StrokeType.CENTERED, scale(1f));
    return null;
  }

  private Void outlineRectInside(Graphics2D g) {
    Rectangle2D b = rectBounds();
    double x = UIUtil.isJreHiDPIEnabled() ? b.getX() : PaintUtil.alignToInt(b.getX(), g);
    double y = UIUtil.isJreHiDPIEnabled() ? b.getY() : PaintUtil.alignToInt(b.getY(), g);
    double xx = x + b.getWidth();
    double yy = y + b.getHeight();

    // the rect right/bottom edge is painted at x + width - 1/y + height - 1 (unlike in Graphics.drawRect)
    double _xx = xx - 1;
    double _yy = yy - 1;

    LinePainter2D.paint(g, b.getX(), b.getY(), _xx, b.getY(), StrokeType.INSIDE, scale(1f));
    LinePainter2D.paint(g, xx, b.getY(), xx, _yy, StrokeType.OUTSIDE, scale(1f));
    LinePainter2D.paint(g, _xx, yy, b.getX(), yy, StrokeType.OUTSIDE, scale(1f));
    LinePainter2D.paint(g, b.getX(), _yy, b.getX(), b.getY(), StrokeType.INSIDE, scale(1f));
    return null;
  }

  private Void outlineRectCentered(Graphics2D g) {
    Rectangle2D b = rectBounds();
    double x = UIUtil.isJreHiDPIEnabled() ? b.getX() : PaintUtil.alignToInt(b.getX(), g);
    double y = UIUtil.isJreHiDPIEnabled() ? b.getY() : PaintUtil.alignToInt(b.getY(), g);
    double xx = x + b.getWidth();
    double yy = y + b.getHeight();

    // the rect right/bottom edge is painted at x + width - 1/y + height - 1 (unlike in Graphics.drawRect)
    xx -= 1;
    yy -= 1;

    LinePainter2D.paint(g, b.getX(), b.getY(), xx, b.getY(), StrokeType.CENTERED_CAPS_SQUARE, scale(1f));
    LinePainter2D.paint(g, xx, b.getY(), xx, yy, StrokeType.CENTERED_CAPS_SQUARE, scale(1f));
    LinePainter2D.paint(g, xx, yy, b.getX(), yy, StrokeType.CENTERED_CAPS_SQUARE, scale(1f));
    LinePainter2D.paint(g, b.getX(), yy, b.getX(), b.getY(), StrokeType.CENTERED_CAPS_SQUARE, scale(1f));
    return null;
  }

  @Override
  protected String getGoldenImageName() {
    return "RectanglePainter2D";
  }

  @Override
  protected Dimension getImageSize() {
    return new Dimension((RECT_SIZE + 2) * 3 + 3, (RECT_SIZE + 3) * 4 + 2);
  }

  @Override
  protected int[] getScales() {
    return new int[] {1, 2, 3};
  }
}
