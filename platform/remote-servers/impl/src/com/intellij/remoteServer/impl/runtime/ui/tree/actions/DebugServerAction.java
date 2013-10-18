package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServerNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public class DebugServerAction extends RunServerActionBase {

  public DebugServerAction() {
    super("Debug", "Start the selected server in debug mode", AllIcons.Actions.StartDebugger);
  }

  @Override
  protected Executor getExecutor() {
    return DefaultDebugExecutor.getDebugExecutorInstance();
  }

  /**
   * Created by IntelliJ IDEA.
   * User: michael.golubev
   */
  public abstract static class SingleServerActionBase extends ServersTreeActionBase {
    protected SingleServerActionBase(String text, String description, Icon icon) {
      super(text, description, icon);
    }

    @Override
    protected void doActionPerformed(@NotNull ServersToolWindowContent content) {
      doActionPerformed(content, content.getSelectedServerNodes().iterator().next());
    }

    @Override
    protected boolean isEnabled(@NotNull ServersToolWindowContent content, AnActionEvent e) {
      Set<ServerNode> serverNodes = content.getSelectedServerNodes();
      return content.getBuilder().getSelectedElements().size() == serverNodes.size() && serverNodes.size() == 1 &&
             isEnabledForServer(serverNodes.iterator().next());
    }

    protected abstract boolean isEnabledForServer(ServerNode serverNode);

    protected abstract void doActionPerformed(@NotNull ServersToolWindowContent content, @NotNull ServerNode server);
  }
}
