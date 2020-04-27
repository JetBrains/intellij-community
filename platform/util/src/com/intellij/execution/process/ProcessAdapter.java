// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public abstract class ProcessAdapter implements ProcessListener {
  @Override
  public void startNotified(@NotNull ProcessEvent event) { }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) { }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) { }
}