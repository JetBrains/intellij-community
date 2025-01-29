// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class XJumpToSourceActionBase extends XDebuggerTreeActionBase {
  @Override
  protected void perform(final XValueNodeImpl node, final @NotNull String nodeName, final AnActionEvent e) {
    XValue value = node.getValueContainer();
    final XDebuggerEvaluationDialog dialog = e.getData(XDebuggerEvaluationDialog.KEY);
    XNavigatable navigatable = sourcePosition -> {
      if (sourcePosition != null) {
        final Project project = node.getTree().getProject();
        AppUIExecutor.onUiThread().expireWith(project).submit(() -> {
          sourcePosition.createNavigatable(project).navigate(true);
          if (dialog != null && Registry.is("debugger.close.dialog.on.navigate")) {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
          }
        });
      }
    };
    startComputingSourcePosition(value, navigatable);
  }

  protected abstract void startComputingSourcePosition(XValue value, XNavigatable navigatable);
}
