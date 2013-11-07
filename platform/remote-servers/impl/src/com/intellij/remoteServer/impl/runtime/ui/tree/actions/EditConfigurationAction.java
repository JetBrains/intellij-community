package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServerNode;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author michael.golubev
 */
public class EditConfigurationAction extends ServersTreeActionBase {
  public EditConfigurationAction() {
    super("Edit Configuration", "Edit configuration of the selected server", AllIcons.Actions.EditSource);
  }

  @Override
  protected void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e) {
    Set<DeploymentNode> deploymentNodes = content.getSelectedDeploymentNodes();
    Set<ServerNode> serverNodes = content.getSelectedServerNodes();
    if (deploymentNodes.size() == 1) {
      deploymentNodes.iterator().next().editConfiguration();
    }
    else {
      serverNodes.iterator().next().editConfiguration();
    }
  }

  @Override
  protected boolean isEnabled(@NotNull ServersToolWindowContent content, AnActionEvent e) {
    Set<DeploymentNode> deploymentNodes = content.getSelectedDeploymentNodes();
    Set<ServerNode> serverNodes = content.getSelectedServerNodes();
    if (deploymentNodes.size() + serverNodes.size() != 1) return false;
    if (deploymentNodes.size() == 1) {
      return deploymentNodes.iterator().next().isEditConfigurationActionEnabled();
    }
    return true;
  }
}
