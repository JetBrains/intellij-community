package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public abstract class ServersTreeAction<T extends ServersTreeNode> extends AnAction {

  protected ServersTreeAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    T targetNode = getTargetNode(e);

    boolean visible = false;
    boolean enabled = false;

    if (targetNode != null) {
      visible = isVisible4(targetNode);
      if (visible) {
        enabled = isEnabled4(targetNode);
      }
    }

    presentation.setVisible(visible);
    presentation.setEnabled(enabled);
    updatePresentation(presentation, targetNode);
  }

  private T getTargetNode(AnActionEvent e) {
    ServersToolWindowContent content = getContent(e);
    if (content == null) {
      return null;
    }
    Set<Object> selectedElements = content.getBuilder().getSelectedElements();
    if (selectedElements.size() != 1) {
      return null;
    }
    ServersTreeNode node = (ServersTreeNode)selectedElements.iterator().next();
    Class<T> targetNodeClass = getTargetNodeClass();
    if (!targetNodeClass.isInstance(node)) {
      return null;
    }
    return targetNodeClass.cast(node);
  }

  private static ServersToolWindowContent getContent(AnActionEvent e) {
    return e.getData(ServersToolWindowContent.KEY);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    T targetNode = getTargetNode(e);
    if (targetNode != null && isVisible4(targetNode) && isEnabled4(targetNode)) {
      doActionPerformed(getContent(e), e, targetNode);
    }
  }


  protected boolean isVisible4(T node) {
    return true;
  }

  protected boolean isEnabled4(T node) {
    return true;
  }

  protected void updatePresentation(@NotNull Presentation presentation, @Nullable T node) {
  }


  protected void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e, T node) {
    doActionPerformed(node);
  }

  protected void doActionPerformed(T node) {
    throw new UnsupportedOperationException();
  }

  protected abstract Class<T> getTargetNodeClass();
}
