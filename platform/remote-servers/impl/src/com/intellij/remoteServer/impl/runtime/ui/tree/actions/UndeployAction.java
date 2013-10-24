package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public class UndeployAction extends DeploymentActionBase {
  public UndeployAction() {
    super("Undeploy", "Undeploy the selected item", AllIcons.Nodes.Undeploy);
  }

  @Override
  protected boolean isApplicable(DeploymentNode node) {
    return node.isUndeployActionEnabled();
  }

  @Override
  protected void perform(DeploymentNode node) {
    node.undeploy();
  }
}
