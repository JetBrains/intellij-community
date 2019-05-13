// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.standalone;

import com.intellij.idea.StartupUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.TestScaleHelper;
import com.intellij.util.ui.UIUtil;
import org.junit.*;

import java.awt.*;

/**
 * Tests "hidpi" system property.
 *
 * @author tav
 */
public class HidpiPropTest {
  static final String HIDPI_PROP = "hidpi";
  static final String UI_SCALE_PROP = "sun.java2d.uiScale.enabled";

  @Test
  public void test() {
    TestScaleHelper.assumeStandalone();

    System.setProperty(HIDPI_PROP, "false");
    StartupUtil.test_checkHiDPISettings();

    Graphics2D g = TestScaleHelper.createGraphics(2);

    Assert.assertFalse(UI_SCALE_PROP + " should be disabled", SystemProperties.is(UI_SCALE_PROP));
    Assert.assertFalse("hidpi should be disabled", UIUtil.isJreHiDPI());
    Assert.assertFalse("hidpi should be disabled", UIUtil.isJreHiDPI(g));
  }
}
