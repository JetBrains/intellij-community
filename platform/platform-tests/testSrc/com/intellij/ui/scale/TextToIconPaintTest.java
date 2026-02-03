// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleExtension;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TextToIconPaintTest extends CompositeIconPaintTestHelper {
  @RegisterExtension
  public static final RestoreScaleExtension manageState = new RestoreScaleExtension();
  private static final String TEXT = "IDEA";
  private static final int FONT_SIZE = 12;
  private static final int[][] SCALES_TO_SIZE = { // {USR_SCALE, SYS_SCALE, OBJ_SCALE -> size}
    {1, 1, 1, 26},
    {1, 2, 1, 26},
    {2, 1, 1, 57},
    {2, 2, 1, 57},
    {1, 1, 2, 57},
    {1, 2, 2, 57},
    {2, 1, 2, 113}
  };
  private static final Map<ScaleContext, Integer> CTX_TO_SIZE = new HashMap<>();

  static {
    for (int[] scaleData : SCALES_TO_SIZE) {
      CTX_TO_SIZE.put(ScaleContext.Companion.of(new Scale[]{ScaleType.USR_SCALE.of(scaleData[0]),
                        ScaleType.SYS_SCALE.of(scaleData[1]),
                        ScaleType.OBJ_SCALE.of(scaleData[2])}),
                      scaleData[3]);
    }
  }

  @Test
  @Override
  public void test() {
    super.test();
  }

  @Override
  protected ScalableIcon createCompositeIcon(ScaleContext ctx, Icon... cellIcons) {
    return (ScalableIcon)IconUtil.textToIcon(TEXT, TestScaleHelper.createComponent(ctx), JBUIScale.scale(FONT_SIZE));
  }

  @Override
  protected void assume(ScaleContext ctx) {
    // The test may depend on a physical font which may vary b/w platforms. By this reason, there's a preset map
    // b/w ScaleContext's and the tested string with. If the font on this platform doesn't fit the preset,
    // then the test silently interrupts.
    Font font = JBFont.create(JBFont.label().deriveFont((float)ctx.apply(FONT_SIZE, DerivedScaleType.EFF_USR_SCALE)));
    int width = TestScaleHelper.createComponent(ctx).getFontMetrics(font).stringWidth(TEXT);
    assumeTrue(CTX_TO_SIZE.containsKey(ctx), "unexpected ScaleContext: " + ctx);
    assumeTrue(CTX_TO_SIZE.get(ctx) == width, "unexpected text width: " + width);
  }

  @Override
  protected String getGoldImagePath(ScaleContext ctx) {
    int usrScale = (int)(ctx.apply(1, DerivedScaleType.EFF_USR_SCALE));
    int sysScale = (int)(ctx.getScale(ScaleType.SYS_SCALE));
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_TextIcon@" + usrScale + "x" + sysScale + "x.png";
  }

  @Override
  protected boolean shouldSaveGoldImage() {
    return false;
  }

  @Override
  protected String[] getCellIconsPaths() {
    return ArrayUtil.EMPTY_STRING_ARRAY; // just pretends to be composite
  }
}
