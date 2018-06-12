// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.util.RetinaImage;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;

import static com.intellij.util.ui.TestScaleHelper.createImageAndGraphics;
import static com.intellij.util.ui.TestScaleHelper.overrideJreHiDPIEnabled;
import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static junit.framework.TestCase.assertEquals;

/**
 * Tests {@link UIUtil#drawImage(Graphics, Image, Rectangle, Rectangle, BufferedImageOp, ImageObserver)}
 *
 * @author tav
 */
public class DrawImageTest {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  private final static int IMAGE_SIZE = 4;
  private final static int IMAGE_QUARTER_SIZE = IMAGE_SIZE / 2;

  private final static Color[] IMAGE_QUARTER_COLORS = new Color[] {
    Color.RED, Color.GREEN,
    Color.BLUE, Color.WHITE
  };
  private final static Color DEST_SURFACE_COLOR = Color.BLACK;

  private static Dest dest;
  private static Image source;

  private static class Dest {
    final Image image;
    final Graphics2D gr;
    final double scale;

    Dest(double scale) {
      Pair<Image, Graphics2D> pair = supplyImage(scale, IMAGE_SIZE, IMAGE_SIZE, new Color[] { DEST_SURFACE_COLOR }, true);
      image = pair.first;
      gr = pair.second;
      this.scale = scale;
    }

    void dispose() {
      gr.dispose();
    }
  }

  private static class TestColor {
    final Color expectedColor;
    final int dstCol;
    final int dstRow;

    TestColor(int col, int row) {
      this(col, row, col, row);
    }

    TestColor(int srcCol, int srcRow, int dstCol, int dstRow) {
      this(IMAGE_QUARTER_COLORS[srcRow * 2 + srcCol], dstCol, dstRow);
    }

    TestColor(Color expectedColor, int dstCol, int dstRow) {
      this.expectedColor = expectedColor;
      this.dstCol = dstCol;
      this.dstRow = dstRow;
    }

    void test() {
      BufferedImage bi = ImageUtil.toBufferedImage(dest.image);

      double qSize = IMAGE_QUARTER_SIZE * dest.scale;
      // left/top corner
      int x = (int)ceil(dstCol * qSize);
      int y = (int)ceil(dstRow * qSize);
      test(bi, x, y);

      // right/bottom corner
      x = (int)floor(dstCol * (qSize + 1));
      y = (int)floor(dstRow * (qSize + 1));
      test(bi, x, y);
    }

    private void test(BufferedImage bi, int x, int y) {
      Color dstColor = new Color(bi.getRGB(x, y));
      assertEquals("color mismatch at [" + x + ", " + y + "]", expectedColor, dstColor);
    }
  }

  @Test
  public void test() {
    for (double scale : new double[] {1, 2, 2.5}) {
      overrideJreHiDPIEnabled(true);
      JBUI.setUserScaleFactor(1);
      test(scale);

      overrideJreHiDPIEnabled(false);
      JBUI.setUserScaleFactor((float)scale);
      test(scale);
    }
  }

  public void test(double scale) {
    source = RetinaImage.createFrom(supplyImage(scale, IMAGE_SIZE, IMAGE_SIZE, IMAGE_QUARTER_COLORS, false).first,
                                    UIUtil.isJreHiDPIEnabled() ? scale : 1, null);

    //
    // 1) draw one to one
    //
    TestColor[] colors = new TestColor[] {
      new TestColor(0, 0),
      new TestColor(1, 0),
      new TestColor(0, 1),
      new TestColor(1, 1),
    };
    testDrawImage(dest = new Dest(scale), bounds(), bounds(), colors);
    testDrawImage(dest = new Dest(scale), null, bounds(), colors);
    testDrawImage(dest = new Dest(scale), bounds(), null, colors);
    testDrawImage(dest = new Dest(scale), null, null, colors);
    testDrawImage(dest = new Dest(scale), new Rectangle(0, 0, -1, -1), new Rectangle(0, 0, -1, -1), colors);


    //
    // 2) scale the 4th quarter to the whole dest
    //
    colors = new TestColor[] {
      new TestColor(IMAGE_QUARTER_COLORS[3], 0, 0),
      new TestColor(IMAGE_QUARTER_COLORS[3], 1, 0),
      new TestColor(IMAGE_QUARTER_COLORS[3], 0, 1),
      new TestColor(IMAGE_QUARTER_COLORS[3], 1, 1),
    };
    testDrawImage(dest = new Dest(scale),
                  new Rectangle(0, 0, -1, -1),
                  new Rectangle(JBUI.scale(IMAGE_QUARTER_SIZE), JBUI.scale(IMAGE_QUARTER_SIZE), -1, -1), colors);

    //
    // 3) draw random quarter to random quarter, all the rest quarter colors should remain DEST_SURFACE_COLOR
    //
    colors = new TestColor[] {
      new TestColor(DEST_SURFACE_COLOR, 0, 0),
      new TestColor(DEST_SURFACE_COLOR, 1, 0),
      new TestColor(DEST_SURFACE_COLOR, 0, 1),
      new TestColor(DEST_SURFACE_COLOR, 1, 1),
    };
    int srcCol = (int)floor(Math.random() + 0.5);
    int srcRow = (int)floor(Math.random() + 0.5);
    int dstCol = (int)floor(Math.random() + 0.5);
    int dstRow = (int)floor(Math.random() + 0.5);
    colors[dstRow * 2 + dstCol] = new TestColor(srcCol, srcRow, dstCol, dstRow); // replace the random quarter
    testDrawImage(dest = new Dest(scale), bounds(dstCol, dstRow), bounds(srcCol, srcRow), colors);
  }

  private static void testDrawImage(Dest dest, Rectangle dstBounds, Rectangle srcBounds, TestColor[] testColors) {
    UIUtil.drawImage(dest.gr, source, dstBounds, srcBounds, null);
    for (TestColor t : testColors) t.test();
    dest.dispose();
  }

  @SuppressWarnings("SameParameterValue")
  private static Pair<Image, Graphics2D> supplyImage(double scale, int width, int height, Color[] quarterColors, boolean supplyGraphics) {
    Pair<BufferedImage, Graphics2D> pair = createImageAndGraphics(scale, width, height);
    BufferedImage image = pair.first;
    Graphics2D g = pair.second;

    int qw = JBUI.scale(width) / 2;
    int qh = JBUI.scale(height) / 2;

    g.setColor(quarterColors[0]);
    g.fillRect(0, 0, qw, qh);
    g.setColor(quarterColors[1 % quarterColors.length]);
    g.fillRect(qw, 0, qw, qh);
    g.setColor(quarterColors[2 % quarterColors.length]);
    g.fillRect(0, qh, qw, qh);
    g.setColor(quarterColors[3 % quarterColors.length]);
    g.fillRect(qw, qh, qw, qh);

    if (!supplyGraphics) {
      g.dispose();
      g = null;
    }
    return new Pair<>(image, g);
  }

  private static Rectangle bounds() {
    return bounds(0, 0, JBUI.scale(IMAGE_SIZE));
  }

  private static Rectangle bounds(int col, int row) {
    return bounds(col, row, JBUI.scale(IMAGE_QUARTER_SIZE));
  }

  private static Rectangle bounds(int col, int row, int size) {
    return new Rectangle(col * size, row * size, size, size);
  }
}
