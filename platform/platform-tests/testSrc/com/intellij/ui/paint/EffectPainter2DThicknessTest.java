// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.paint;

import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.TestScaleHelper;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.awt.*;

import static com.intellij.ui.scale.ScaleType.SYS_SCALE;
import static com.intellij.ui.scale.ScaleType.USR_SCALE;
import static com.intellij.util.ui.TestScaleHelper.overrideJreHiDPIEnabled;

/**
 * Tests {@link EffectPainter2D#maybeScaleFontMetricsThickness(double, Graphics2D, Font)}
 *
 * @author tav
 */
public class EffectPainter2DThicknessTest {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @Test
  public void test() {
    JBUIScale.DEF_SYSTEM_FONT_SIZE = 12;

    overrideJreHiDPIEnabled(false);
    for (int usrScale : new int[] {1, 2, 3}) {
      test(ScaleContext.create(SYS_SCALE.of(1), USR_SCALE.of(usrScale)));
    }

    overrideJreHiDPIEnabled(true);
    for (int sysScale : new int[] {1, 2, 3}) {
      for (int usrScale : new int[] {1, 2, 3}) {
        test(ScaleContext.create(SYS_SCALE.of(sysScale), USR_SCALE.of(usrScale)));
      }
    }
  }

  private static void test(ScaleContext ctx) {
    JBUIScale.setUserScaleFactor((float)ctx.getScale(USR_SCALE));

    Font font = StartupUiUtil.getLabelFont().deriveFont(JBUIScale.scale(JBUIScale.DEF_SYSTEM_FONT_SIZE));
    Graphics2D g = TestScaleHelper.createGraphics(ctx.getScale(SYS_SCALE));

    double actual = EffectPainter2D.maybeScaleFontMetricsThickness_TestOnly(1, font);
    double expectedHighBound = PaintUtil.alignToInt(JBUIScale.getFontScale(font.getSize2D()), g, RoundingMode.ROUND_FLOOR_BIAS);
    double expectedLowBound = expectedHighBound / 3;

    Assert.assertTrue(TestScaleHelper.msg(ctx), actual >= expectedLowBound && actual <= expectedHighBound);
  }
}
