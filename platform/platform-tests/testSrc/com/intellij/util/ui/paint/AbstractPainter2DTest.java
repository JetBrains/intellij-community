// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.paint;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.paint.ImageComparator.AASmootherComparator;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Function;

import static com.intellij.util.ui.TestScaleHelper.loadImage;
import static com.intellij.util.ui.TestScaleHelper.overrideJreHiDPIEnabled;
import static java.lang.Math.ceil;

/**
 * Compares golden images with the images painted by the test.
 *
 * @author tav
 */
public abstract class AbstractPainter2DTest {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  public void testGoldenImages() {
    ImageComparator comparator = new ImageComparator(
      new AASmootherComparator(0.15, 0.5, Color.BLACK));

    // 1) IDE-HiDPI
    for (int scale : getScales()) testGolden(comparator, scale, false);

    // 2) JRE-HiDPI
    for (int scale : getScales()) testGolden(comparator, scale, true);

    // 3) Boundary values
    supplyGraphics(2, 10, 10, this::testBoundaries);
  }

  private void testGolden(ImageComparator comparator, int scale, boolean jreHiDPIEnabled) {
    overrideJreHiDPIEnabled(jreHiDPIEnabled);
    JBUI.setUserScaleFactor(jreHiDPIEnabled ? 1 : scale);

    BufferedImage image = supplyGraphics(scale, getImageSize().width, getImageSize().height, this::paint);

    //saveImage(image, getGoldenImagePath(scale)); // uncomment to recreate golden image

    compare(image, loadImage(getGoldenImagePath(scale)), comparator, scale);
  }

  protected BufferedImage supplyGraphics(double scale, int width, int height, Function<Graphics2D, Void> consumeGraphics) {
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage image = new BufferedImage((int)ceil(width * scale), (int)ceil(height * scale), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      double gScale = UIUtil.isJreHiDPIEnabled() ? scale : 1;
      g.scale(gScale, gScale);
      g.setColor(Color.white);
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      g.setColor(Color.black);

      consumeGraphics.apply(g);

      return image;
    }
    finally {
      g.dispose();
    }
  }

  private Void testBoundaries(Graphics2D g) {
    double[][] values = {
      {0, 0, 0, 0},
      {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE},
      {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE},
    };
    for (double[] v : values) paintBoundaries(g, v);
    return null;
  }

  protected static void compare(BufferedImage img1, BufferedImage img2, ImageComparator comparator, double scale) {
    comparator.compareAndAssert(img1, img2, "images mismatch: JreHiDPIEnabled=" + UIUtil.isJreHiDPIEnabled() + "; scale=" + scale + "; ");
  }

  private String getGoldenImagePath(int scale) {
    return PlatformTestUtil.getPlatformTestDataPath() +
           "ui/paint/" + getGoldenImageName() +
           (scale > 1 && UIUtil.isJreHiDPIEnabled() ? "_hd@" : "@") +
           scale + "x.png";
  }

  protected abstract Void paint(Graphics2D g);

  protected abstract void paintBoundaries(Graphics2D g, double[] values);

  protected abstract Dimension getImageSize();

  protected abstract String getGoldenImageName();

  protected abstract int[] getScales();
}
