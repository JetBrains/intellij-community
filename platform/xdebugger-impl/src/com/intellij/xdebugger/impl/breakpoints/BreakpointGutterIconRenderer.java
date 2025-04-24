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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class BreakpointGutterIconRenderer extends CommonBreakpointGutterIconRenderer implements DumbAware {
  private final XBreakpointBase<?, ?, ?> myBreakpoint;

  BreakpointGutterIconRenderer(XBreakpointBase<?, ?, ?> breakpoint) { myBreakpoint = breakpoint; }

  @Override
  public @NotNull Icon getIcon() {
    return myBreakpoint.getIcon();
  }

  @Override
  public @NotNull String getAccessibleName() {
    // [tav] todo: add "hit" state
    return XDebuggerBundle.message("accessible.name.icon.0.1.2", myBreakpoint.getType().getTitle(),
                                   myBreakpoint.getCondition() != null
                                   ? " " + XDebuggerBundle.message("accessible.name.icon.conditional")
                                   : "",
                                   !myBreakpoint.isEnabled() ? " " + XDebuggerBundle.message("accessible.name.icon.disabled") : "");
  }

  @Override
  public @NotNull AnAction getClickAction() {
    if (Registry.is("debugger.click.disable.breakpoints")) {
      return new ToggleBreakpointGutterIconAction(XBreakpointProxyKt.asProxy(myBreakpoint));
    }
    else {
      return new RemoveBreakpointGutterIconAction(XBreakpointProxyKt.asProxy(myBreakpoint));
    }
  }

  @Override
  public @NotNull AnAction getMiddleButtonClickAction() {
    if (!Registry.is("debugger.click.disable.breakpoints")) {
      return new ToggleBreakpointGutterIconAction(XBreakpointProxyKt.asProxy(myBreakpoint));
    }
    else {
      return new RemoveBreakpointGutterIconAction(XBreakpointProxyKt.asProxy(myBreakpoint));
    }
  }

  @Override
  public @NotNull AnAction getRightButtonClickAction() {
    return new EditBreakpointAction.ContextAction(this, XBreakpointProxyKt.asProxy(myBreakpoint));
  }

  @Override
  public @NotNull ActionGroup getPopupMenuActions() {
    return new DefaultActionGroup(
      myBreakpoint.getAdditionalPopupMenuActions(myBreakpoint.getBreakpointManager().getDebuggerManager().getCurrentSession()));
  }

  @Override
  public @NotNull String getTooltipText() {
    return myBreakpoint.getDescription();
  }

  @Override
  public GutterDraggableObject getDraggableObject() {
    return myBreakpoint.createBreakpointDraggableObject();
  }

  XBreakpointBase<?, ?, ?> getBreakpoint() {
    return myBreakpoint;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof BreakpointGutterIconRenderer renderer
           && getBreakpoint() == renderer.getBreakpoint()
           && Comparing.equal(getIcon(), renderer.getIcon());
  }

  @Override
  public int hashCode() {
    return getBreakpoint().hashCode();
  }
}
