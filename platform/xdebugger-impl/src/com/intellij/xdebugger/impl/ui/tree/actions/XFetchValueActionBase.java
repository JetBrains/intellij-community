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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.IntIntHashMap;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.HeadlessValueEvaluationCallback;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class XFetchValueActionBase extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    for (XValueNodeImpl node : XDebuggerTreeActionBase.getSelectedNodes(e.getDataContext())) {
      if (isEnabled(e, node)) {
        return;
      }
    }
    e.getPresentation().setEnabled(false);
  }

  protected boolean isEnabled(@NotNull AnActionEvent event, @NotNull XValueNodeImpl node) {
    if (node instanceof WatchNodeImpl || node.isComputed()) {
      event.getPresentation().setEnabled(true);
      return true;
    }
    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<XValueNodeImpl> nodes = XDebuggerTreeActionBase.getSelectedNodes(e.getDataContext());
    if (nodes.isEmpty()) {
      return;
    }

    ValueCollector valueCollector = createCollector(e);
    for (XValueNodeImpl node : nodes) {
      addToCollector(nodes, node, valueCollector);
    }
    valueCollector.processed = true;
    valueCollector.finish();
  }

  protected void addToCollector(@NotNull List<XValueNodeImpl> paths, @NotNull XValueNodeImpl valueNode, @NotNull ValueCollector valueCollector) {
    if (paths.size() > 1) { // multiselection - copy the whole node text, see IDEA-136722
      valueCollector.add(valueNode.getText().toString(), valueNode.getPath().getPathCount());
    }
    else {
      XFullValueEvaluator fullValueEvaluator = valueNode.getFullValueEvaluator();
      if (fullValueEvaluator == null || !fullValueEvaluator.isShowValuePopup()) {
        valueCollector.add(StringUtil.notNullize(DebuggerUIUtil.getNodeRawValue(valueNode)));
      }
      else {
        new CopyValueEvaluationCallback(valueNode, valueCollector).startFetchingValue(fullValueEvaluator);
      }
    }
  }

  @NotNull
  protected ValueCollector createCollector(@NotNull AnActionEvent e) {
    return new ValueCollector(XDebuggerTree.getTree(e.getDataContext()));
  }

  public class ValueCollector {
    private final List<String> values = new SmartList<>();
    private final IntIntHashMap indents = new IntIntHashMap();
    private final XDebuggerTree myTree;
    private volatile boolean processed;

    public ValueCollector(XDebuggerTree tree) {
      myTree = tree;
    }

    public void add(@NotNull String value) {
      values.add(value);
    }

    public void add(@NotNull String value, int indent) {
      values.add(value);
      indents.put(values.size() - 1, indent);
    }

    public void finish() {
      Project project = myTree.getProject();
      if (processed && !values.contains(null) && !project.isDisposed()) {
        int minIndent = Integer.MAX_VALUE;
        for (int indent : indents.getValues()) {
          minIndent = Math.min(minIndent, indent);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
          if (i > 0) {
            sb.append("\n");
          }
          int indent = indents.get(i);
          if (indent > 0) {
            StringUtil.repeatSymbol(sb, ' ', indent - minIndent);
          }
          sb.append(values.get(i));
        }
        handleInCollector(project, sb.toString(), myTree);
      }
    }

    public void handleInCollector(final Project project, final String value, XDebuggerTree tree) {
      handle(project, value, tree);
    }

    public int acquire() {
      int index = values.size();
      values.add(null);
      return index;
    }

    public void evaluationComplete(final int index, @NotNull final String value) {
      AppUIUtil.invokeOnEdt(() -> {
        values.set(index, value);
        finish();
      });
    }
  }

  protected abstract void handle(final Project project, final String value, XDebuggerTree tree);

  private static final class CopyValueEvaluationCallback extends HeadlessValueEvaluationCallback {
    private final int myValueIndex;
    private final ValueCollector myValueCollector;

    public CopyValueEvaluationCallback(@NotNull XValueNodeImpl node, @NotNull ValueCollector valueCollector) {
      super(node);

      myValueCollector = valueCollector;
      myValueIndex = valueCollector.acquire();
    }

    @Override
    protected void evaluationComplete(@NotNull String value, @NotNull Project project) {
      myValueCollector.evaluationComplete(myValueIndex, value);
    }
  }
}
