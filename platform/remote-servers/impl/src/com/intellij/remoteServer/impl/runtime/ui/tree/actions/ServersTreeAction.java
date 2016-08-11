package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.Condition;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeNode;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class ServersTreeAction<T extends ServersTreeNode> extends AnAction {

  protected ServersTreeAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    List<T> targetNodes = getTargetNodes(e);

    boolean visible;
    boolean enabled;

    if (targetNodes == null) {
      visible = false;
      enabled = false;
    }
    else {
      visible = true;
      enabled = true;
      for (T targetNode : targetNodes) {
        visible &= isVisible4(targetNode);
        enabled &= visible && isEnabled4(targetNode);
      }
    }

    presentation.setVisible(visible);
    presentation.setEnabled(enabled);
    updatePresentation(presentation, ContainerUtil.getFirstItem(targetNodes));
  }

  private List<T> getTargetNodes(AnActionEvent e) {
    ServersToolWindowContent content = getContent(e);
    if (content == null) {
      return null;
    }
    Set<Object> selectedElements = content.getBuilder().getSelectedElements();
    int selectionCount = selectedElements.size();
    if (selectionCount == 0 || selectionCount > 1 && !isMultiSelectionAllowed()) {
      return null;
    }
    Class<T> targetNodeClass = getTargetNodeClass();
    List<T> result = new ArrayList<>();
    for (Object selectedElement : selectedElements) {
      ServersTreeNode node = (ServersTreeNode)selectedElement;
      if (!targetNodeClass.isInstance(node)) {
        return null;
      }
      result.add(targetNodeClass.cast(node));
    }
    return result;
  }

  private static ServersToolWindowContent getContent(AnActionEvent e) {
    return e.getData(ServersToolWindowContent.KEY);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<T> targetNodes = getTargetNodes(e);
    if (targetNodes == null) {
      return;
    }

    List<T> verifiedTargetNodes = ContainerUtil.filter(targetNodes, targetNode -> isVisible4(targetNode) && isEnabled4(targetNode));
    doActionPerformed(getContent(e), e, verifiedTargetNodes);
  }

  protected boolean isMultiSelectionAllowed() {
    return false;
  }

  protected boolean isVisible4(T node) {
    return true;
  }

  protected boolean isEnabled4(T node) {
    return true;
  }

  protected void updatePresentation(@NotNull Presentation presentation, @Nullable T node) {
  }

  protected void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e, List<T> nodes) {
    for (T node : nodes) {
      doActionPerformed(content, e, node);
    }
  }

  protected void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e, T node) {
    doActionPerformed(node);
  }

  protected void doActionPerformed(T node) {
    throw new UnsupportedOperationException();
  }

  protected abstract Class<T> getTargetNodeClass();
}
