/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.AbstractDebuggerSession;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.actions.handlers.*;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointPanelProvider;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.XQuickEvaluateHandler;
import com.intellij.xdebugger.impl.settings.DebuggerSettingsPanelProvider;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingsPanelProviderImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XDebuggerSupport extends DebuggerSupport {
  private final XBreakpointPanelProvider myBreakpointPanelProvider;
  private final XToggleLineBreakpointActionHandler myToggleLineBreakpointActionHandler;
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
  private final XQuickEvaluateHandler myQuickEvaluateHandler;
  private final XDebuggerSettingsPanelProviderImpl mySettingsPanelProvider;
  private final XAddToWatchesFromEditorActionHandler myAddToWatchesActionHandler;
  private final DebuggerToggleActionHandler myMuteBreakpointsHandler;
  private final DebuggerActionHandler mySmartStepIntoHandler;

  public XDebuggerSupport() {
    myBreakpointPanelProvider = new XBreakpointPanelProvider();
    myToggleLineBreakpointActionHandler = new XToggleLineBreakpointActionHandler();
    myAddToWatchesActionHandler = new XAddToWatchesFromEditorActionHandler();
    myStepOverHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.stepOver(false);
      }
    };
    myStepIntoHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.stepInto();
      }
    };
    myStepOutHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.stepOut();
      }
    };
    myForceStepOverHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.stepOver(true);
      }
    };
    myForceStepIntoHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.forceStepInto();
      }
    };
    mySmartStepIntoHandler = new XDebuggerSmartStepIntoHandler();
    myRunToCursorHandler = new XDebuggerRunToCursorActionHandler(false);
    myForceRunToCursor = new XDebuggerRunToCursorActionHandler(true);
    myResumeHandler = new XDebuggerActionHandler() {
      protected boolean isEnabled(@NotNull final XDebugSession session, final DataContext dataContext) {
        return session.isPaused();
      }

      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.resume();
      }
    };
    myPauseHandler = new XDebuggerPauseActionHandler();
    myShowExecutionPointHandler = new XDebuggerSuspendedActionHandler() {
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.showExecutionPoint();
      }
    };
    myMuteBreakpointsHandler = new XDebuggerMuteBreakpointsHandler();
    myEvaluateHandler = new XDebuggerEvaluateActionHandler();
    myQuickEvaluateHandler = new XQuickEvaluateHandler();
    mySettingsPanelProvider = new XDebuggerSettingsPanelProviderImpl();
  }

  @NotNull
  public BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
  }

  @NotNull
  public DebuggerActionHandler getStepOverHandler() {
    return myStepOverHandler;
  }

  @NotNull
  public DebuggerActionHandler getStepIntoHandler() {
    return myStepIntoHandler;
  }

  @NotNull
  public DebuggerActionHandler getSmartStepIntoHandler() {
    return mySmartStepIntoHandler;
  }

  @NotNull
  public DebuggerActionHandler getStepOutHandler() {
    return myStepOutHandler;
  }

  @NotNull
  public DebuggerActionHandler getForceStepOverHandler() {
    return myForceStepOverHandler;
  }

  @NotNull
  public DebuggerActionHandler getForceStepIntoHandler() {
    return myForceStepIntoHandler;
  }

  @NotNull
  public DebuggerActionHandler getRunToCursorHandler() {
    return myRunToCursorHandler;
  }

  @NotNull
  public DebuggerActionHandler getForceRunToCursorHandler() {
    return myForceRunToCursor;
  }

  @NotNull
  public DebuggerActionHandler getResumeActionHandler() {
    return myResumeHandler;
  }

  @NotNull
  public DebuggerActionHandler getPauseHandler() {
    return myPauseHandler;
  }

  @NotNull
  public DebuggerActionHandler getToggleLineBreakpointHandler() {
    return myToggleLineBreakpointActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getShowExecutionPointHandler() {
    return myShowExecutionPointHandler;
  }

  @NotNull
  public DebuggerActionHandler getEvaluateHandler() {
    return myEvaluateHandler;
  }

  @NotNull
  public QuickEvaluateHandler getQuickEvaluateHandler() {
    return myQuickEvaluateHandler;
  }

  @NotNull
  @Override
  public DebuggerActionHandler getAddToWatchesActionHandler() {
    return myAddToWatchesActionHandler;
  }

  @NotNull
  public DebuggerToggleActionHandler getMuteBreakpointsHandler() {
    return myMuteBreakpointsHandler;
  }

  @Override
  public AbstractDebuggerSession getCurrentSession(@NotNull Project project) {
    return XDebuggerManagerImpl.getInstance(project).getCurrentSession();
  }

  @NotNull
  public DebuggerSettingsPanelProvider getSettingsPanelProvider() {
    return mySettingsPanelProvider;
  }

}
