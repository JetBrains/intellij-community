// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.OffsetIcon;
import com.intellij.ui.RestoreScaleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.swing.*;

/**
 * Tests {@link com.intellij.ui.OffsetIcon} painting.
 *
 * @author tav
 */
public class OffsetIconPaintTest extends CompositeIconPaintTestHelper {
  @RegisterExtension
  public static final RestoreScaleExtension manageState = new RestoreScaleExtension();

  @Test
  @Override
  public void test() {
    super.test();
  }

  @Override
  protected ScalableIcon createCompositeIcon(ScaleContext ctx, Icon... cellIcons) {
    return new OffsetIcon(cellIcons[0].getIconWidth(), cellIcons[0]);
  }

  @Override
  protected String getGoldImagePath(ScaleContext ctx) {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_OffsetIcon@" + (int)ctx.getScale(DerivedScaleType.PIX_SCALE) + "x.png";
  }

  @Override
  protected boolean shouldSaveGoldImage() {
    return false;
  }

  @Override
  protected String[] getCellIconsPaths() {
    return new String[] {
      PlatformTestUtil.getPlatformTestDataPath() + "ui/db_set_breakpoint.png"
    };
  }
}
