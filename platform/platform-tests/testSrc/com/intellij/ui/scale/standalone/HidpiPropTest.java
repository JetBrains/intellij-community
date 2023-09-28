// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale.standalone;

import com.intellij.platform.ide.bootstrap.UiKt;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.TestScaleHelper;
import com.intellij.util.ui.StartupUiUtil;
import org.junit.Assert;
import org.junit.Test;

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
    TestScaleHelper.assumeHeadful();

    System.setProperty(HIDPI_PROP, "false");
    UiKt.checkHiDPISettings();

    Graphics2D g = TestScaleHelper.createGraphics(2);

    Assert.assertFalse(UI_SCALE_PROP + " should be disabled", Boolean.getBoolean(UI_SCALE_PROP));
    Assert.assertFalse("hidpi should be disabled", StartupUiUtil.isJreHiDPI());
    Assert.assertFalse("hidpi should be disabled", JreHiDpiUtil.isJreHiDPI(g));
  }
}
