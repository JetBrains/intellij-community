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
package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.dnd.DnDManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ObjectLongHashMap;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.evaluate.quick.XValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRestorer;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XStackFrameNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public abstract class XVariablesViewBase extends XDebugView {
  private final XDebuggerTreePanel myTreePanel;
  private XDebuggerTreeState myTreeState;
  private XDebuggerTreeRestorer myTreeRestorer;

  private Object myFrameEqualityObject;
  private MySelectionListener mySelectionListener;

  protected XVariablesViewBase(@NotNull Project project, @NotNull XDebuggerEditorsProvider editorsProvider, @Nullable XValueMarkers<?, ?> markers) {
    myTreePanel = new XDebuggerTreePanel(
      project, editorsProvider, this, null, this instanceof XWatchesView ? XDebuggerActions.WATCHES_TREE_POPUP_GROUP : XDebuggerActions.VARIABLES_TREE_POPUP_GROUP, markers);
    getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.variables.not.available"));
    DnDManager.getInstance().registerSource(myTreePanel, getTree());
  }

  protected void buildTreeAndRestoreState(@NotNull final XStackFrame stackFrame) {
    XSourcePosition position = stackFrame.getSourcePosition();
    XDebuggerTree tree = getTree();
    tree.setSourcePosition(position);
    createNewRootNode(stackFrame);
    final Project project = tree.getProject();
    project.putUserData(XVariablesView.DEBUG_VARIABLES, new XVariablesView.InlineVariablesInfo());
    project.putUserData(XVariablesView.DEBUG_VARIABLES_TIMESTAMPS, new ObjectLongHashMap<>());
    Object newEqualityObject = stackFrame.getEqualityObject();
    if (myFrameEqualityObject != null && newEqualityObject != null && myFrameEqualityObject.equals(newEqualityObject)
        && myTreeState != null) {
      disposeTreeRestorer();
      myTreeRestorer = myTreeState.restoreState(tree);
    }
    if (position != null && Registry.is("debugger.valueTooltipAutoShowOnSelection")) {
      registerInlineEvaluator(stackFrame, position, project);
    }
  }

  protected XValueContainerNode createNewRootNode(@Nullable XStackFrame stackFrame) {
    XValueContainerNode root;
    if (stackFrame == null) {
      root = new XValueContainerNode<XValueContainer>(getTree(), null, new XValueContainer() {}) {};
    }
    else {
      root = new XStackFrameNode(getTree(), stackFrame);
    }
    getTree().setRoot(root, false);
    return root;
  }

  private void registerInlineEvaluator(final XStackFrame stackFrame,
                                       final XSourcePosition position,
                                       final Project project) {
    final VirtualFile file = position.getFile();
    final FileEditor fileEditor = FileEditorManagerEx.getInstanceEx(project).getSelectedEditor(file);
    if (fileEditor instanceof PsiAwareTextEditorImpl) {
      final Editor editor = ((PsiAwareTextEditorImpl)fileEditor).getEditor();
      removeSelectionListener();
      mySelectionListener = new MySelectionListener(editor, stackFrame, project);
      editor.getSelectionModel().addSelectionListener(mySelectionListener);
    }
  }

  protected void saveCurrentTreeState(@Nullable XStackFrame stackFrame) {
    removeSelectionListener();
    myFrameEqualityObject = stackFrame != null ? stackFrame.getEqualityObject() : null;
    if (myTreeRestorer == null || myTreeRestorer.isFinished()) {
      myTreeState = XDebuggerTreeState.saveState(getTree());
    }
    disposeTreeRestorer();
  }

  private void removeSelectionListener() {
    if (mySelectionListener != null) {
      mySelectionListener.remove();
      mySelectionListener = null;
    }
  }

  @Override
  protected void clear() {
    removeSelectionListener();
  }

  private void disposeTreeRestorer() {
    if (myTreeRestorer != null) {
      myTreeRestorer.dispose();
      myTreeRestorer = null;
    }
  }

  @NotNull
  public final XDebuggerTree getTree() {
    return myTreePanel.getTree();
  }

  public JComponent getPanel() {
    return myTreePanel.getMainPanel();
  }

  @Override
  public void dispose() {
    disposeTreeRestorer();
    removeSelectionListener();
    DnDManager.getInstance().unregisterSource(myTreePanel, getTree());
  }

  private class MySelectionListener implements SelectionListener {
    private final Editor myEditor;
    private final XStackFrame myStackFrame;
    private final Project myProject;

    public MySelectionListener(Editor editor, XStackFrame stackFrame, Project project) {
      myEditor = editor;
      myStackFrame = stackFrame;
      myProject = project;
    }

    public void remove() {
      myEditor.getSelectionModel().removeSelectionListener(this);
    }

    @Override
    public void selectionChanged(final SelectionEvent e) {
      if (!Registry.is("debugger.valueTooltipAutoShowOnSelection") || myEditor.getCaretModel().getCaretCount() > 1) {
        return;
      }
      final String text = myEditor.getDocument().getText(e.getNewRange());
      if (!StringUtil.isEmpty(text) && !(text.contains("exec(") || text.contains("++") || text.contains("--") || text.contains("="))) {
        final XDebugSession session = getSession(getTree());
        if (session == null) return;
        XDebuggerEvaluator evaluator = myStackFrame.getEvaluator();
        if (evaluator == null) return;
        TextRange range = e.getNewRange();
        ExpressionInfo info = new ExpressionInfo(range);
        int offset = range.getStartOffset();
        LogicalPosition pos = myEditor.offsetToLogicalPosition(offset);
        Point point = myEditor.logicalPositionToXY(pos);
        new XValueHint(myProject, myEditor, point, ValueHintType.MOUSE_OVER_HINT, info, evaluator, session).invokeHint();
      }
    }
  }
}
