// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class UITestUtil {
  /**
   * Sets "java.awt.headless" system property and clears Swing stuff which depends on its value
   */
  public static void setHeadlessProperty(boolean isHeadless) {
    System.setProperty("java.awt.headless", Boolean.toString(isHeadless));
    // reset field value to let java.awt.GraphicsEnvironment.getHeadlessProperty re-read updated property
    boolean set = ReflectionUtil.setField(GraphicsEnvironment.class, null, Boolean.class, "headless", null);
    assert set;
    set = ReflectionUtil.setField(Toolkit.class, null, Toolkit.class, "toolkit", null);
    assert set;
    assert GraphicsEnvironment.isHeadless() == isHeadless;
  }

  /**
   * Executes {@code runnable} with the specified "java.awt.headless" system property and restore its value afterwards
   */
  public static <E extends Throwable> void runWithHeadlessProperty(boolean propertyValue, @NotNull ThrowableRunnable<E> runnable) throws E {
    boolean old = GraphicsEnvironment.isHeadless();
    setHeadlessProperty(propertyValue);
    try {
      runnable.run();
    }
    finally {
      setHeadlessProperty(old);
    }
  }
}
