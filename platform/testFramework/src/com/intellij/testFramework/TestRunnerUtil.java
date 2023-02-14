// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

public final class TestRunnerUtil {
  private TestRunnerUtil() { }

  /** @deprecated please use {@link UITestUtil#replaceIdeEventQueueSafely()} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static void replaceIdeEventQueueSafely() {
    UITestUtil.replaceIdeEventQueueSafely();
  }
}
