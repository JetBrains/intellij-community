// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI.ScaleContext;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import javax.swing.*;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.util.ui.JBUI.ScaleType.*;

public class TextToIconPaintTest extends CompositeIconPaintTestHelper {
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
    for (int i=0; i<SCALES_TO_SIZE.length; i++) {
      CTX_TO_SIZE.put(ScaleContext.create(USR_SCALE.of(SCALES_TO_SIZE[i][0]),
                                          SYS_SCALE.of(SCALES_TO_SIZE[i][1]),
                                          OBJ_SCALE.of(SCALES_TO_SIZE[i][2])),
                      SCALES_TO_SIZE[i][3]);
    }
  }

  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @Test
  @Override
  public void test() {
    super.test();
  }

  @Override
  protected ScalableIcon createCompositeIcon(ScaleContext ctx, Icon... cellIcons) {
    return (ScalableIcon)IconUtil.textToIcon(TEXT, TestScaleHelper.createComponent(ctx), JBUI.scale(FONT_SIZE));
  }

  @Override
  protected void assume(ScaleContext ctx) {
    // The test may depend on a physical font which may vary b/w platforms. By this reason, there's a preset map
    // b/w ScaleContext's and the tested string with. If the font on this platform doesn't fit the preset,
    // then the test silently interrupts.
    Font font = JBFont.create(JBUI.Fonts.label().deriveFont((float)ctx.apply(FONT_SIZE, USR_SCALE, OBJ_SCALE)));
    int width = TestScaleHelper.createComponent(ctx).getFontMetrics(font).stringWidth(TEXT);
    Assume.assumeTrue("unexpected ScaleContext: " + ctx, CTX_TO_SIZE.containsKey(ctx));
    Assume.assumeTrue("unexpected text width: " + width, CTX_TO_SIZE.get(ctx) == width);
  }

  @Override
  protected String getGoldImagePath(ScaleContext ctx) {
    int usrScale = (int)(ctx.apply(1, USR_SCALE, OBJ_SCALE));
    int sysScale = (int)(ctx.getScale(SYS_SCALE));
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_TextIcon@" + usrScale + "x" + sysScale + "x.png";
  }

  @Override
  protected boolean shouldSaveGoldImage() {
    return false;
  }

  @Override
  protected String[] getCellIconsPaths() {
    return new String[] {}; // just pretends to be composite
  }
}
