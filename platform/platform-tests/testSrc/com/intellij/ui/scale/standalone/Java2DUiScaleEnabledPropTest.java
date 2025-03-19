// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale.standalone;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.TestScaleHelper;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Tests "sun.java2d.uiScale.enabled" & "sun.java2d.uiScale" properties.
 *
 * @author tav
 */
public class Java2DUiScaleEnabledPropTest {
  public static final String JAVA2D_UI_SCALE_ENABLED_PROP = "sun.java2d.uiScale.enabled";
  public static final String JAVA2D_UI_SCALE_PROP = "sun.java2d.uiScale";

  @Test
  public void test() {
    TestScaleHelper.assumeStandalone();
    TestScaleHelper.assumeHeadful();

    System.setProperty(JAVA2D_UI_SCALE_ENABLED_PROP, "true");
    System.setProperty(JAVA2D_UI_SCALE_PROP, "3.0");

    assertTrue(JAVA2D_UI_SCALE_ENABLED_PROP + " system property is ignored", JreHiDpiUtil.isJreHiDPIEnabled());
    if (!SystemInfoRt.isMac) {
      assertEquals(JAVA2D_UI_SCALE_PROP + " system property is ignored", 3f, JBUIScale.INSTANCE.computeSystemScaleFactor(() -> null));
    }
  }
}
