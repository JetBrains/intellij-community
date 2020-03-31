// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.execution.DefaultExecutionTarget;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.configurations.RunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MockExecutionTargetManager extends ExecutionTargetManager {
  private ExecutionTarget myTarget = DefaultExecutionTarget.INSTANCE;

  @NotNull
  @Override
  public ExecutionTarget getActiveTarget() {
    return myTarget;
  }

  @Override
  public void setActiveTarget(@NotNull ExecutionTarget target) {
    myTarget = target;
  }

  @NotNull
  @Override
  public List<ExecutionTarget> getTargetsFor(@Nullable RunConfiguration settings) {
    return Collections.singletonList(DefaultExecutionTarget.INSTANCE);
  }

  @Override
  public boolean doCanRun(@Nullable RunConfiguration settings, @NotNull ExecutionTarget target) {
    return true;
  }

  @Override
  public void update() {
  }
}
