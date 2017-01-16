package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.execution.dashboard.DashboardTreeAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeNode;

import javax.swing.*;

public abstract class ServersTreeAction<T extends ServersTreeNode> extends DashboardTreeAction<T, ServersToolWindowContent>
  implements DumbAware {
  protected ServersTreeAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected final ServersToolWindowContent getTreeContent(AnActionEvent e) {
    return e.getData(ServersToolWindowContent.KEY);
  }
}
