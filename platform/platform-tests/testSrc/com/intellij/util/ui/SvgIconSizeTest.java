// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.JBUI.ScaleContext;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import static com.intellij.util.ui.JBUI.ScaleType.SYS_SCALE;
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
    JBUI.setUserScaleFactor(1);
    overrideJreHiDPIEnabled(true);

    test(ScaleContext.create(SYS_SCALE.of(1)));
    test(ScaleContext.create(SYS_SCALE.of(2)));

    /*
     * Test overridden size.
     */
    URL url = new File(getSvgIconPath("20x10")).toURI().toURL();
    Image image = SVGLoader.load(url, url.openStream(), 25, 15);
    assertNotNull(image);
    image = ImageUtil.toBufferedImage(image);
    assertEquals("wrong image width", 25, image.getWidth(null));
    assertEquals("wrong image height", 15, image.getHeight(null));
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