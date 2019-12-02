// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.idea.IdeaTestApplicationKt;

/**
 * @author yole
 */
public final class TestRunnerUtil {
  private TestRunnerUtil() {
  }

  public static void replaceIdeEventQueueSafely() {
    IdeaTestApplicationKt.replaceIdeEventQueueSafely();
  }
}
