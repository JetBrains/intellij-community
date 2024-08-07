// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import org.jetbrains.annotations.NotNull;

public abstract class XDebuggerActionBase extends AnAction {
  private final boolean myHideDisabledInPopup;

  protected XDebuggerActionBase() {
    this(false);
  }

  protected XDebuggerActionBase(final boolean hideDisabledInPopup) {
    myHideDisabledInPopup = hideDisabledInPopup;
  }

  @Override
  public void update(@NotNull final AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    boolean hidden = isHidden(event);
    if (hidden) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    boolean enabled = isEnabled(event);
    if (myHideDisabledInPopup && ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setVisible(true);
    }
    presentation.setEnabled(enabled);
  }

  protected boolean isEnabled(final AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && !project.isDisposed()) {
      for (DebuggerSupport t : DebuggerSupport.getDebuggerSupports()) {
        if (isEnabled(project, e, t)) {
          return true;
        }
      }
      return false;
    }
    return false;
  }

  @NotNull
  protected abstract DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport);

  private boolean isEnabled(final Project project, final AnActionEvent event, final DebuggerSupport support) {
    return getHandler(support).isEnabled(project, event);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    performWithHandler(e);
    XDebuggerUtilImpl.reshowInlayRunToCursor(e);
  }

  protected boolean performWithHandler(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) {
      return true;
    }

    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      if (isEnabled(project, e, support)) {
        perform(project, e, support);
        return true;
      }
    }
    return false;
  }

  private void perform(final Project project, final AnActionEvent e, final DebuggerSupport support) {
    getHandler(support).perform(project, e);
  }

  protected boolean isHidden(AnActionEvent event) {
    Project project = event.getProject();
    if (project != null && !project.isDisposed()) {
      for (DebuggerSupport t : DebuggerSupport.getDebuggerSupports()) {
        if (!getHandler(t).isHidden(project, event)) {
          return false;
        }
      }
      return true;
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
