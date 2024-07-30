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
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

@Deprecated
public abstract class DebuggerSupport {
  private static final ExtensionPointName<DebuggerSupport> EXTENSION_POINT = new ExtensionPointName<>("com.intellij.xdebugger.debuggerSupport");

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

  @ApiStatus.Internal
  public static @NotNull List<@NotNull DebuggerSupport> getDebuggerSupports() {
    return EXTENSION_POINT.getExtensionList();
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

    @Override
    public @Nullable Object findBreakpoint(@NotNull Project project, @NotNull Document document, int offset) {
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
    public void provideBreakpointItems(Project project, Collection<? super BreakpointItem> collection) {
    }
  };

  public @NotNull BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return EMPTY_PANEL_PROVIDER;
  }

  public @NotNull DebuggerActionHandler getStepOverHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getStepIntoHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getSmartStepIntoHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getStepOutHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getForceStepOverHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getForceStepIntoHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getRunToCursorHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getForceRunToCursorHandler() {
    return DisabledActionHandler.INSTANCE;
  }


  public @NotNull DebuggerActionHandler getResumeActionHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getPauseHandler() {
    return DisabledActionHandler.INSTANCE;
  }


  public @NotNull DebuggerActionHandler getToggleLineBreakpointHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getToggleTemporaryLineBreakpointHandler() {
    return DisabledActionHandler.INSTANCE;
  }


  public @NotNull DebuggerActionHandler getShowExecutionPointHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getEvaluateHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull QuickEvaluateHandler getQuickEvaluateHandler() {
    return DISABLED_QUICK_EVALUATE;
  }

  private static final QuickEvaluateHandler DISABLED_QUICK_EVALUATE = new QuickEvaluateHandler() {
    @Override
    public boolean isEnabled(@NotNull Project project) {
      return false;
    }

    @Override
    public @Nullable AbstractValueHint createValueHint(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, ValueHintType type) {
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

  public @NotNull DebuggerActionHandler getAddToWatchesActionHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  public @NotNull DebuggerActionHandler getAddToInlineWatchesActionHandler() {
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
  @Deprecated
  public @NotNull DebuggerToggleActionHandler getMuteBreakpointsHandler() {
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

  public @NotNull MarkObjectActionHandler getMarkObjectHandler() {
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

  public @NotNull EditBreakpointActionHandler getEditBreakpointAction() {
    return DISABLED_EDIT;
  }


  public static @NotNull <T extends DebuggerSupport> DebuggerSupport getDebuggerSupport(Class<T> aClass) {
    for (DebuggerSupport support : getDebuggerSupports()) {
      if (support.getClass() == aClass) {
        return support;
      }
    }
    throw new IllegalStateException();
  }
}
