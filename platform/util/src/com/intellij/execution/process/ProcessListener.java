// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ProcessListener extends EventListener {
  default void startNotified(@NotNull ProcessEvent event) {
  }

  default void processTerminated(@NotNull ProcessEvent event) {
  }

  default void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
  }

  default void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
  }
}