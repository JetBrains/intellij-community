// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.memory.utils.InstancesProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import java.awt.*;

public abstract class InstancesViewBase extends JBPanel implements Disposable {

  private final InstancesProvider myInstancesProvider;


  public InstancesViewBase(LayoutManager layout, @NotNull XDebugSession session, InstancesProvider instancesProvider) {
    super(layout);
    myInstancesProvider = instancesProvider;
    XDebugSessionListener debugSessionListener = new MySessionListener();
    session.addSessionListener(debugSessionListener, this);
    final XValueMarkers<?, ?> markers = getValueMarkers(session);

    if (markers != null) {
      final MyActionListener listener = new MyActionListener(markers);
      ActionManager.getInstance().addAnActionListener(listener, this);
    }
  }


  protected XValueMarkers<?, ?> getValueMarkers(@NotNull XDebugSession session) {
    return session instanceof XDebugSessionImpl
      ? ((XDebugSessionImpl)session).getValueMarkers()
      : null;
  }

  protected abstract InstancesTree getInstancesTree();

  @Override
  public void dispose() {
  }

  public InstancesProvider getInstancesProvider() {
    return myInstancesProvider;
  }

  private class MySessionListener implements XDebugSessionListener {
    private volatile XDebuggerTreeState myTreeState = null;

    @Override
    public void sessionResumed() {
      ApplicationManager.getApplication().invokeLater(() -> {
        myTreeState = XDebuggerTreeState.saveState(getInstancesTree());

        getInstancesTree().setInfoMessage(
          "The application is running");
      });
    }

    @Override
    public void sessionPaused() {
      ApplicationManager.getApplication().invokeLater(() -> getInstancesTree().rebuildTree(InstancesTree.RebuildPolicy.RELOAD_INSTANCES, myTreeState));
    }
  }

  private class MyActionListener extends AnActionListener.Adapter {
    private final XValueMarkers<?, ?> myValueMarkers;

    private MyActionListener(@NotNull XValueMarkers<?, ?> markers) {
      myValueMarkers = markers;
    }

    @Override
    public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
      if (dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) == getInstancesTree() &&
        (isAddToWatchesAction(action) || isEvaluateExpressionAction(action))) {
        XValueNodeImpl selectedNode = XDebuggerTreeActionBase.getSelectedNode(dataContext);

        if (selectedNode != null) {
          TreeNode currentNode = selectedNode;
          while (!getInstancesTree().getRoot().equals(currentNode.getParent())) {
            currentNode = currentNode.getParent();
          }

          final XValue valueContainer = ((XValueNodeImpl)currentNode).getValueContainer();

          final String expression = valueContainer.getEvaluationExpression();
          if (expression != null) {
            myValueMarkers.markValue(valueContainer,
              new ValueMarkup(expression.replace("@", ""), new JBColor(0, 0), null));
          }

          ApplicationManager.getApplication().invokeLater(() -> getInstancesTree()
            .rebuildTree(InstancesTree.RebuildPolicy.ONLY_UPDATE_LABELS));
        }
      }
    }

    private boolean isAddToWatchesAction(AnAction action) {
      final String className = action.getClass().getSimpleName();
      return action instanceof XDebuggerTreeActionBase && className.equals("XAddToWatchesAction");
    }

    private boolean isEvaluateExpressionAction(AnAction action) {
      final String className = action.getClass().getSimpleName();
      return action instanceof XDebuggerActionBase && className.equals("EvaluateAction");
    }
  }
}
