// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface XLocalAttachDebugger extends XAttachDebugger {
  @Override
  @NotNull
  String getDebuggerDisplayName();

  void attachDebugSession(@NotNull Project project,
                          @NotNull ProcessInfo processInfo) throws ExecutionException;

  @Override
  default void attachDebugSession(@NotNull Project project,
                                  @NotNull XAttachHost attachHost,
                                  @NotNull ProcessInfo processInfo) throws ExecutionException {
    attachDebugSession(project, processInfo);
  }
}
