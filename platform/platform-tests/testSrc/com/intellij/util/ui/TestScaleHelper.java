// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.util.FieldAccessor;
import org.junit.After;
import org.junit.Before;

/**
 * @author tav
 */
public class TestScaleHelper {
  private static final FieldAccessor<UIUtil, Boolean> JRE_HIDPI_ACCESSOR = new FieldAccessor<>(UIUtil.class, "jreHiDPI");

  private float originalUserScale;
  private boolean originalJreHiDPIEnabled;

  @Before
  public void setState() {
    originalUserScale = JBUI.scale(1f);
    originalJreHiDPIEnabled = UIUtil.isJreHiDPIEnabled();
  }

  @After
  public void restoreState() {
    JBUI.setUserScaleFactor(originalUserScale);
    overrideJreHiDPIEnabled(originalJreHiDPIEnabled);
  }

  public static void overrideJreHiDPIEnabled(boolean enabled) {
    JRE_HIDPI_ACCESSOR.set(null, enabled);
  }
}
