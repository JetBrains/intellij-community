// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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
public abstract class XFetchValueActionBase extends AbstractXFetchValueAction {
  public class ValueCollector extends AbstractXFetchValueAction.ValueCollector {
    public ValueCollector(Project project) {
      super(project);
    }

    /**
     * @deprecated Use {@link #ValueCollector(Project)} instead
     */
    @Deprecated
    public ValueCollector(@NotNull XDebuggerTree tree) {
      super(tree, tree.getProject());
    }
  }

  @ApiStatus.Internal
  @Override
  public @NotNull ActionRemoteBehavior getBehavior() {
    return ActionRemoteBehavior.BackendOnly;
  }

  /**
   * @return {@link XValueNodeImpl} node instances with corresponding backend {@link XValue}s.
   */
  @ApiStatus.Internal
  @Override
  @NotNull
  protected final List<@NotNull XValueNodeImpl> getNodes(@NotNull AnActionEvent e) {
    return XDebuggerTreeActionBase.getSelectedNodes(e.getDataContext());
  }
}
