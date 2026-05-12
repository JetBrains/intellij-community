// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.debugger.impl.shared.CoroutineUtilsKt.createMutableStateFlow;

public class XDebugSessionData extends UserDataHolderBase {
  public static final DataKey<XDebugSessionData> DATA_KEY = DataKey.create("XDebugSessionData");
  /**
   * Prevents a breakpoint pause in this session from attracting the debugger UI/tool window.
   * Put this key into the corresponding {@code XDebugSessionImpl.sessionData}.
   * Used by backend-driven debugger control flows that still need normal pause state updates.
   */
  @ApiStatus.Internal
  public static final Key<Boolean> SUPPRESS_BREAKPOINT_ATTRACTION = Key.create("XDebugSessionData.SuppressBreakpointAttraction");

  private final @NotNull String myConfigurationName;
  private final MutableStateFlow<Boolean> myBreakpointsMutedFlow = createMutableStateFlow(false);
  private final MutableStateFlow<Boolean> myPauseSupported = createMutableStateFlow(false);

  @ApiStatus.Internal
  public XDebugSessionData(@NotNull String configurationName) {
    myConfigurationName = configurationName;
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
