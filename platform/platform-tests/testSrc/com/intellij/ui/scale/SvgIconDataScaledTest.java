// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.util.SVGLoader;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

/**
 * Tests that {@link SVGLoader} correctly interprets "data-scaled" custom tag.
 *
 * @author tav
 */
public class SvgIconDataScaledTest {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @Test
  public void test() throws IOException {
    TestScaleHelper.overrideJreHiDPIEnabled(true);

    test(1);
    test(2);
  }

  private static void test(float userScale) {
    JBUIScale.setUserScaleFactor(userScale);
    test(ScaleContext.create(ScaleType.SYS_SCALE.of(2)));
    test(ScaleContext.create(ScaleType.SYS_SCALE.of(3)));
  }

  private static void test(ScaleContext ctx) {
    System.out.println("ScaleContext: " + ctx);

    BufferedImage image = TestScaleHelper.loadImage(Path.of(PlatformTestUtil.getPlatformTestDataPath() + "ui/myIcon_20x10_dataScaled@2x.svg"), ctx);
    assertNotNull(image);

    double scale = ctx.getScale(DerivedScaleType.PIX_SCALE) / 2 /* count the icon's @2x format */;

    assertEquals("wrong image width", PaintUtil.RoundingMode.ROUND.round(20 * scale), image.getWidth());
    assertEquals("wrong image height", PaintUtil.RoundingMode.ROUND.round(10 * scale), image.getHeight());
  }
}