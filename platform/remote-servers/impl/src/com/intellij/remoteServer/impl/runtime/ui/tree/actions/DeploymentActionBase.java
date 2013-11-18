package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.DeploymentNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public abstract class DeploymentActionBase extends ServersTreeActionBase {
  protected DeploymentActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  protected abstract void perform(@NotNull List<DeploymentNode> nodes, ServersToolWindowContent content, AnActionEvent e);

  protected abstract boolean isApplicable(@NotNull DeploymentNode node);

  @Override
  public void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e) {
    List<DeploymentNode> toPerform = new ArrayList<DeploymentNode>();
    for (DeploymentNode node : content.getSelectedDeploymentNodes()) {
      if (isApplicable(node)) {
        toPerform.add(node);
      }
    }
    if (!toPerform.isEmpty()) {
      perform(toPerform, content, e);
    }
  }

  @Override
  protected boolean isEnabled(@NotNull ServersToolWindowContent content, AnActionEvent e) {
    Set<?> selectedElements = content.getBuilder().getSelectedElements();
    if (selectedElements.isEmpty() || selectedElements.size() != content.getSelectedDeploymentNodes().size()) {
      return false;
    }

    for (DeploymentNode node : content.getSelectedDeploymentNodes()) {
      if (!isApplicable(node)) {
        return false;
      }
    }
    return true;
  }
}
