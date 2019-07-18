// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.paint;

import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.LinePainter2D.Align;
import com.intellij.ui.paint.LinePainter2D.StrokeType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import junit.framework.TestCase;
import org.junit.Test;

import java.awt.*;
import java.awt.geom.Line2D;
import java.util.EnumSet;

import static com.intellij.ui.scale.DerivedScaleType.PIX_SCALE;
import static com.intellij.util.ui.TestScaleHelper.overrideJreHiDPIEnabled;

/**
 * Tests the {@link LinePainter2D} painting.
 *
 * @author tav
 */
public class LinePainter2DTest extends AbstractPainter2DTest {
  private static final int LINE_LEN = 10;

  @Test
  @Override
  public void testGoldenImages() {
    super.testGoldenImages();
  }

  @Test
  public void testAlign() {
    JBUIScale.setUserScaleFactor((float)1);

    overrideJreHiDPIEnabled(false);
    supplyGraphics(1, 1, 1, LinePainter2DTest::testAlign);

    overrideJreHiDPIEnabled(true);
    supplyGraphics(2, 1, 1, LinePainter2DTest::testAlign);
  }

  private static Void testAlign(Graphics2D g) {
    double scale = ScaleContext.create(g).getScale(PIX_SCALE);
    String msg = "LinePainter2D.align is incorrect (JreHiDPIEnabled: " + JreHiDpiUtil.isJreHiDPIEnabled() + "; scale: " + scale + ")";
    double delta = 0.000001;
    boolean jhd = JreHiDpiUtil.isJreHiDPIEnabled();

    // HORIZONTAL
    Line2D line = LinePainter2D.align(g, EnumSet.of(Align.CENTER_X, Align.CENTER_Y), 2.5, 2.5, 5, false, StrokeType.CENTERED, 1);
    TestCase.assertEquals(msg, 0, line.getX1(), delta);
    TestCase.assertEquals(msg, 2, line.getY1(), delta);
    TestCase.assertEquals(msg, 4, line.getX2(), delta);
    TestCase.assertEquals(msg, 2, line.getY2(), delta);

    line = LinePainter2D.align(g, EnumSet.of(Align.CENTER_X, Align.CENTER_Y), 2.5, 2, 5, false, StrokeType.CENTERED, 2);
    TestCase.assertEquals(msg, 0, line.getX1(), delta);
    TestCase.assertEquals(msg, jhd ? 1.5 : 2, line.getY1(), delta);
    TestCase.assertEquals(msg, 4, line.getX2(), delta);
    TestCase.assertEquals(msg, jhd ? 1.5 : 2, line.getY2(), delta);

    line = LinePainter2D.align(g, EnumSet.of(Align.CENTER_X, Align.CENTER_Y), 3, 1.5, 6, false, StrokeType.CENTERED, 3);
    TestCase.assertEquals(msg, 0, line.getX1(), delta);
    TestCase.assertEquals(msg, 1, line.getY1(), delta);
    TestCase.assertEquals(msg, 5, line.getX2(), delta);
    TestCase.assertEquals(msg, 1, line.getY2(), delta);

    // VERTICAL
    line = LinePainter2D.align(g, EnumSet.of(Align.CENTER_X, Align.CENTER_Y), 2.5, 2.5, 5, true, StrokeType.CENTERED, 1);
    TestCase.assertEquals(msg, 2, line.getX1(), delta);
    TestCase.assertEquals(msg, 0, line.getY1(), delta);
    TestCase.assertEquals(msg, 2, line.getX2(), delta);
    TestCase.assertEquals(msg, 4, line.getY2(), delta);

    line = LinePainter2D.align(g, EnumSet.of(Align.CENTER_X, Align.CENTER_Y), 2, 2.5, 5, true, StrokeType.CENTERED, 2);
    TestCase.assertEquals(msg, jhd ? 1.5 : 2, line.getX1(), delta);
    TestCase.assertEquals(msg, 0, line.getY1(), delta);
    TestCase.assertEquals(msg, jhd ? 1.5 : 2, line.getX2(), delta);
    TestCase.assertEquals(msg, 4, line.getY2(), delta);

    line = LinePainter2D.align(g, EnumSet.of(Align.CENTER_X, Align.CENTER_Y), 1.5, 3, 6, true, StrokeType.CENTERED, 3);
    TestCase.assertEquals(msg, 1, line.getX1(), delta);
    TestCase.assertEquals(msg, 0, line.getY1(), delta);
    TestCase.assertEquals(msg, 1, line.getX2(), delta);
    TestCase.assertEquals(msg, 5, line.getY2(), delta);

    return null;
  }

  @Override
  public Void paint(Graphics2D g) {
    paintLines(g, StrokeType.CENTERED, LINE_LEN + 2, LINE_LEN + 2);

    paintLines(g, StrokeType.CENTERED_CAPS_SQUARE, LINE_LEN * 2 + 2, 0);

    paintLines(g, StrokeType.INSIDE, LINE_LEN * 2 + 2, 0);

    paintLines(g, StrokeType.OUTSIDE, LINE_LEN * 2 + 2, 0);
    return null;
  }

  private static void paintLines(Graphics2D g, StrokeType type, float trX, float trY) {
    g.translate(JBUIScale.scale(trX), JBUIScale.scale(trY));
    Object aa = RenderingHints.VALUE_ANTIALIAS_ON;
    paintLine(g, 0, 0, 0, 0, type, 1, aa); // a dot
    paintLine(g, 0, -2, 0, -LINE_LEN, type, 1, aa);
    paintLine(g, 2, -2, LINE_LEN, -LINE_LEN, type, 1, aa);
    paintLine(g, 2, 0, LINE_LEN, 0, type, 1, aa);
    paintLine(g, 2, 2, LINE_LEN, LINE_LEN, type, 1, aa);
    paintLine(g, 0, 2, 0, LINE_LEN, type, 1, aa);
    paintLine(g, -2, 2, -LINE_LEN, LINE_LEN, type, 1, aa);
    paintLine(g, -2, 0, -LINE_LEN, 0, type, 1, aa);
    paintLine(g, -2, -2, -LINE_LEN, -LINE_LEN, type, 1, aa);
  }

  @SuppressWarnings("SameParameterValue")
  private static void paintLine(Graphics2D g,
                                double x1, double y1, double x2, double y2,
                                StrokeType strokeType,
                                double strokeWidth,
                                Object valueAA)
  {
    strokeWidth = JBUIScale.scale((float)strokeWidth);
    x1 = JBUIScale.scale((float)x1);
    y1 = JBUIScale.scale((float)y1);
    x2 = JBUIScale.scale((float)x2);
    y2 = JBUIScale.scale((float)y2);
    LinePainter2D.paint(g, x1, y1, x2, y2, strokeType, strokeWidth, valueAA);
  }

  @Override
  protected void paintBoundaries(Graphics2D g, double[] values) {
    LinePainter2D.paint(g, values[0], values[1], values[2], values[3]);
  }

  @Override
  protected String getGoldenImageName() {
    return "gold_LinePainter2D";
  }

  @Override
  protected Dimension getImageSize() {
    return new Dimension((LINE_LEN * 2 + 2) * 4 + 2, LINE_LEN * 2 + 4);
  }

  @Override
  protected int[] getScales() {
    return new int[] {1, 2, 3};
  }
}
