package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServerNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author michael.golubev
 */
public class DeployAction extends ServersTreeActionBase {
  public DeployAction() {
    super("Deploy", "Deploy the selected item", AllIcons.Nodes.Deploy);
  }

  @Override
  protected void updatePresentation(@NotNull Presentation presentation, @Nullable ServersToolWindowContent content) {
    if (content != null) {
      Set<DeploymentNode> nodes = content.getSelectedDeploymentNodes();
      if (nodes.size() == 1) {
        DeploymentNode node = nodes.iterator().next();
        if (node.isDeployed()) {
          presentation.setText("Redeploy");
          presentation.setDescription("Redeploy '" + node.getDeploymentName() + "'");
          return;
        }
      }
    }
    presentation.setText(getTemplatePresentation().getText());
    presentation.setDescription(getTemplatePresentation().getDescription());
  }

  @Override
  protected boolean isEnabled(@NotNull ServersToolWindowContent content, AnActionEvent e) {
    Set<DeploymentNode> deploymentNodes = content.getSelectedDeploymentNodes();
    Set<ServerNode> serverNodes = content.getSelectedServerNodes();
    if (deploymentNodes.size() + serverNodes.size() != 1) return false;
    for (DeploymentNode node : deploymentNodes) {
      if (!node.isRedeployActionEnabled()) {
        return false;
      }
    }
    for (ServerNode serverNode : serverNodes) {
      if (!serverNode.isDeployActionEnabled()) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e) {
    for (DeploymentNode node : content.getSelectedDeploymentNodes()) {
      node.redeploy();
    }
    for (ServerNode node : content.getSelectedServerNodes()) {
      node.deploy(e);
    }
  }
}

