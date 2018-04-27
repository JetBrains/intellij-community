// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RowIcon;

import javax.swing.*;

/**
 * Tests {@link com.intellij.ui.RowIcon} painting.
 *
 * @author tav
 */
public class RowIconPaintTest extends LayeredIconPaintTest {
  @Override
  protected ScalableIcon createCompositeIcon(Icon... cellIcons) {
    RowIcon icon = new RowIcon(2);
    icon.setIcon(cellIcons[0], 0);
    icon.setIcon(cellIcons[1], 1);
    return icon;
  }

  @Override
  protected String getGoldImagePath(int scale) {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_RowIcon@" + scale + "x.png";
  }
}
