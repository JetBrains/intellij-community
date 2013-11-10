package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author michael.golubev
 */
public class UndeployAction extends DeploymentActionBase {
  public UndeployAction() {
    super("Undeploy", "Undeploy the selected item", AllIcons.Nodes.Undeploy);
  }

  @Override
  protected boolean isApplicable(@NotNull DeploymentNode node) {
    return node.isUndeployActionEnabled();
  }

  @Override
  protected void perform(@NotNull List<DeploymentNode> nodes, ServersToolWindowContent content, AnActionEvent e) {
    for (DeploymentNode node : nodes) {
      node.undeploy();
    }
  }
}
