package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.actions.ChooseDebugConfigurationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.AbstractDebuggerSession;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ResumeAction extends XDebuggerActionBase {
  @Override
  protected boolean isEnabled(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return false;

    boolean haveCurrentSession = false;
    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
      final AbstractDebuggerSession session = support.getCurrentSession(project);
      if (session != null && !session.isStopped()) {
        haveCurrentSession = true;
        if (session.isPaused()) {
          return true;
        }
      }
    }
    return !ActionPlaces.DEBUGGER_TOOLBAR.equals(e.getPlace()) && !haveCurrentSession;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (!performWithHandler(e)) {
      new ChooseDebugConfigurationAction().actionPerformed(e);
    }
  }

  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return debuggerSupport.getResumeActionHandler();
  }
}
