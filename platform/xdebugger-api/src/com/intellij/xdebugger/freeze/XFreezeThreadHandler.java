// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.freeze;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this class and return its instance from {@link XDebugProcess#getFreezeThreadHandler()} to support
 * Freeze Thread actions
 */
@ApiStatus.Experimental
public interface XFreezeThreadHandler {

  boolean canFreezeSingleThread(@NotNull XDebugSession session, @NotNull DataContext dataContext);

  void freezeSingleThread(@NotNull XDebugSession session, @NotNull DataContext dataContext);

  boolean isFreezeSingleThreadHidden(@NotNull Project project, @Nullable AnActionEvent event);
}
