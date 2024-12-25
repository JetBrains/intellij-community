// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class XDebugSessionData extends UserDataHolderBase {
  public static final DataKey<XDebugSessionData> DATA_KEY = DataKey.create("XDebugSessionData");

  private final @Nullable Project myProject;
  private final @NotNull String myConfigurationName;
  private boolean myBreakpointsMuted = false;

  /**
   * @deprecated Use {@link XDebugSessionData#XDebugSessionData(Project, String)} instead
   */
  @Deprecated
  public XDebugSessionData(@NotNull List<XExpression> watchExpressions,
                           @NotNull String configurationName) {
    myProject = null;
    myConfigurationName = configurationName;
  }

  @ApiStatus.Internal
  public XDebugSessionData(@NotNull Project project,
                           @NotNull String configurationName) {
    myProject = project;
    myConfigurationName = configurationName;
  }

  public void setWatchExpressions(@NotNull List<XExpression> watchExpressions) {
    if (myProject == null) return;
    XDebuggerManagerImpl instance = (XDebuggerManagerImpl)XDebuggerManager.getInstance(myProject);
    instance.getWatchesManager().setWatches(myConfigurationName, watchExpressions);
  }

  public @NotNull List<XExpression> getWatchExpressions() {
    if (myProject == null) return Collections.emptyList();
    XDebuggerManagerImpl instance = (XDebuggerManagerImpl)XDebuggerManager.getInstance(myProject);
    return instance.getWatchesManager().getWatches(myConfigurationName);
  }

  public boolean isBreakpointsMuted() {
    return myBreakpointsMuted;
  }

  public void setBreakpointsMuted(boolean breakpointsMuted) {
    myBreakpointsMuted = breakpointsMuted;
  }

  public @NotNull String getConfigurationName() {
    return myConfigurationName;
  }
}
