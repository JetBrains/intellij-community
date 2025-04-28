// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.actions.EditBreakpointAction;
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public final class BreakpointGutterIconRenderer extends CommonBreakpointGutterIconRenderer implements DumbAware {
  private final XBreakpointProxy myBreakpoint;

  @ApiStatus.Internal
  public BreakpointGutterIconRenderer(XBreakpointProxy breakpoint) { myBreakpoint = breakpoint; }

  @Override
  public @NotNull Icon getIcon() {
    return myBreakpoint.getIcon();
  }

  @Override
  public @NotNull String getAccessibleName() {
    // [tav] todo: add "hit" state
    return XDebuggerBundle.message("accessible.name.icon.0.1.2", myBreakpoint.getType().getTitle(),
                                   myBreakpoint.getConditionExpression() != null
                                   ? " " + XDebuggerBundle.message("accessible.name.icon.conditional")
                                   : "",
                                   !myBreakpoint.isEnabled() ? " " + XDebuggerBundle.message("accessible.name.icon.disabled") : "");
  }

  @Override
  public @NotNull AnAction getClickAction() {
    if (Registry.is("debugger.click.disable.breakpoints")) {
      return new ToggleBreakpointGutterIconAction(myBreakpoint);
    }
    else {
      return new RemoveBreakpointGutterIconAction(myBreakpoint);
    }
  }

  @Override
  public @NotNull AnAction getMiddleButtonClickAction() {
    if (!Registry.is("debugger.click.disable.breakpoints")) {
      return new ToggleBreakpointGutterIconAction(myBreakpoint);
    }
    else {
      return new RemoveBreakpointGutterIconAction(myBreakpoint);
    }
  }

  @Override
  public @NotNull AnAction getRightButtonClickAction() {
    return new EditBreakpointAction.ContextAction(this, myBreakpoint);
  }

  @Override
  public @Nullable ActionGroup getPopupMenuActions() {
    XDebugSessionProxy currentSessionProxy = XDebugManagerProxy.getInstance().getCurrentSessionProxy(myBreakpoint.getProject());
    // TODO IJPL-185322
    if (myBreakpoint instanceof XBreakpointProxy.Monolith monolithBreakpoint
        && currentSessionProxy instanceof XDebugSessionProxy.Monolith sessionProxy) {
      return new DefaultActionGroup(monolithBreakpoint.getBreakpoint().getAdditionalPopupMenuActions(sessionProxy.getSession()));
    }
    return super.getPopupMenuActions();
  }

  @Override
  public @NotNull String getTooltipText() {
    return myBreakpoint.getTooltipDescription();
  }

  @Override
  public GutterDraggableObject getDraggableObject() {
    return myBreakpoint.createBreakpointDraggableObject();
  }

  XBreakpointProxy getBreakpoint() {
    return myBreakpoint;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof BreakpointGutterIconRenderer renderer
           && myBreakpoint.equals(renderer.myBreakpoint)
           && Comparing.equal(getIcon(), renderer.getIcon());
  }

  @Override
  public int hashCode() {
    return getBreakpoint().hashCode();
  }
}
