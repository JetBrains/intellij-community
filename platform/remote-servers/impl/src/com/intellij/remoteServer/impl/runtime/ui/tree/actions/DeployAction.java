package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import com.intellij.remoteServer.CloudBundle;
import org.jetbrains.annotations.NotNull;

import static com.intellij.remoteServer.util.ApplicationActionUtils.getDeploymentTarget;

/**
 * @author michael.golubev
 */
public class DeployAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    DeploymentNode node = getDeploymentTarget(e);
    Presentation presentation = e.getPresentation();
    boolean visible = node != null && node.isDeployActionVisible();
    presentation.setVisible(visible);
    presentation.setEnabled(visible && node.isDeployActionEnabled());
    if (node != null && node.isDeployed()) {
      presentation.setText(CloudBundle.messagePointer("action.presentation.DeployAction.text"));
      presentation.setDescription(CloudBundle.messagePointer("action.presentation.DeployAction.description"));
    }
    else {
      presentation.setText(getTemplatePresentation().getText());
      presentation.setDescription(getTemplatePresentation().getDescription());
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DeploymentNode node = getDeploymentTarget(e);
    if (node != null) {
      node.deploy();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}

