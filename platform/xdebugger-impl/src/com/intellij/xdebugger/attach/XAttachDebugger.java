/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link XAttachDebugger} allows to attach to process with specified {@link ProcessInfo}
 */
@ApiStatus.Experimental
public interface XAttachDebugger {
  @NotNull
  String getDebuggerDisplayName();

  /**
   * Attaches this debugger to the specified process. The debugger is guaranteed to be
   * returned by {@link XAttachDebuggerProvider#getAvailableDebuggers} for the specified process.
   * @param hostInfo host (environment) on which process is being run
   * @param info process to attach to
   * @throws ExecutionException if an error occurs during attach
   */
  void attachDebugSession(@NotNull Project project,
                          @NotNull XAttachHost hostInfo,
                          @NotNull ProcessInfo info) throws ExecutionException;
}
