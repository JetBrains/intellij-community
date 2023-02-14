// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.memory.utils.InstancesProvider;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class InstancesViewBase extends JBPanel implements Disposable {
  private final InstancesProvider myInstancesProvider;

  public InstancesViewBase(@NotNull LayoutManager layout, @NotNull XDebugSession session, InstancesProvider instancesProvider) {
    super(layout);

    myInstancesProvider = instancesProvider;
    session.addSessionListener(new MySessionListener(), this);
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
      ApplicationManager.getApplication().invokeLater(() -> {
        XDebuggerTreeState state = myTreeState;
        InstancesTree tree = getInstancesTree();
        if (state == null) {
          tree.rebuildTree(InstancesTree.RebuildPolicy.RELOAD_INSTANCES);
        }
        else {
          tree.rebuildTree(InstancesTree.RebuildPolicy.RELOAD_INSTANCES, state);
        }
      });
    }
  }
}
