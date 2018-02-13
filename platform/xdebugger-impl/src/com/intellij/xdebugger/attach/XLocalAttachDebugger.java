// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link XAttachDebugger} instead
 */
public interface XLocalAttachDebugger extends XAttachDebugger {

  /**
   * @deprecated use {@link XAttachDebugger#attachDebugSession(Project, XAttachHost, ProcessInfo)} instead
   */
  void attachDebugSession(@NotNull Project project,
                          @NotNull ProcessInfo info) throws ExecutionException;

  default void attachDebugSession(@NotNull Project project,
                          @NotNull XAttachHost hostInfo,
                          @NotNull ProcessInfo info) throws ExecutionException {
    if(hostInfo instanceof LocalAttachHost)
      attachDebugSession(project, info);
  }
}
