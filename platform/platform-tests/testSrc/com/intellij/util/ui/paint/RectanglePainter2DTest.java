// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.paint;

import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.LinePainter2D.StrokeType;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.util.ui.JBUI;
import org.junit.Test;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static com.intellij.util.ui.JBUI.scale;
import static com.intellij.util.ui.TestScaleHelper.overrideJreHiDPIEnabled;

/**
 * Tests the {@link RectanglePainter2D} and {@link LinePainter2D} painting.
 *
 * @author tav
 */
public class RectanglePainter2DTest extends AbstractPainter2DTest {
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
    ImageComparator comparator = new ImageComparator();

    /*
     * In fact, absolute accuracy is achieved by painting with enabled antialiasing, in which case alpha more precisely
     * shows sub-pixel offsets. However, in that case outlining the right/bottom rect edge would bring additional
     * complexity to calculating the outline x/y. This is likely an overkill. So, in this test the only scale factors
     * which allow to achieve pixel-perfect accuracy are counted (more precisely, the combination of factor and coordinates).
     * The test thus represents a defacto behaviour.
     */

    // IDE-HiDPI
    for (double scale : new double[]{1, 1.25, /*1.5,*/ 1.75, 2, 2.25, 2.5, /*2.75,*/ 3}) {
      testRectOutline(comparator, scale, false, StrokeType.CENTERED);
      if (scale == 2.5) continue;
      testRectOutline(comparator, scale, false, StrokeType.INSIDE);
    }
    // JRE-HiDPI
    for (double scale : new double[]{1, 1.25, /*1.5,*/ 1.75, 2, 2.25, /*2.5,*/ 2.75, 3}) {
      testRectOutline(comparator, scale, true, StrokeType.CENTERED);
      testRectOutline(comparator, scale, true, StrokeType.INSIDE);
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

  private void testRectOutline(ImageComparator comparator, double scale, boolean jreHiDPIEnabled, StrokeType strokeType) {
    overrideJreHiDPIEnabled(jreHiDPIEnabled);
    JBUI.setUserScaleFactor(jreHiDPIEnabled ? 1 : (float)scale);

    BufferedImage rect = supplyGraphics(scale, 15, 15,
                                    strokeType == StrokeType.INSIDE ? RectanglePainter2DTest::paintRectInside : RectanglePainter2DTest::paintRectCentered);
    BufferedImage outline = supplyGraphics(scale, 15, 15,
                                       strokeType == StrokeType.INSIDE ? RectanglePainter2DTest::outlineRectInside : RectanglePainter2DTest::outlineRectCentered);
    compare(rect, outline, comparator, scale);
  }

  private static Rectangle2D rectBounds(Graphics2D g) {
    double x = PaintUtil.alignToInt(scale(3f), g);
    double w = PaintUtil.alignToInt(scale(10f), g);
    //noinspection SuspiciousNameCombination
    return new Rectangle2D.Double(x, x, w, w);
  }

  private static Void paintRectInside(Graphics2D g) {
    return _paintRect(g, true);
  }

  private static Void paintRectCentered(Graphics2D g) {
    return _paintRect(g, false);
  }

  private static Void _paintRect(Graphics2D g, boolean inside) {
    Rectangle2D b = rectBounds(g);
    RectanglePainter2D.DRAW.paint(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                                  inside ? StrokeType.INSIDE : StrokeType.CENTERED,
                                  scale(1f));
    return null;
  }

  private static Void outlineRectInside(Graphics2D g) {
    Rectangle2D b = rectBounds(g);
    double x = b.getX();
    double y = b.getY();
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

  private static Void outlineRectCentered(Graphics2D g) {
    Rectangle2D b = rectBounds(g);
    double x = b.getX();
    double y = b.getY();
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
    return "gold_RectanglePainter2D";
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
