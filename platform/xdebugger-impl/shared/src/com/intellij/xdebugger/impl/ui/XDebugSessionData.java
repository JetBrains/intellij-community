// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.xdebugger.XExpression;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.intellij.platform.debugger.impl.shared.CoroutineUtilsKt.createMutableStateFlow;

public class XDebugSessionData extends UserDataHolderBase {
  public static final DataKey<XDebugSessionData> DATA_KEY = DataKey.create("XDebugSessionData");

  private final @NotNull String myConfigurationName;
  private final MutableStateFlow<Boolean> myBreakpointsMutedFlow = createMutableStateFlow(false);
  private final MutableStateFlow<Boolean> myPauseSupported = createMutableStateFlow(false);

  @ApiStatus.Internal
  public XDebugSessionData(@NotNull String configurationName) {
    myConfigurationName = configurationName;
  }

  /**
   * @deprecated Use {@link XDebuggerWatchesManager#setWatchEntries(String, List)} instead
   */
  @Deprecated(forRemoval = true)
  public void setWatchExpressions(@NotNull List<XExpression> ignoredWatchExpressions) {
    Logger.getInstance(XDebugSessionData.class).error("setWatchExpressions is deprecated");
  }

  /**
   * @deprecated Use {@link XDebuggerWatchesManager#getWatchEntries(String)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull List<XExpression> getWatchExpressions() {
    Logger.getInstance(XDebugSessionData.class).error("setWatchExpressions is deprecated");
    return Collections.emptyList();
  }

  public boolean isBreakpointsMuted() {
    return myBreakpointsMutedFlow.getValue();
  }

  public void setBreakpointsMuted(boolean breakpointsMuted) {
    myBreakpointsMutedFlow.setValue(breakpointsMuted);
  }

  @ApiStatus.Internal
  public StateFlow<Boolean> getBreakpointsMutedFlow() {
    return myBreakpointsMutedFlow;
  }

  public @NotNull String getConfigurationName() {
    return myConfigurationName;
  }

  @ApiStatus.Internal
  public boolean isPauseSupported() {
    return myPauseSupported.getValue();
  }

  @ApiStatus.Internal
  public void setPauseSupported(boolean pauseSupported) {
    myPauseSupported.setValue(pauseSupported);
  }
}
