// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
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
import java.util.Collection;

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

  public static DebuggerSupport @NotNull [] getDebuggerSupports() {
    return EXTENSION_POINT.getExtensions();
  }

  private static final BreakpointPanelProvider<?> EMPTY_PANEL_PROVIDER = new BreakpointPanelProvider<>() {
    @Override
    public void createBreakpointsGroupingRules(Collection collection) {
    }

    @Override
    public void addListener(BreakpointsListener listener, Project project, Disposable disposable) {
    }

    @Override
    public int getPriority() {
      return 0;
    }

    @Nullable
    @Override
    public Object findBreakpoint(@NotNull Project project, @NotNull Document document, int offset) {
      return null;
    }

    @Override
    public @Nullable GutterIconRenderer getBreakpointGutterIconRenderer(Object breakpoint) {
      return null;
    }

    @Override
    public void onDialogClosed(Project project) {
    }

    @Override
    public void provideBreakpointItems(Project project, Collection collection) {
    }
  };

  @NotNull
  public BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return EMPTY_PANEL_PROVIDER;
  }

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

  @NotNull
  public DebuggerActionHandler getAddToInlineWatchesActionHandler() {
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

  /**
   * @deprecated use {@link com.intellij.xdebugger.XDebugSessionListener#breakpointsMuted(boolean)}
   */
  @NotNull
  @Deprecated
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

    @Override
    public boolean isHidden(@NotNull Project project, AnActionEvent event) {
      return true;
    }
  };

  @NotNull
  public MarkObjectActionHandler getMarkObjectHandler() {
    return DISABLED_MARK_HANDLER;
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
