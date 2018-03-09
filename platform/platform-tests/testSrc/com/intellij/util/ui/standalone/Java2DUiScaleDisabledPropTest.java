// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.standalone;

import com.intellij.util.ui.TestScaleHelper;
import com.intellij.util.ui.UIUtil;
import org.junit.Test;

import static com.intellij.util.ui.standalone.Java2DUiScaleEnabledPropTest.JAVA2D_UI_SCALE_ENABLED_PROP;
import static junit.framework.TestCase.assertFalse;

/**
 * Tests "sun.java2d.uiScale.enabled" property.
 *
 * @author tav
 */
public class Java2DUiScaleDisabledPropTest {
  @Test
  public void test() {
    TestScaleHelper.assumeStandalone();

    System.setProperty(JAVA2D_UI_SCALE_ENABLED_PROP, "false");

    assertFalse(JAVA2D_UI_SCALE_ENABLED_PROP + " system property is ignored", UIUtil.isJreHiDPIEnabled());
  }
}
