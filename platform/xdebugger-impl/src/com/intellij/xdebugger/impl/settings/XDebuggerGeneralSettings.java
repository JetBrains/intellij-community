// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.settings;

import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.evaluation.EvaluationMode;

/**
 * @author egor
 */
@Tag("general")
public class XDebuggerGeneralSettings {
  private EvaluationMode myEvaluationDialogMode = EvaluationMode.EXPRESSION;
  private boolean myUnmuteOnStop = false;

  private boolean hideDebuggerOnProcessTermination;
  private boolean myShowDebuggerOnBreakpoint = true;
  private boolean myScrollToCenter = false;
  private boolean myConfirmBreakpointRemoval = false;

  @Tag("evaluation-dialog-mode")
  public EvaluationMode getEvaluationDialogMode() {
    return myEvaluationDialogMode;
  }

  public void setEvaluationDialogMode(EvaluationMode evaluationDialogMode) {
    myEvaluationDialogMode = evaluationDialogMode;
  }

  @Tag("unmute-on-stop")
  public boolean isUnmuteOnStop() {
    return myUnmuteOnStop;
  }

  public void setUnmuteOnStop(boolean unmuteOnStop) {
    myUnmuteOnStop = unmuteOnStop;
  }

  public boolean isHideDebuggerOnProcessTermination() {
    return hideDebuggerOnProcessTermination;
  }

  public void setHideDebuggerOnProcessTermination(boolean hideDebuggerOnProcessTermination) {
    this.hideDebuggerOnProcessTermination = hideDebuggerOnProcessTermination;
  }

  public boolean isShowDebuggerOnBreakpoint() {
    return myShowDebuggerOnBreakpoint;
  }

  public void setShowDebuggerOnBreakpoint(boolean showDebuggerOnBreakpoint) {
    this.myShowDebuggerOnBreakpoint = showDebuggerOnBreakpoint;
  }

  @Tag("scroll-to-center")
  public boolean isScrollToCenter() {
    return myScrollToCenter;
  }

  public void setScrollToCenter(boolean scrollToCenter) {
    myScrollToCenter = scrollToCenter;
  }

  @Tag("confirm-breakpoint-removal")
  public boolean isConfirmBreakpointRemoval() {
    return myConfirmBreakpointRemoval;
  }

  public void setConfirmBreakpointRemoval(boolean confirmBreakpointRemoval) {
    myConfirmBreakpointRemoval = confirmBreakpointRemoval;
  }
}
