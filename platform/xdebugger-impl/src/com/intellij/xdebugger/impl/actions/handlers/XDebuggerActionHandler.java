package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerActionHandler extends DebuggerActionHandler {

  public void perform(@NotNull final Project project, final AnActionEvent event) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session != null) {
      perform(session, event.getDataContext());
    }
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    return session != null && isEnabled(session, event.getDataContext());
  }

  protected abstract boolean isEnabled(@NotNull XDebugSession session, final DataContext dataContext);

  protected abstract void perform(@NotNull XDebugSession session, final DataContext dataContext);
}
