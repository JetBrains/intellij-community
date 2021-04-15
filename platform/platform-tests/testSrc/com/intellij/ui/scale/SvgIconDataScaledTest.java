// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.util.SVGLoader;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;
import static com.intellij.ui.scale.DerivedScaleType.PIX_SCALE;
import static com.intellij.ui.scale.ScaleType.SYS_SCALE;
import static com.intellij.ui.scale.TestScaleHelper.loadImage;
import static com.intellij.ui.scale.TestScaleHelper.overrideJreHiDPIEnabled;
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
    overrideJreHiDPIEnabled(true);

    test(1);
    test(2);
  }

  private static void test(float userScale) {
    JBUIScale.setUserScaleFactor(userScale);
    test(ScaleContext.create(SYS_SCALE.of(2)));
    test(ScaleContext.create(SYS_SCALE.of(3)));
  }

  private static void test(ScaleContext ctx) {
    System.out.println("ScaleContext: " + ctx);

    BufferedImage image = loadImage(PlatformTestUtil.getPlatformTestDataPath() + "ui/myIcon_20x10_dataScaled@2x.svg", ctx);
    assertNotNull(image);

    double scale = ctx.getScale(PIX_SCALE) / 2 /* count the icon's @2x format */;

    assertEquals("wrong image width", ROUND.round(20 * scale), image.getWidth());
    assertEquals("wrong image height", ROUND.round(10 * scale), image.getHeight());
  }
}