// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * @author tav
 */
@SuppressWarnings("JUnitTestCaseWithNoTests")
public class TestScaleHelper {
  private static final String STANDALONE_PROP = "intellij.test.standalone";

  private static final Map<String, String> origProps = new HashMap<>();

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
    restoreProperties();
  }

  public static void setProperty(@NotNull String name, @Nullable String value) {
    origProps.put(name, System.getProperty(name));
    _setProperty(name, value);
  }

  private static void _setProperty(String name, String value) {
    if (value != null) {
      System.setProperty(name, value);
    }
    else {
      System.clearProperty(name);
    }
  }

  public static void restoreProperties() {
    for (Map.Entry<String, String> entry : origProps.entrySet()) {
      _setProperty(entry.getKey(), entry.getValue());
    }
  }

  public static void overrideJreHiDPIEnabled(boolean enabled) {
    UIUtil.test_jreHiDPI().set(enabled);
  }

  public static void assumeStandalone() {
    Assume.assumeTrue("not in " + STANDALONE_PROP + " mode", SystemProperties.is(STANDALONE_PROP));
  }

  public static Graphics2D createGraphics(double scale) {
    //noinspection UndesirableClassUsage
    Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
    g.scale(scale, scale);
    return g;
  }
}
