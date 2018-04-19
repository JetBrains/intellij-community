// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.OffsetIcon;
import org.junit.Test;

import javax.swing.*;

/**
 * Tests {@link com.intellij.ui.OffsetIcon} painting.
 *
 * @author tav
 */
public class OffsetIconPaintTest extends CompositeIconPaintTestHelper {
  @Test
  @Override
  public void test() {
    super.test();
  }

  @Override
  protected ScalableIcon createCompositeIcon(Icon... cellIcons) {
    return new OffsetIcon(cellIcons[0].getIconWidth(), cellIcons[0]);
  }

  @Override
  protected String getGoldImagePath(int scale) {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_OffsetIcon@" + scale + "x.png";
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
