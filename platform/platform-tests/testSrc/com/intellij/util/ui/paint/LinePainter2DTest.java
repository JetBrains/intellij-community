// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.paint;

import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.LinePainter2D.StrokeType;
import org.junit.Test;

import java.awt.*;

import static com.intellij.util.ui.JBUI.scale;

/**
 * Tests the {@link LinePainter2D} painting.
 *
 * @author tav
 */
public class LinePainter2DTest extends AbstractPainter2D {
  private static final int LINE_LEN = 10;

  @Test
  @Override
  public void testGoldenImages() {
    super.testGoldenImages();
  }

  @Override
  public Void paint(Graphics2D g) {
    paintLines(g, StrokeType.CENTERED, LINE_LEN + 2, LINE_LEN + 2);

    paintLines(g, StrokeType.CENTERED_CAPS_SQUARE, LINE_LEN * 2 + 2, 0);

    paintLines(g, StrokeType.INSIDE, LINE_LEN * 2 + 2, 0);

    paintLines(g, StrokeType.OUTSIDE, LINE_LEN * 2 + 2, 0);
    return null;
  }

  private void paintLines(Graphics2D g, StrokeType type, float trX, float trY) {
    g.translate(scale(trX), scale(trY));
    Object aa = RenderingHints.VALUE_ANTIALIAS_ON;
    paintLine(g, 0, -2, 0, -LINE_LEN, type, 1, aa);
    paintLine(g, 2, -2, LINE_LEN, -LINE_LEN, type, 1, aa);
    paintLine(g, 2, 0, LINE_LEN, 0, type, 1, aa);
    paintLine(g, 2, 2, LINE_LEN, LINE_LEN, type, 1, aa);
    paintLine(g, 0, 2, 0, LINE_LEN, type, 1, aa);
    paintLine(g, -2, 2, -LINE_LEN, LINE_LEN, type, 1, aa);
    paintLine(g, -2, 0, -LINE_LEN, 0, type, 1, aa);
    paintLine(g, -2, -2, -LINE_LEN, -LINE_LEN, type, 1, aa);
  }

  private void paintLine(Graphics2D g,
                         double x1, double y1, double x2, double y2,
                         StrokeType strokeType,
                         double strokeWidth,
                         Object valueAA)
  {
    strokeWidth = scale((float)strokeWidth);
    x1 = scale((float)x1);
    y1 = scale((float)y1);
    x2 = scale((float)x2);
    y2 = scale((float)y2);
    LinePainter2D.paint(g, x1, y1, x2, y2, strokeType, strokeWidth, valueAA);
  }

  @Override
  protected void paintBoundaries(Graphics2D g, double[] values) {
    LinePainter2D.paint(g, values[0], values[1], values[2], values[3]);
  }

  @Override
  protected String getGoldenImageName() {
    return "LinePainter2D";
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
