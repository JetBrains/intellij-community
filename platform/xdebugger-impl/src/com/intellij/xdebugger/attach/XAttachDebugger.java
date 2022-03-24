// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link XAttachDebugger} allows to attach to process with specified {@link ProcessInfo}
 */
public interface XAttachDebugger {
  @NotNull
  @Nls
  String getDebuggerDisplayName();

  /**
   * @return title for `Attach to process` module window, which will be shown when choosing this debugger
   */
  @Nullable
  default @NlsContexts.PopupTitle String getDebuggerSelectedTitle() {
    String title = getDebuggerDisplayName();
    title = StringUtil.shortenTextWithEllipsis(title, 50, 0);
    return XDebuggerBundle.message("xdebugger.attach.popup.title", title);
  }

  /**
   * Attaches this debugger to the specified process. The debugger is guaranteed to be
   * returned by {@link XAttachDebuggerProvider#getAvailableDebuggers} for the specified process.
   *
   * @param attachHost  host (environment) on which process is being run
   * @param processInfo process to attach to
   * @throws ExecutionException if an error occurs during attach
   */
  void attachDebugSession(@NotNull Project project,
                          @NotNull XAttachHost attachHost,
                          @NotNull ProcessInfo processInfo) throws ExecutionException;
}
