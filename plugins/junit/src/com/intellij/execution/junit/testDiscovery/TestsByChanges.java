// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.testDiscovery;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Pair;

public final class TestsByChanges extends JUnitTestDiscoveryRunnableState  {
  public TestsByChanges(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  protected String getChangeList() {
    return getConfiguration().getPersistentData().getChangeList();
  }

  @Override
  protected Pair<String, String> getPosition() {
    return null;
  }
}
