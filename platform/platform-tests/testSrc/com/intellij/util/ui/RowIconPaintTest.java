// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RowIcon;
import org.junit.Test;

import javax.swing.*;
import java.net.MalformedURLException;

/**
 * Tests {@link com.intellij.ui.RowIcon} painting.
 *
 * @author tav
 */
public class RowIconPaintTest extends LayeredIconPaintTest {
  @Test
  @Override
  public void test() throws MalformedURLException {
    super.test();
  }

  @Override
  protected ScalableIcon createAndSetIcons(Icon icon1, Icon icon2) {
    RowIcon icon = new RowIcon(2);
    icon.setIcon(icon1, 0);
    icon.setIcon(icon2, 1);
    return icon;
  }

  @Override
  protected String getGoldImagePath(int scale) {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_RowIcon@" + scale + "x.png";
  }
}
