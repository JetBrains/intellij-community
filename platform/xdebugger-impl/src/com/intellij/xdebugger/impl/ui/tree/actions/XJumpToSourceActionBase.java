/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XJumpToSourceActionBase extends XDebuggerTreeActionBase {
  @Override
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    XValue value = node.getValueContainer();
    final XDebuggerEvaluationDialog dialog = e.getData(XDebuggerEvaluationDialog.KEY);
    XNavigatable navigatable = sourcePosition -> {
      if (sourcePosition != null) {
        final Project project = node.getTree().getProject();
        AppUIUtil.invokeOnEdt(() -> {
          sourcePosition.createNavigatable(project).navigate(true);
          if (dialog != null && Registry.is("debugger.close.dialog.on.navigate")) {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
          }
        }, project.getDisposed());
      }
    };
    startComputingSourcePosition(value, navigatable);
  }

  protected abstract void startComputingSourcePosition(XValue value, XNavigatable navigatable);
}
