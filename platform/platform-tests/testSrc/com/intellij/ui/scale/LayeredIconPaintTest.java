// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RestoreScaleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.swing.*;

import static com.intellij.ui.scale.DerivedScaleType.PIX_SCALE;

/**
 * Tests {@link com.intellij.ui.LayeredIcon} painting.
 *
 * @author tav
 */
public class LayeredIconPaintTest extends CompositeIconPaintTestHelper {
  @RegisterExtension
  public static final RestoreScaleExtension manageState = new RestoreScaleExtension();

  @Test
  @Override
  public void test() {
    super.test();
  }

  @Override
  protected ScalableIcon createCompositeIcon(ScaleContext ctx, Icon... cellIcons) {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(cellIcons[0], 0);
    icon.setIcon(cellIcons[1], 1, JBUIScale.scale(10), JBUIScale.scale(6));
    return icon;
  }

  @Override
  protected String getGoldImagePath(ScaleContext ctx) {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_LayeredIcon@" + (int)ctx.getScale(PIX_SCALE) + "x.png";
  }

  @Override
  protected boolean shouldSaveGoldImage() {
    return false;
  }

  @Override
  protected String[] getCellIconsPaths() {
    String platformTestDataPath = PlatformTestUtil.getPlatformTestDataPath();
    return new String[] {
      platformTestDataPath + "ui/db_set_breakpoint.png",
      platformTestDataPath + "ui/question_badge.png"
    };
  }
}
