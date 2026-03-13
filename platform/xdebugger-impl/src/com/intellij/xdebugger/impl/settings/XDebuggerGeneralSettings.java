// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings;

import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import org.jetbrains.annotations.NotNull;

@Tag("general")
public class XDebuggerGeneralSettings implements XDebuggerSettingsManager.GeneralSettings {
  private EvaluationMode myEvaluationDialogMode = EvaluationMode.EXPRESSION;
  private boolean myUnmuteOnStop = false;

  private boolean hideDebuggerOnProcessTermination;
  private boolean myShowDebuggerOnBreakpoint = true;
  private boolean myScrollToCenter = false;
  private boolean myConfirmBreakpointRemoval = false;
  private boolean myRunToCursorGesture = true;

  @Override
  @Tag("evaluation-dialog-mode")
  public @NotNull EvaluationMode getEvaluationDialogMode() {
    return myEvaluationDialogMode;
  }

  @Override
  public void setEvaluationDialogMode(@NotNull EvaluationMode evaluationDialogMode) {
    myEvaluationDialogMode = evaluationDialogMode;
  }

  @Override
  @Tag("unmute-on-stop")
  public boolean isUnmuteOnStop() {
    return myUnmuteOnStop;
  }

  @Override
  public void setUnmuteOnStop(boolean unmuteOnStop) {
    myUnmuteOnStop = unmuteOnStop;
  }

  @Override
  public boolean isHideDebuggerOnProcessTermination() {
    return hideDebuggerOnProcessTermination;
  }

  @Override
  public void setHideDebuggerOnProcessTermination(boolean hideDebuggerOnProcessTermination) {
    this.hideDebuggerOnProcessTermination = hideDebuggerOnProcessTermination;
  }

  @Override
  public boolean isShowDebuggerOnBreakpoint() {
    return myShowDebuggerOnBreakpoint;
  }

  @Override
  public void setShowDebuggerOnBreakpoint(boolean showDebuggerOnBreakpoint) {
    this.myShowDebuggerOnBreakpoint = showDebuggerOnBreakpoint;
  }

  @Override
  @Tag("scroll-to-center")
  public boolean isScrollToCenter() {
    return myScrollToCenter;
  }

  @Override
  public void setScrollToCenter(boolean scrollToCenter) {
    myScrollToCenter = scrollToCenter;
  }

  @Tag("confirm-breakpoint-removal")
  @Override
  public boolean isConfirmBreakpointRemoval() {
    return myConfirmBreakpointRemoval;
  }

  @Override
  public void setConfirmBreakpointRemoval(boolean confirmBreakpointRemoval) {
    myConfirmBreakpointRemoval = confirmBreakpointRemoval;
  }

  @Override
  @Tag("run-to-cursor-gesture")
  public boolean isRunToCursorGestureEnabled() {
    return myRunToCursorGesture;
  }

  @Override
  public void setRunToCursorGestureEnabled(boolean runToCursorGesture) {
    myRunToCursorGesture = runToCursorGesture;
  }
}
