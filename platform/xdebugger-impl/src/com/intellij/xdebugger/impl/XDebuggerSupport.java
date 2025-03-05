// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.actions.*;
import com.intellij.xdebugger.impl.actions.handlers.*;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointPanelProvider;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class XDebuggerSupport extends DebuggerSupport {
  private final XBreakpointPanelProvider myBreakpointPanelProvider;
  private final XToggleLineBreakpointActionHandler myToggleLineBreakpointActionHandler;
  private final XToggleLineBreakpointActionHandler myToggleTemporaryLineBreakpointActionHandler;
  private final XDebuggerSuspendedActionHandler myStepOverHandler;
  private final XDebuggerSuspendedActionHandler myStepIntoHandler;
  private final XDebuggerSuspendedActionHandler myStepOutHandler;
  private final XDebuggerSuspendedActionHandler myForceStepOverHandler;
  private final XDebuggerSuspendedActionHandler myForceStepIntoHandler;
  private final XDebuggerRunToCursorActionHandler myRunToCursorHandler;
  private final XDebuggerRunToCursorActionHandler myForceRunToCursor;
  private final XDebuggerActionHandler myResumeHandler;
  private final XDebuggerPauseActionHandler myPauseHandler;
  private final XDebuggerSuspendedActionHandler myShowExecutionPointHandler;
  private final XDebuggerEvaluateActionHandler myEvaluateHandler;

  private final XAddToWatchesFromEditorActionHandler myAddToWatchesActionHandler;
  private final XAddToInlineWatchesFromEditorActionHandler myAddToInlineWatchesActionHandler;
  private final DebuggerActionHandler myEvaluateInConsoleActionHandler = new XEvaluateInConsoleFromEditorActionHandler();

  private final DebuggerToggleActionHandler myMuteBreakpointsHandler;
  private final DebuggerActionHandler mySmartStepIntoHandler;
  private final XMarkObjectActionHandler myMarkObjectActionHandler;
  private final EditBreakpointActionHandler myEditBreakpointActionHandler;

  public XDebuggerSupport() {
    myBreakpointPanelProvider = new XBreakpointPanelProvider();
    myToggleLineBreakpointActionHandler = new XToggleLineBreakpointActionHandler(false);
    myToggleTemporaryLineBreakpointActionHandler = new XToggleLineBreakpointActionHandler(true);
    myAddToWatchesActionHandler = new XAddToWatchesFromEditorActionHandler();
    myAddToInlineWatchesActionHandler = new XAddToInlineWatchesFromEditorActionHandler();
    myStepOverHandler = new XDebuggerSuspendedActionHandler() {
      @Override
      protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
        session.stepOver(false);
      }
    };
    myStepIntoHandler = new XDebuggerStepIntoHandler();
    myStepOutHandler = new XDebuggerSuspendedActionHandler() {
      @Override
      protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
        session.stepOut();
      }
    };
    myForceStepOverHandler = new XDebuggerSuspendedActionHandler() {
      @Override
      protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
        session.stepOver(true);
      }
    };
    myForceStepIntoHandler = new XDebuggerSuspendedActionHandler() {
      @Override
      protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
        session.forceStepInto();
      }
    };
    mySmartStepIntoHandler = new XDebuggerSmartStepIntoHandler();
    myRunToCursorHandler = new XDebuggerRunToCursorActionHandler(false);
    myForceRunToCursor = new XDebuggerRunToCursorActionHandler(true);
    myResumeHandler = new XDebuggerActionHandler() {
      @Override
      protected boolean isEnabled(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
        return session.isPaused();
      }

      @Override
      protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
        session.resume();
      }
    };
    myPauseHandler = new XDebuggerPauseActionHandler();
    myShowExecutionPointHandler = new XDebuggerSuspendedActionHandler() {
      @Override
      protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
        session.showExecutionPoint();
      }
    };
    myMuteBreakpointsHandler = new XDebuggerMuteBreakpointsHandler();
    myEvaluateHandler = new XDebuggerEvaluateActionHandler();
    myMarkObjectActionHandler = new XMarkObjectActionHandler();
    myEditBreakpointActionHandler = new XDebuggerEditBreakpointActionHandler();
  }

  @Override
  public @NotNull BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
  }

  @Override
  public @NotNull DebuggerActionHandler getStepOverHandler() {
    return myStepOverHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getStepIntoHandler() {
    return myStepIntoHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getSmartStepIntoHandler() {
    return mySmartStepIntoHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getStepOutHandler() {
    return myStepOutHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getForceStepOverHandler() {
    return myForceStepOverHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getForceStepIntoHandler() {
    return myForceStepIntoHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getRunToCursorHandler() {
    return myRunToCursorHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getForceRunToCursorHandler() {
    return myForceRunToCursor;
  }

  @Override
  public @NotNull DebuggerActionHandler getResumeActionHandler() {
    return myResumeHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getPauseHandler() {
    return myPauseHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getToggleLineBreakpointHandler() {
    return myToggleLineBreakpointActionHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getToggleTemporaryLineBreakpointHandler() {
    return myToggleTemporaryLineBreakpointActionHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getShowExecutionPointHandler() {
    return myShowExecutionPointHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getEvaluateHandler() {
    return myEvaluateHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getAddToWatchesActionHandler() {
    return myAddToWatchesActionHandler;
  }

  @Override
  public @NotNull DebuggerActionHandler getAddToInlineWatchesActionHandler() {
    return myAddToInlineWatchesActionHandler;
  }


  @Override
  public @NotNull DebuggerActionHandler getEvaluateInConsoleActionHandler() {
    return myEvaluateInConsoleActionHandler;
  }

  @Override
  public @NotNull DebuggerToggleActionHandler getMuteBreakpointsHandler() {
    return myMuteBreakpointsHandler;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull MarkObjectActionHandler getMarkObjectHandler() {
    return myMarkObjectActionHandler;
  }

  @Override
  public @NotNull EditBreakpointActionHandler getEditBreakpointAction() {
    return myEditBreakpointActionHandler;
  }
}
