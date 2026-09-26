// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale.standalone;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.TestScaleHelper;
import org.junit.Before;
import org.junit.Test;

import static com.intellij.ui.scale.standalone.Java2DUiScaleEnabledPropTest.JAVA2D_UI_SCALE_ENABLED_PROP;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assume.assumeFalse;

/**
 * Tests "sun.java2d.uiScale.enabled" property.
 *
 * @author tav
 */
public class Java2DUiScaleDisabledPropTest {
  @Before
  public void before() {
    assumeFalse(SystemInfoRt.isMac);
  }

  @Test
  public void test() {
    TestScaleHelper.assumeStandalone();
    TestScaleHelper.assumeHeadful();

    System.setProperty(JAVA2D_UI_SCALE_ENABLED_PROP, "false");

    assertFalse(JAVA2D_UI_SCALE_ENABLED_PROP + " system property is ignored", JreHiDpiUtil.isJreHiDPIEnabled());
  }
}
