// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6;

import com.intellij.junit5.JUnit5IdeaTestRunner;
import com.intellij.junit5.JUnit5TestRunnerHelper;

public final class JUnit6IdeaTestRunner extends JUnit5IdeaTestRunner {
  private final JUnit5TestRunnerHelper myHelper = new JUnit6TestRunnerHelper();

  @Override
  protected JUnit5TestRunnerHelper getHelper() {
    return myHelper;
  }
}