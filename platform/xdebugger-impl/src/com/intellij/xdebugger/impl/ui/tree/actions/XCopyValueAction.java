/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * @author nik
 */
public class XCopyValueAction extends XDebuggerTreeActionBase {
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    XSuspendContext suspendContext =  node.getTree().getSession().getSuspendContext();
    XDebuggerEvaluator evaluator = XDebuggerUtilImpl.getEvaluator(suspendContext);
    if (evaluator != null && evaluator.isEvaluateOnCopy(node.getName(), node.getValue())) {
      evaluator.evaluateFull(node.getName(), new XDebuggerEvaluator.XEvaluationCallback() {
        @Override
        public void evaluated(@NotNull XValue result) {
          String value = result.getModifier().getInitialValueEditorText();
          setCopyContents(value);
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          String value = node.getValue();
          setCopyContents(value);
        }
      }, null);
    } else {
      String value = node.getValue();
      setCopyContents(value);
    }

  }

  private static void setCopyContents(String value) {
    CopyPasteManager.getInstance().setContents(new StringSelection(value));
  }

  protected boolean isEnabled(final XValueNodeImpl node) {
    return super.isEnabled(node) && node.getValue() != null;
  }
}
