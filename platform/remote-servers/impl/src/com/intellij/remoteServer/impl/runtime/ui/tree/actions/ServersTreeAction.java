package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.execution.dashboard.RunDashboardTreeAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class ServersTreeAction<T extends ServersTreeNode> extends RunDashboardTreeAction<T, ServersToolWindowContent>
  implements DumbAware {
  protected ServersTreeAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected final ServersToolWindowContent getTreeContent(AnActionEvent e) {
    return e.getData(ServersToolWindowContent.KEY);
  }

  @Override
  protected boolean isVisible4(T node) {
    return super.isVisible4(node);
  }

  @Override
  protected boolean isEnabled4(T node) {
    return super.isEnabled4(node);
  }

  @Override
  protected void updatePresentation(@NotNull Presentation presentation, @Nullable T node) {
    super.updatePresentation(presentation, node);
  }

  @Override
  protected void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e, List<T> nodes) {
    super.doActionPerformed(content, e, nodes);
  }

  @Override
  protected void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e, T node) {
    super.doActionPerformed(content, e, node);
  }

  @Override
  protected void doActionPerformed(T node) {
    super.doActionPerformed(node);
  }
}
