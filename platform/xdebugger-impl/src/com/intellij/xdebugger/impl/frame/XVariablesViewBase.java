/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.dnd.DnDManager;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.impl.SelectionModelImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.evaluate.quick.XValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRestorer;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XStackFrameNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Set;

/**
 * @author nik
 */
public abstract class XVariablesViewBase extends XDebugView {
  protected final XDebuggerTreePanel myDebuggerTreePanel;
  private XDebuggerTreeState myTreeState;
  private Object myFrameEqualityObject;
  private XDebuggerTreeRestorer myTreeRestorer;

  protected XVariablesViewBase(@NotNull Project project, @NotNull XDebuggerEditorsProvider editorsProvider, @Nullable XValueMarkers<?, ?> markers) {
    myDebuggerTreePanel = new XDebuggerTreePanel(project, editorsProvider, this, null, XDebuggerActions.VARIABLES_TREE_POPUP_GROUP, markers);
    myDebuggerTreePanel.getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.variables.not.available"));
    DnDManager.getInstance().registerSource(myDebuggerTreePanel, myDebuggerTreePanel.getTree());
  }

  protected void buildTreeAndRestoreState(@NotNull final XStackFrame stackFrame) {
    XDebuggerTree tree = myDebuggerTreePanel.getTree();
    final XSourcePosition position = stackFrame.getSourcePosition();
    tree.setSourcePosition(position);
    tree.setRoot(new XStackFrameNode(tree, stackFrame), false);
    final Project project = tree.getProject();
    project.putUserData(XVariablesView.DEBUG_VARIABLES, new HashMap<Pair<VirtualFile, Integer>, Set<XValueNodeImpl>>());
    project.putUserData(XVariablesView.DEBUG_VARIABLES_TIMESTAMPS, new HashMap<VirtualFile, Long>());
    Object newEqualityObject = stackFrame.getEqualityObject();
    if (myFrameEqualityObject != null && newEqualityObject != null && myFrameEqualityObject.equals(newEqualityObject)
        && myTreeState != null) {
      disposeTreeRestorer();
      myTreeRestorer = myTreeState.restoreState(tree);
    }
    if (position != null && Registry.is("ide.debugger.inline")) {
      registerInlineEvaluator(stackFrame, tree, position, project);
    }
  }

  private void registerInlineEvaluator(final XStackFrame stackFrame,
                                       XDebuggerTree tree,
                                       final XSourcePosition position,
                                       final Project project) {
    final VirtualFile file = position.getFile();
    final FileEditor fileEditor = FileEditorManagerEx.getInstanceEx(project).getSelectedEditor(file);
    if (fileEditor instanceof PsiAwareTextEditorImpl) {
      final Editor editor = ((PsiAwareTextEditorImpl)fileEditor).getEditor();
      final SelectionListener listener = new SelectionListener() {
        @Override
        public void selectionChanged(final SelectionEvent e) {
          final String text = editor.getDocument().getText(e.getNewRange());
          final XDebuggerEvaluator evaluator = stackFrame.getEvaluator();
          if (evaluator != null && !StringUtil.isEmpty(text)
              && !(text.contains("exec(") || text.contains("++") || text.contains("--") || text.contains("="))) {
            evaluator.evaluate(text, new XEvaluationCallbackBase() {
              @Override
              public void evaluated(@NotNull XValue result) {
                final AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
                try {
                  final XDebugSession session = getSession(getTree());
                  if (session == null) return;
                  final TextRange range = e.getNewRange();
                  final ExpressionInfo info = new ExpressionInfo(range);
                  final int offset = range.getStartOffset();
                  final LogicalPosition pos = editor.offsetToLogicalPosition(offset);
                  final Point point = editor.logicalPositionToXY(pos);

                  new XValueHint(project, editor, point, ValueHintType.MOUSE_OVER_HINT, info, evaluator, session).invokeHint();
                }
                finally {
                  token.finish();
                }
              }

              @Override
              public void errorOccurred(@NotNull String errorMessage) {
              }
            }, position);
          }
        }
      };
      ((SelectionModelImpl)editor.getSelectionModel()).addSelectionListener(listener, tree);
    }
  }

  protected void saveCurrentTreeState(@Nullable XStackFrame stackFrame) {
    disposeTreeRestorer();
    myFrameEqualityObject = stackFrame != null ? stackFrame.getEqualityObject() : null;
    myTreeState = XDebuggerTreeState.saveState(myDebuggerTreePanel.getTree());
  }

  private void disposeTreeRestorer() {
    if (myTreeRestorer != null) {
      myTreeRestorer.dispose();
      myTreeRestorer = null;
    }
  }

  public XDebuggerTree getTree() {
    return myDebuggerTreePanel.getTree();
  }

  public JComponent getPanel() {
    return myDebuggerTreePanel.getMainPanel();
  }

  @Override
  public void dispose() {
    disposeTreeRestorer();
    DnDManager.getInstance().unregisterSource(myDebuggerTreePanel, myDebuggerTreePanel.getTree());
  }
}
