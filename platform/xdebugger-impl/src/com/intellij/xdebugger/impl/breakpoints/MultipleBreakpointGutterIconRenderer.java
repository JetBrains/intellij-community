// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory;
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

class MultipleBreakpointGutterIconRenderer extends CommonBreakpointGutterIconRenderer implements DumbAware {

  private final List<XBreakpointProxy> breakpoints;

  MultipleBreakpointGutterIconRenderer(List<XBreakpointProxy> breakpoints) {
    this.breakpoints = breakpoints;
    assert breakpoints.size() >= 2;
  }

  private boolean areAllDisabled() {
    return ContainerUtil.and(breakpoints, b -> !b.isEnabled());
  }

  @Override
  public @NotNull Icon getIcon() {
    var session = XDebugManagerProxy.getInstance().getCurrentSessionProxy(breakpoints.get(0).getProject());
    if (session != null && session.areBreakpointsMuted()) {
      return AllIcons.Debugger.MultipleBreakpointsMuted;
    }
    else if (areAllDisabled()) {
      return AllIcons.Debugger.MultipleBreakpointsDisabled;
    }
    else {
      return AllIcons.Debugger.MultipleBreakpoints;
    }
  }

  @Override
  public @NotNull String getAccessibleName() {
    return super.getAccessibleName();
  }

  private AnAction createToggleAction() {
    // This gutter's actions are not collected to any menu, so we use SimpleAction.
    return DumbAwareAction.create(e -> {
      // Semantics:
      // - disable all if any is enabled,
      // - enable all if all are disabled.
      var newEnabledValue = areAllDisabled();
      for (var b : breakpoints) {
        b.setEnabled(newEnabledValue);
      }
    });
  }

  private AnAction createRemoveAction() {
    // This gutter's actions are not collected to any menu, so we use SimpleAction.
    return DumbAwareAction.create(e -> {
      removeBreakpoints();
    });
  }

  private void removeBreakpoints() {
    XDebuggerUtilImpl.removeBreakpointsWithConfirmation(breakpoints);
  }

  @Override
  public @Nullable AnAction getClickAction() {
    if (Registry.is("debugger.click.disable.breakpoints")) {
      return createToggleAction();
    }
    else {
      return createRemoveAction();
    }
  }

  @Override
  public @Nullable AnAction getMiddleButtonClickAction() {
    if (!Registry.is("debugger.click.disable.breakpoints")) {
      return createToggleAction();
    }
    else {
      return createRemoveAction();
    }
  }

  @Override
  public @Nullable AnAction getRightButtonClickAction() {
    // This gutter's actions are not collected to any menu, so we use SimpleAction.
    return DumbAwareAction.create(e -> {
      var project = e.getProject();
      if (project == null) return;
      // Initially we select the newest breakpoint, it's shown above other breakpoints in the dialog.
      @SuppressWarnings("OptionalGetWithoutIsPresent") // there are always at least two breakpoints
      var initialOne = breakpoints.stream().sorted().findFirst().get();
      BreakpointsDialogFactory.getInstance(project).showDialog(initialOne.getId());
    });
  }

  @Override
  public @Nullable ActionGroup getPopupMenuActions() {
    // TODO[inline-bp]: show some menu with the list of all breakpoints with some actions for them (remove, edit, ...)
    // TODO[inline-bp]: alt+enter actions are completely broken for multiple breakpoints:
    //                   all actions are mixed and it's hard to separate them
    //                   and it's non trivial to add batch actions "toggle all", "remove all", ...
    //                   see GutterIntentionMenuContributor.collectActions.
    //                   Moreover it might be a good idea to show breakpoint actions on alt+enter only if cursor is in breakpoint's range
    return super.getPopupMenuActions();
  }

  @Override
  public @Nullable String getTooltipText() {
    return XDebuggerBundle.message("xbreakpoint.tooltip.multiple");
  }

  @Override
  public GutterDraggableObject getDraggableObject() {
    return new GutterDraggableObject() {
      @Override
      public boolean copy(int line, VirtualFile file, int actionId) {
        return false; // It's too hard, no copying, please.
      }

      @Override
      public void remove() {
        removeBreakpoints();
      }
    };
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof MultipleBreakpointGutterIconRenderer that
           && this.breakpoints.equals(that.breakpoints);
  }

  @Override
  public int hashCode() {
    return breakpoints.hashCode();
  }
}
