package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.remoteServer.impl.runtime.ui.ServersToolWindowContent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public abstract class ServersTreeActionBase extends AnAction {

  protected ServersTreeActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(AnActionEvent e) {
    ServersToolWindowContent content = e.getData(ServersToolWindowContent.KEY);
    e.getPresentation().setEnabled(content != null && isEnabled(content, e));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ServersToolWindowContent content = e.getData(ServersToolWindowContent.KEY);
    if (content == null) {
      return;
    }
    doActionPerformed(content, e);
  }

  protected abstract boolean isEnabled(@NotNull ServersToolWindowContent content, AnActionEvent e);

  protected abstract void doActionPerformed(@NotNull ServersToolWindowContent content, AnActionEvent e);
}
