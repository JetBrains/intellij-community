/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.xdebugger.XExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class XDebugSessionData extends UserDataHolderBase {
  public static final DataKey<XDebugSessionData> DATA_KEY = DataKey.create("XDebugSessionData");

  @NotNull
  private List<XExpression> myWatchExpressions;
  private final String myConfigurationName;
  private boolean myBreakpointsMuted = false;

  public XDebugSessionData(@NotNull List<XExpression> watchExpressions, @NotNull String configurationName) {
    myWatchExpressions = watchExpressions;
    myConfigurationName = configurationName;
  }

  public void setWatchExpressions(@NotNull List<XExpression> watchExpressions) {
    myWatchExpressions = watchExpressions;
  }

  @NotNull
  public List<XExpression> getWatchExpressions() {
    return myWatchExpressions;
  }

  public boolean isBreakpointsMuted() {
    return myBreakpointsMuted;
  }

  public void setBreakpointsMuted(boolean breakpointsMuted) {
    myBreakpointsMuted = breakpointsMuted;
  }

  @NotNull
  public String getConfigurationName() {
    return myConfigurationName;
  }
}
