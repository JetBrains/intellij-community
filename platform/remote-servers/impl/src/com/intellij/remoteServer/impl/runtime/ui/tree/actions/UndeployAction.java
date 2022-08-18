package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import org.jetbrains.annotations.NotNull;

import static com.intellij.remoteServer.util.ApplicationActionUtils.getDeploymentTarget;

/**
 * @author michael.golubev
 */
public class UndeployAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    DeploymentNode node = getDeploymentTarget(e);
    boolean visible = node != null;
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && node.isUndeployActionEnabled());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DeploymentNode node = getDeploymentTarget(e);
    if (node != null) {
      node.undeploy();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

}
