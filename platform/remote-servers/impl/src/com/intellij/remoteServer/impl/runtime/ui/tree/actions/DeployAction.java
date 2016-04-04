package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author michael.golubev
 */
public class DeployAction extends ServersTreeAction<DeploymentNode> {

  public DeployAction() {
    super("Deploy", "Deploy the selected item", AllIcons.Nodes.Deploy);
  }

  @Override
  protected Class<DeploymentNode> getTargetNodeClass() {
    return DeploymentNode.class;
  }

  @Override
  protected void updatePresentation(@NotNull Presentation presentation, @Nullable DeploymentNode node) {
    if (node != null && node.isDeployed()) {
      presentation.setText("Redeploy");
      presentation.setDescription("Redeploy the selected item");
    }
    else {
      presentation.setText(getTemplatePresentation().getText());
      presentation.setDescription(getTemplatePresentation().getDescription());
    }
  }

  @Override
  protected boolean isVisible4(DeploymentNode node) {
    return node.isDeployActionVisible();
  }

  @Override
  protected boolean isEnabled4(DeploymentNode node) {
    return node.isDeployActionEnabled();
  }

  @Override
  protected void doActionPerformed(DeploymentNode node) {
    node.deploy();
  }
}

