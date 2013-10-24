package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public class RunServerAction extends RunServerActionBase {

  public RunServerAction() {
    super("Run/Connect", "Run/Connect to the selected server", AllIcons.Actions.Execute);
  }

  @Override
  protected Executor getExecutor() {
    return DefaultRunExecutor.getRunExecutorInstance();
  }
}
