package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;

/**
 * @author michael.golubev
 */
public class UndeployAction extends ServersTreeAction<DeploymentNode> {

  public UndeployAction() {
    super("Undeploy", "Undeploy the selected item", AllIcons.Nodes.Undeploy);
  }

  @Override
  protected Class<DeploymentNode> getTargetNodeClass() {
    return DeploymentNode.class;
  }

  @Override
  protected boolean isEnabled4(DeploymentNode node) {
    return node.isUndeployActionEnabled();
  }

  @Override
  protected void doActionPerformed(DeploymentNode node) {
    node.undeploy();
  }
}
