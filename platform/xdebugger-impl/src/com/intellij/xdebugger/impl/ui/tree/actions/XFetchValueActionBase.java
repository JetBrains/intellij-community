// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.HeadlessValueEvaluationCallback;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class for actions that operate on frontend {@link XValueNodeImpl} UI nodes
 * but still need access to backend {@link XValue} instances.
 * <p>
 * <strong>Note</strong>: This class is supposed to maintain backward compatibility for plugins during the migration to Split Mode.
 * It bridges the gap by providing {@link XValueNodeImpl} nodes that expose backend {@link XValue}s obtained from plugin-specific {@link XDebugProcess}.
 * <p>
 * <li><strong>In Monolith Mode</strong>: the action works as it did before Split mode, providing nodes with backend {@link XValue}s.</li>
 * </li><strong>In Remote Development</strong>: the action DOES NOT work. Since {@link XValueNodeImpl} is a frontend UI entity,
 * it cannot be accessed from the backend.</li>
 * <p>
 * For the action which should operate on the frontend use {@link XFetchValueSplitActionBase} instead.
 */
public abstract class XFetchValueActionBase extends DumbAwareAction implements ActionRemoteBehaviorSpecification {
  @ApiStatus.Internal
  @Override
  public @Nullable ActionRemoteBehavior getBehavior() {
    return ActionRemoteBehavior.BackendOnly;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    for (XValueNodeImpl node : getNodes(e)) {
      if (isEnabled(e, node)) {
        return;
      }
    }
    e.getPresentation().setEnabled(false);
  }

  /**
   * @return {@link XValueNodeImpl} node instances with corresponding backend {@link XValue}s.
   */
  @NotNull List<@NotNull XValueNodeImpl> getNodes(@NotNull AnActionEvent e) {
    return XDebuggerTreeActionBase.getSelectedNodes(e.getDataContext());
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
    List<XValueNodeImpl> nodes = getNodes(e);
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

  protected @NotNull ValueCollector createCollector(@NotNull AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e.getDataContext());
    return new ValueCollector(tree, e.getProject());
  }

  public class ValueCollector {
    private final List<String> values = new SmartList<>();
    private final Int2IntMap indents = new Int2IntOpenHashMap();
    private final Project myProject;
    private volatile boolean processed;
    private XDebuggerTree myTree;

    /**
     * @deprecated Use {@link #ValueCollector(Project)} instead
     */
    @Deprecated
    public ValueCollector(@NotNull XDebuggerTree tree) {
      this(tree.getProject());
      myTree = tree;
    }

    ValueCollector(@Nullable XDebuggerTree tree, Project project) {
      this(project);
      myTree = tree;
    }

    public ValueCollector(Project project) {
      indents.defaultReturnValue(-1);
      myProject = project;
    }

    public void add(@NotNull String value) {
      values.add(value);
    }

    /**
     * @deprecated Do not use. Override {@link XFetchValueActionBase#createCollector(AnActionEvent)} to access required state instead.
     */
    @Deprecated
    public @Nullable XDebuggerTree getTree() {
      return myTree;
    }

    public void add(@NotNull String value, int indent) {
      values.add(value);
      indents.put(values.size() - 1, indent);
    }

    public void finish() {
      if (processed && !values.contains(null) && !myProject.isDisposed()) {
        int minIndent = Integer.MAX_VALUE;
        for (int indent : indents.values()) {
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
        handleInCollector(myProject, sb.toString(), myTree);
      }
    }

    /**
     * @deprecated Use {@link #handleInCollector(Project, String)} instead
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public void handleInCollector(final Project project, final String value, XDebuggerTree ignoredTree) {
      handleInCollector(project, value);
    }

    public void handleInCollector(final Project project, final String value) {
      handle(project, value, myTree);
    }

    public int acquire() {
      int index = values.size();
      values.add(null);
      return index;
    }

    public void evaluationComplete(final int index, final @NotNull String value) {
      AppUIUtil.invokeOnEdt(() -> {
        values.set(index, value);
        finish();
      });
    }
  }

  /**
   * @deprecated Use {@link #handle(Project, String)} instead
   */
  @Deprecated
  protected void handle(final Project project, final String value, XDebuggerTree ignoredTree) {
    handle(project, value);
  }

  protected void handle(final Project project, final String value) {
    throw new AbstractMethodError();
  }

  private static final class CopyValueEvaluationCallback extends HeadlessValueEvaluationCallback {
    private final int myValueIndex;
    private final ValueCollector myValueCollector;

    CopyValueEvaluationCallback(@NotNull XValueNodeImpl node, @NotNull ValueCollector valueCollector) {
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
