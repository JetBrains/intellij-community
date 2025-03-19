// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.JavaTestFrameworkDebuggerRunner;
import com.intellij.execution.configurations.RunProfile;
import org.jetbrains.annotations.NotNull;

public class JUnitDebuggerRunner extends JavaTestFrameworkDebuggerRunner {
  @Override
  protected boolean validForProfile(@NotNull RunProfile profile) {
    return profile instanceof JUnitConfiguration;
  }

  @Override
  protected @NotNull String getThreadName() {
    return "junit";
  }

  @Override
  public @NotNull String getRunnerId() {
    return "JUnitDebug";
  }
}
