// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.standalone;

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TestScaleHelper;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

/**
 * Tests "ide.ui.scale" property.
 *
 * @author tav
 */
public class UserScalePropTest {
  private static final String IDE_UI_SCALE_PROP = "ide.ui.scale";

  @Test
  public void test() {
    TestScaleHelper.assumeStandalone();

    System.setProperty(IDE_UI_SCALE_PROP, "1.0");

    JBUI.setUserScaleFactor(2f);
    assertEquals(IDE_UI_SCALE_PROP + " system property is ignored", 1f, JBUI.scale(1f));
  }
}
