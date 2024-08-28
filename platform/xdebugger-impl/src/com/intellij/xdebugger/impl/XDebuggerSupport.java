// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.actions.*;
import com.intellij.xdebugger.impl.actions.handlers.*;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointPanelProvider;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import org.jetbrains.annotations.ApiStatus;
import com.intellij.xdebugger.impl.evaluate.quick.XQuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
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
  private final DebuggerActionHandler myFreezeThreadHandler;

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
    myFreezeThreadHandler = new DebuggerThreadActionHandler(provider -> provider.getFreezeThreadHandler());
  }

  @Override
  @NotNull
  public BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getStepOverHandler() {
    return myStepOverHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getStepIntoHandler() {
    return myStepIntoHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getSmartStepIntoHandler() {
    return mySmartStepIntoHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getStepOutHandler() {
    return myStepOutHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getForceStepOverHandler() {
    return myForceStepOverHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getForceStepIntoHandler() {
    return myForceStepIntoHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getRunToCursorHandler() {
    return myRunToCursorHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getForceRunToCursorHandler() {
    return myForceRunToCursor;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getResumeActionHandler() {
    return myResumeHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getPauseHandler() {
    return myPauseHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getToggleLineBreakpointHandler() {
    return myToggleLineBreakpointActionHandler;
  }

  @NotNull
  @Override
  public DebuggerActionHandler getToggleTemporaryLineBreakpointHandler() {
    return myToggleTemporaryLineBreakpointActionHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getShowExecutionPointHandler() {
    return myShowExecutionPointHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getEvaluateHandler() {
    return myEvaluateHandler;
  }

  @NotNull
  @Override
  public DebuggerActionHandler getAddToWatchesActionHandler() {
    return myAddToWatchesActionHandler;
  }

  @NotNull
  @Override
  public DebuggerActionHandler getAddToInlineWatchesActionHandler() {
    return myAddToInlineWatchesActionHandler;
  }


  @NotNull
  @Override
  public DebuggerActionHandler getEvaluateInConsoleActionHandler() {
    return myEvaluateInConsoleActionHandler;
  }

  @Override
  @NotNull
  public DebuggerToggleActionHandler getMuteBreakpointsHandler() {
    return myMuteBreakpointsHandler;
  }

  @ApiStatus.Internal
  @NotNull
  @Override
  public MarkObjectActionHandler getMarkObjectHandler() {
    return myMarkObjectActionHandler;
  }

  @NotNull
  @Override
  public EditBreakpointActionHandler getEditBreakpointAction() {
    return myEditBreakpointActionHandler;
  }

  @NotNull
  @Override
  public DebuggerActionHandler getFreezeThreadHandler() {
    return myFreezeThreadHandler;
  }
}
