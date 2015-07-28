/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.console.ConsoleExecuteAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.util.Consumer;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.actions.handlers.XEvaluateInConsoleFromEditorActionHandler;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class EvaluateInConsoleFromTreeAction extends XAddToWatchesAction {
  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    return super.isEnabled(node, e) && getConsoleExecuteAction(e) != null;
  }

  @Override
  public void update(AnActionEvent e) {
    if (getConsoleExecuteAction(e) != null) {
      e.getPresentation().setVisible(true);
      super.update(e);
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Nullable
  private static ConsoleExecuteAction getConsoleExecuteAction(@NotNull AnActionEvent e) {
    return XEvaluateInConsoleFromEditorActionHandler.getConsoleExecuteAction(e.getData(LangDataKeys.CONSOLE_VIEW));
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    final ConsoleExecuteAction action = getConsoleExecuteAction(e);
    if (action != null) {
      node.getValueContainer().calculateEvaluationExpression().done(new Consumer<XExpression>() {
        @Override
        public void consume(XExpression expression) {
          if (expression != null) {
            action.execute(null, expression.getExpression(), null);
          }
        }
      });
    }
  }
}