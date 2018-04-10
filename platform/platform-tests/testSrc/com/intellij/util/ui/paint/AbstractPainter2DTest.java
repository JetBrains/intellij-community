// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.paint;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.UIUtil;
import org.junit.After;
import org.junit.Before;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.function.Function;

import static com.intellij.util.ui.JBUI.scale;
import static java.lang.Math.ceil;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Compares golden images with the images painted by the test.
 *
 * @author tav
 */
public abstract class AbstractPainter2DTest {
  private float originalUserScale;
  private boolean originalJreHiDPIEnabled;

  @Before
  public void setState() {
    originalUserScale = scale(1f);
    originalJreHiDPIEnabled = UIUtil.isJreHiDPIEnabled();
  }

  @After
  public void restoreState() {
    JBUI.setUserScaleFactor(originalUserScale);
    PaintUtilTest.overrideJreHiDPIEnabled(originalJreHiDPIEnabled);
  }

  public void testGoldenImages() {
    // 1) IDE-HiDPI
    for (int scale : getScales()) testGolden(scale, false);

    // 2) JRE-HiDPI
    for (int scale : getScales()) testGolden(scale, true);

    // 3) Boundary values
    paintImage(2, 10, 10, this::testBoundaries);
  }

  private void testGolden(int scale, boolean jreHiDPIEnabled) {
    PaintUtilTest.overrideJreHiDPIEnabled(jreHiDPIEnabled);
    JBUI.setUserScaleFactor(jreHiDPIEnabled ? 1 : scale);

    BufferedImage image = paintImage(scale, getImageSize().width, getImageSize().height, this::paint);

    //save(image, scale); // uncomment to recreate golden image

    compare(image, load(scale), scale, true);
  }

  protected BufferedImage paintImage(double scale, int width, int height, Function<Graphics2D, Void> paint) {
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage image = new BufferedImage((int)ceil(width * scale), (int)ceil(height * scale), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      double gScale = UIUtil.isJreHiDPIEnabled() ? scale : 1;
      g.scale(gScale, gScale);
      g.setColor(Color.white);
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      g.setColor(Color.black);

      paint.apply(g);

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

  @SuppressWarnings("unused")
  private void save(BufferedImage bi, int scale) {
    try {
      javax.imageio.ImageIO.write(bi, "png", new File(getGoldenImagePath(scale)));
    } catch (java.io.IOException e) {
      e.printStackTrace();
    }
  }

  private BufferedImage load(int scale) {
    try {
      Image img = ImageLoader.loadFromUrl(
        new File(getGoldenImagePath(scale)).toURI().toURL(), false, false, null, ScaleContext.createIdentity());
      return ImageUtil.toBufferedImage(img);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void compare(BufferedImage img1, BufferedImage img2, double scale, boolean alpha) {
    int[] d1 = ((DataBufferInt)img1.getRaster().getDataBuffer()).getData();
    int[] d2 = ((DataBufferInt)img2.getRaster().getDataBuffer()).getData();
    String msg = "the painting is incorrect (JreHiDPIEnabled: " + UIUtil.isJreHiDPIEnabled() + "; scale: " + scale + ")";
    if (alpha) {
      assertEquals(msg, d1.length, d2.length);
      for (int i = 0; i < d1.length; i++) {
        boolean pix1 = d1[i] != 0xffffffff;
        boolean pix2 = d2[i] != 0xffffffff;
        assertTrue(msg, pix1 == pix2);
      }
    }
    else {
      assertTrue(msg, Arrays.equals(d1, d2));
    }
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
