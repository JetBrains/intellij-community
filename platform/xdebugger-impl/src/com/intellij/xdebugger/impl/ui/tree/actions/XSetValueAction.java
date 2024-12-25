// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.xdebugger.impl.ui.tree.SetValueInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class XSetValueAction extends XDebuggerTreeActionBase {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    XValueNodeImpl node = getSelectedNode(e.getDataContext());
    Presentation presentation = e.getPresentation();
    if (node instanceof WatchNode) {
      presentation.setEnabledAndVisible(false);
    }
    else {
      presentation.setVisible(true);
    }
  }

  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    return super.isEnabled(node, e) && node.getValueContainer().getModifier() != null;
  }

  @Override
  protected void perform(final XValueNodeImpl node, final @NotNull String nodeName, final AnActionEvent e) {
    SetValueInplaceEditor.show(node, nodeName);
  }
}
