// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

import static com.intellij.xdebugger.impl.XDebuggerUtilImpl.performDebuggerAction;

public abstract class XDebuggerActionBase extends AnAction {
  private final boolean myHideDisabledInPopup;

  protected XDebuggerActionBase() {
    this(false);
  }

  protected XDebuggerActionBase(final boolean hideDisabledInPopup) {
    myHideDisabledInPopup = hideDisabledInPopup;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    boolean hidden = isHidden(event);
    if (hidden) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    boolean enabled = isEnabled(event);
    if (myHideDisabledInPopup && event.isFromContextMenu()) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setVisible(true);
    }
    presentation.setEnabled(enabled);
  }

  protected boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && !project.isDisposed()) {
      DebuggerSupport support = new DebuggerSupport();
      if (isEnabled(project, e, support)) {
        return true;
      }
      return false;
    }
    return false;
  }

  protected abstract @NotNull DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport);

  private boolean isEnabled(final Project project, final AnActionEvent event, final DebuggerSupport support) {
    return getHandler(support).isEnabled(project, event);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    performDebuggerAction(e, () -> performWithHandler(e));
  }

  protected boolean performWithHandler(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) {
      return true;
    }

    DebuggerSupport support = new DebuggerSupport();
    if (isEnabled(project, e, support)) {
      perform(project, e, support);
      return true;
    }
    return false;
  }

  private void perform(@NotNull Project project,
                       @NotNull AnActionEvent e,
                       @NotNull DebuggerSupport support) {
    getHandler(support).perform(project, e);
  }

  protected boolean isHidden(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project != null && !project.isDisposed()) {
      DebuggerSupport support = new DebuggerSupport();
      return getHandler(support).isHidden(project, event);
    }
    return true;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
