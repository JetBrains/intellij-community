// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;


public final class TestRunnerUtil {
  private TestRunnerUtil() {
  }

  public static void replaceIdeEventQueueSafely() {
    TestApplicationManagerKt.replaceIdeEventQueueSafely();
  }
}
