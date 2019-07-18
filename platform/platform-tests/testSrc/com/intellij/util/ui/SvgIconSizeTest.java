// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.SVGLoader;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import static com.intellij.ui.scale.DerivedScaleType.PIX_SCALE;
import static com.intellij.ui.scale.ScaleType.SYS_SCALE;
import static com.intellij.util.ui.TestScaleHelper.loadImage;
import static com.intellij.util.ui.TestScaleHelper.overrideJreHiDPIEnabled;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

/**
 * Tests that {@link SVGLoader} correctly interprets SVG document size.
 *
 * @author tav
 */
public class SvgIconSizeTest {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @Test
  public void test() throws IOException {
    JBUIScale.setUserScaleFactor((float)1);
    overrideJreHiDPIEnabled(true);

    test(ScaleContext.create(SYS_SCALE.of(1)));
    test(ScaleContext.create(SYS_SCALE.of(2)));

    float currentSysScale = JBUIScale.sysScale();
    if (currentSysScale != 2) {
      JBUIScale.setSystemScaleFactor(2);
      /*
       * Test with the system scale equal to the current system scale.
       */
      test(ScaleContext.create(SYS_SCALE.of(2)));
      JBUIScale.setSystemScaleFactor(currentSysScale);
    }

    /*
     * Test overridden size.
     */
    URL url = new File(getSvgIconPath("20x10")).toURI().toURL();
    ScaleContext ctx = ScaleContext.create(SYS_SCALE.of(2));
    double pixScale = ctx.getScale(PIX_SCALE);
    Image image = SVGLoader.load(url, url.openStream(), ctx, 25, 15);
    assertNotNull(image);
    image = ImageUtil.toBufferedImage(image);
    assertEquals("wrong image width", pixScale * 25, (double)image.getWidth(null));
    assertEquals("wrong image height", pixScale * 15, (double)image.getHeight(null));

    /*
     * Test SVGLoader.getDocumentSize for SVG starting with <svg.
     */
    url = new File(getSvgIconPath("20x10")).toURI().toURL();
    ImageLoader.Dimension2DDouble size = SVGLoader.getDocumentSize(url, url.openStream(), 1);
    assertEquals("wrong svg doc width", 20d, size.getWidth());
    assertEquals("wrong svg doc height", 10d, size.getHeight());

    /*
     * Test SVGLoader.getDocumentSize for SVG starting with <?xml.
     */
    url = new File(getSvgIconPath("xml_20x10")).toURI().toURL();
    size = SVGLoader.getDocumentSize(url, url.openStream(), 1);
    assertEquals("wrong svg doc width", 20d, size.getWidth());
    assertEquals("wrong svg doc height", 10d, size.getHeight());
  }

  private static void test(ScaleContext ctx) {
    int scale = (int)ctx.getScale(SYS_SCALE);

    /*
     * Test default unit ("px").
     */
    BufferedImage image = loadImage(getSvgIconPath("20x10"), ctx);
    assertNotNull(image);
    assertEquals("wrong image width", 20 * scale, image.getWidth());
    assertEquals("wrong image height", 10 * scale, image.getHeight());

    /*
     * Test "px" unit.
     */
    image = loadImage(getSvgIconPath("20px10px"), ctx);
    assertNotNull(image);
    assertEquals("wrong image width", 20 * scale, image.getWidth());
    assertEquals("wrong image height", 10 * scale, image.getHeight());

    /*
     * Test default size.
     */
    image = loadImage(getSvgIconPath("default"), ctx);
    assertNotNull(image);
    assertEquals("wrong image width", SVGLoader.ICON_DEFAULT_SIZE * scale, image.getWidth());
    assertEquals("wrong image height", SVGLoader.ICON_DEFAULT_SIZE * scale, image.getHeight());
  }

  private static String getSvgIconPath(String size) {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/myIcon_" + size + ".svg";
  }
}