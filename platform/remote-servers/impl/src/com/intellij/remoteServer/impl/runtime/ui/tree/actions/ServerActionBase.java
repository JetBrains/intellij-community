package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServerNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

/**
 * @author michael.golubev
 */
public abstract class ServerActionBase extends ServersTreeActionBase {

  protected ServerActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected final boolean isEnabled(@NotNull ServersToolWindowContent content, AnActionEvent e) {
    Set<ServerNode> selectedServerNodes = content.getSelectedServerNodes();
    Set<?> selectedElements = content.getBuilder().getSelectedElements();
    if (selectedElements.size() != selectedServerNodes.size() || selectedElements.isEmpty()) {
      return false;
    }

    for (ServerNode selectedServer : selectedServerNodes) {
      if (!isEnabledForServer(selectedServer)) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e) {
    for (ServerNode node : content.getSelectedServerNodes()) {
      performAction(node);
    }
  }

  protected abstract void performAction(@NotNull ServerNode serverNode);

  protected abstract boolean isEnabledForServer(@NotNull ServerNode serverNode);
}
