// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XInstanceEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerInstanceTreeCreator;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.evaluate.quick.common.DebuggerTreeWithHistoryPanel;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

@ApiStatus.Internal
public class XInspectDialog extends DialogWrapper {
  private final DebuggerTreeWithHistoryPanel myDebuggerTreePanel;
  private final boolean myRebuildOnSessionEvents;

  public XInspectDialog(@NotNull Project project,
                        XDebuggerEditorsProvider editorsProvider,
                        XSourcePosition sourcePosition,
                        @NotNull String name,
                        @NotNull XValue value,
                        XValueMarkers<?, ?> markers,
                        @Nullable XDebugSessionProxy session,
                        boolean rebuildOnSessionEvents) {
    super(project, false);
    myRebuildOnSessionEvents = rebuildOnSessionEvents;

    setTitle(XDebuggerBundle.message("inspect.value.dialog.title", name));
    setModal(false);

    XInstanceEvaluator instanceEvaluator = value.getInstanceEvaluator();
    if (instanceEvaluator != null && myRebuildOnSessionEvents && session != null) {
      Pair<XInstanceEvaluator, String> initialItem = Pair.create(instanceEvaluator, name);
      XDebuggerInstanceTreeCreator creator = new XDebuggerInstanceTreeCreator(project, editorsProvider, sourcePosition, markers, session);
      myDebuggerTreePanel = new DebuggerTreeWithHistoryPanel<>(initialItem, creator, project, myDisposable);
    }
    else {
      Pair<XValue, String> initialItem = Pair.create(value, name);
      XDebuggerTreeCreator creator = new XDebuggerTreeCreator(project, editorsProvider, sourcePosition, markers);
      myDebuggerTreePanel = new DebuggerTreeWithHistoryPanel<>(initialItem, creator, project, myDisposable);
    }

    if (session != null) {
      session.addSessionListener(new XDebugSessionListener() {
        @Override
        public void sessionPaused() {
          if (myRebuildOnSessionEvents) {
            myDebuggerTreePanel.rebuild();
          }
        }

        // do not close on session end IDEA-132136
        //@Override
        //public void sessionStopped() {
        //  DebuggerUIUtil.invokeLater(new Runnable() {
        //    @Override
        //    public void run() {
        //      close(OK_EXIT_CODE);
        //    }
        //  });
        //}
      }, myDisposable);
    }

    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myDebuggerTreePanel.getMainPanel();
  }

  @Override
  protected @Nullable JComponent createSouthPanel() {
    return null;
  }

  @Override
  protected @NonNls String getDimensionServiceKey() {
    return "#xdebugger.XInspectDialog";
  }

  public @NotNull XDebuggerTree getTree() {
    return myDebuggerTreePanel.getTree();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myDebuggerTreePanel.getTree();
  }
}