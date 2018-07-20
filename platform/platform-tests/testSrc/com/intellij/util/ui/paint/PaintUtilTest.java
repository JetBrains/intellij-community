// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.paint;

import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.paint.PaintUtil.ParityMode;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.ui.JBUI.ScaleContext;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.awt.*;
import java.awt.image.BufferedImage;

import static com.intellij.util.ui.TestScaleHelper.overrideJreHiDPIEnabled;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Tests {@link PaintUtil} methods.
 *
 * @author tav
 */
public class PaintUtilTest {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @Test
  public void test() {
    overrideJreHiDPIEnabled(true);
    @SuppressWarnings("UndesirableClassUsage")
    Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB).createGraphics();
    try {
      g.scale(2, 2);

      assertTrue(PaintUtil.getParityMode(1, g).even());
      assertTrue(PaintUtil.getParityMode(1.1, ScaleContext.create(g), null).even());
      assertTrue(PaintUtil.getParityMode(1.1, ScaleContext.create(g), RoundingMode.FLOOR).even());
      assertTrue(PaintUtil.getParityMode(1.1, ScaleContext.create(g), RoundingMode.ROUND).even());
      assertTrue(!PaintUtil.getParityMode(1.1, ScaleContext.create(g), RoundingMode.CEIL).even());

      assertEquals(1.0, PaintUtil.alignToInt(1, g, (RoundingMode)null));
      assertEquals(1.0, PaintUtil.alignToInt(1, g, (ParityMode)null));
      assertEquals(1.0, PaintUtil.alignToInt(1.1, g, RoundingMode.FLOOR));
      assertEquals(1.0, PaintUtil.alignToInt(1.1, g, RoundingMode.ROUND));
      assertEquals(1.5, PaintUtil.alignToInt(1.1, g, RoundingMode.CEIL));

      assertEquals(1.5, PaintUtil.alignToInt(1.1, ScaleContext.create(g), null, ParityMode.ODD));
      assertEquals(1.5, PaintUtil.alignToInt(1.1, ScaleContext.create(g), RoundingMode.ROUND, ParityMode.ODD));
      assertEquals(0.5, PaintUtil.alignToInt(1.1, ScaleContext.create(g), RoundingMode.FLOOR, ParityMode.ODD));
      assertEquals(1.5, PaintUtil.alignToInt(1.1, ScaleContext.create(g), RoundingMode.CEIL, ParityMode.ODD));

      assertEquals(1.0, PaintUtil.alignToInt(1.1, ScaleContext.create(g), null, ParityMode.EVEN));
      assertEquals(1.0, PaintUtil.alignToInt(1.1, ScaleContext.create(g), RoundingMode.ROUND, ParityMode.EVEN));
      assertEquals(1.0, PaintUtil.alignToInt(1.1, ScaleContext.create(g), RoundingMode.FLOOR, ParityMode.EVEN));
      assertEquals(2.0, PaintUtil.alignToInt(1.1, ScaleContext.create(g), RoundingMode.CEIL, ParityMode.EVEN));
    }
    finally {
      g.dispose();
    }
  }
}
