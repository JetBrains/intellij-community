// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.idea.StartupUtil;
import com.intellij.util.MethodInvocator;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SystemProperties;
import org.junit.*;

import javax.swing.*;
import java.awt.*;

/**
 * Tests "hidpi" system property.
 *
 * @author tav
 */
public class HidpiPropTest {
  static final String HIDPI_PROP = "hidpi";
  static final String UI_SCALE_PROP = "sun.java2d.uiScale.enabled";
  static final String STANDALONE_PROP = "intellij.test.standalone";

  @Before
  public void checkStandalone() {
    Assume.assumeTrue("not in " + STANDALONE_PROP + " mode", SystemProperties.is(STANDALONE_PROP));
  }

  @Before
  public void setState() {
    TestScaleHelper.setProperty(HIDPI_PROP, "false");
    MethodInvocator m = new MethodInvocator(StartupUtil.class, "checkHiDPISettings");
    Assume.assumeTrue("StartupUtil.checkHiDPISettings method not available", m.isAvailable());
    m.invoke(null);
  }

  @After
  public void restoreState() {
    TestScaleHelper.restoreProperties();
  }

  @Test
  public void test() {
    Graphics2D g = TestScaleHelper.createGraphics(2);

    Assert.assertFalse(UI_SCALE_PROP + " should be disabled", SystemProperties.is(UI_SCALE_PROP));
    Assert.assertFalse("hidpi should be disabled", UIUtil.isJreHiDPI());
    Assert.assertFalse("hidpi should be disabled", UIUtil.isJreHiDPI(g));
  }
}
