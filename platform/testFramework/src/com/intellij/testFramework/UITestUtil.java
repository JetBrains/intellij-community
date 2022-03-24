// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public class UITestUtil {
  /**
   * Sets "java.awt.headless" system property
   */
  public static void setHeadlessProperty(boolean isHeadless) {
    System.setProperty("java.awt.headless", Boolean.toString(isHeadless));
  }

  /**
   * Executes {@code runnable} with the specified "java.awt.headless" system property and restore its value afterwards
   */
  public static <E extends Throwable> void runWithHeadlessProperty(boolean propertyValue, @NotNull ThrowableRunnable<E> runnable) throws E {
    boolean old = Boolean.getBoolean("java.awt.headless");
    setHeadlessProperty(propertyValue);
    try {
      runnable.run();
    }
    finally {
      setHeadlessProperty(old);
    }
  }
}
