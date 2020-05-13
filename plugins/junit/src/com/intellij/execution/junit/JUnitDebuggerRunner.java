// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.JavaTestFrameworkDebuggerRunner;
import com.intellij.execution.configurations.RunProfile;
import org.jetbrains.annotations.NotNull;

public class JUnitDebuggerRunner extends JavaTestFrameworkDebuggerRunner {
  @Override
  protected boolean validForProfile(@NotNull RunProfile profile) {
    return profile instanceof JUnitConfiguration;
  }

  @NotNull
  @Override
  protected String getThreadName() {
    return "junit";
  }

  @NotNull
  @Override
  public String getRunnerId() {
    return "JUnitDebug";
  }
}
