/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.AbstractDebuggerSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.actions.EditBreakpointActionHandler;
import com.intellij.xdebugger.impl.actions.MarkObjectActionHandler;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
@Deprecated
public abstract class DebuggerSupport {
  private static final ExtensionPointName<DebuggerSupport> EXTENSION_POINT = ExtensionPointName.create("com.intellij.xdebugger.debuggerSupport");

  protected static final class DisabledActionHandler extends DebuggerActionHandler {
    public static final DisabledActionHandler INSTANCE = new DisabledActionHandler();

    @Override
    public void perform(@NotNull Project project, AnActionEvent event) {
    }

    @Override
    public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
      return false;
    }
  }

  @NotNull
  public static DebuggerSupport[] getDebuggerSupports() {
    return Extensions.getExtensions(EXTENSION_POINT);
  }

  @NotNull
  public abstract BreakpointPanelProvider<?> getBreakpointPanelProvider();

  @NotNull
  public DebuggerActionHandler getStepOverHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public DebuggerActionHandler getStepIntoHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public DebuggerActionHandler getSmartStepIntoHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public DebuggerActionHandler getStepOutHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public DebuggerActionHandler getForceStepOverHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public DebuggerActionHandler getForceStepIntoHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public DebuggerActionHandler getRunToCursorHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public DebuggerActionHandler getForceRunToCursorHandler() {
    return DisabledActionHandler.INSTANCE;
  }


  @NotNull
  public DebuggerActionHandler getResumeActionHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public DebuggerActionHandler getPauseHandler() {
    return DisabledActionHandler.INSTANCE;
  }


  @NotNull
  public DebuggerActionHandler getToggleLineBreakpointHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public DebuggerActionHandler getToggleTemporaryLineBreakpointHandler() {
    return DisabledActionHandler.INSTANCE;
  }


  @NotNull
  public DebuggerActionHandler getShowExecutionPointHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public DebuggerActionHandler getEvaluateHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  public QuickEvaluateHandler getQuickEvaluateHandler() {
    return DISABLED_QUICK_EVALUATE;
  }

  private static final QuickEvaluateHandler DISABLED_QUICK_EVALUATE = new QuickEvaluateHandler() {
    @Override
    public boolean isEnabled(@NotNull Project project) {
      return false;
    }

    @Nullable
    @Override
    public AbstractValueHint createValueHint(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, ValueHintType type) {
      return null;
    }

    @Override
    public boolean canShowHint(@NotNull Project project) {
      return false;
    }

    @Override
    public int getValueLookupDelay(Project project) {
      return 0;
    }
  };

  @NotNull
  public DebuggerActionHandler getAddToWatchesActionHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public DebuggerActionHandler getEvaluateInConsoleActionHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  protected static final DebuggerToggleActionHandler DISABLED_TOGGLE_HANDLER = new DebuggerToggleActionHandler() {
    @Override
    public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
      return false;
    }

    @Override
    public boolean isSelected(@NotNull Project project, AnActionEvent event) {
      return false;
    }

    @Override
    public void setSelected(@NotNull Project project, AnActionEvent event, boolean state) {
    }
  };

  @NotNull
  public DebuggerToggleActionHandler getMuteBreakpointsHandler() {
    return DISABLED_TOGGLE_HANDLER;
  }

  protected static final MarkObjectActionHandler DISABLED_MARK_HANDLER = new MarkObjectActionHandler() {
    @Override
    public boolean isMarked(@NotNull Project project, @NotNull AnActionEvent event) {
      return false;
    }

    @Override
    public void perform(@NotNull Project project, AnActionEvent event) {
    }

    @Override
    public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
      return false;
    }
  };

  @NotNull
  public MarkObjectActionHandler getMarkObjectHandler() {
    return DISABLED_MARK_HANDLER;
  }

  /**
   * @deprecated {@link XDebuggerManager#getCurrentSession()} is used instead
   */
  @Nullable
  @Deprecated
  public AbstractDebuggerSession getCurrentSession(@NotNull Project project) {
    return null;
  }

  protected static final EditBreakpointActionHandler DISABLED_EDIT = new EditBreakpointActionHandler() {
    @Override
    protected void doShowPopup(Project project, JComponent component, Point whereToShow, Object breakpoint) {
    }

    @Override
    public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
      return false;
    }
  };

  @NotNull
  public EditBreakpointActionHandler getEditBreakpointAction() {
    return DISABLED_EDIT;
  }


  @NotNull
  public static <T extends DebuggerSupport> DebuggerSupport getDebuggerSupport(Class<T> aClass) {
    for (DebuggerSupport support : getDebuggerSupports()) {
      if (support.getClass() == aClass) {
        return support;
      }
    }
    throw new IllegalStateException();
  }
}
