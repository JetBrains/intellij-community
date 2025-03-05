// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.frame.XDebugView;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.tree.TreePath;

public abstract class XDebuggerTreeInplaceEditor extends TreeInplaceEditor {
  protected final XDebuggerTreeNode myNode;
  protected final XDebuggerExpressionComboBox myExpressionEditor;
  protected final XDebuggerTree myTree;

  public XDebuggerTreeInplaceEditor(final XDebuggerTreeNode node, final @NonNls String historyId) {
    myNode = node;
    myTree = myNode.getTree();
    myExpressionEditor = new XDebuggerExpressionComboBox(myTree.getProject(), myTree.getEditorsProvider(), historyId,
                                                         myTree.getSourcePosition(), false, true);
  }

  @Override
  protected JComponent createInplaceEditorComponent() {
    return myExpressionEditor.getComponent();
  }

  @Override
  protected void onHidden() {
    final ComboPopup popup = myExpressionEditor.getComboBox().getPopup();
    if (popup != null && popup.isVisible()) {
      popup.hide();
    }
  }

  @Override
  protected void doPopupOKAction() {
    ComboPopup popup = myExpressionEditor.getComboBox().getPopup();
    if (popup != null && popup.isVisible()) {
      Object value = popup.getList().getSelectedValue();
      if (value != null) {
        myExpressionEditor.setExpression((XExpression)value);
      }
    }
    doOKAction();
  }

  @Override
  public void doOKAction() {
    myExpressionEditor.saveTextInHistory();
    super.doOKAction();
  }

  @Override
  protected void onShown() {
    XDebugSession session = XDebugView.getSession(myTree);
    if (session != null) {
      session.addSessionListener(new XDebugSessionListener() {
        @Override
        public void sessionPaused() {
          cancel();
        }

        @Override
        public void sessionResumed() {
          cancel();
        }

        @Override
        public void sessionStopped() {
          cancel();
        }

        @Override
        public void stackFrameChanged() {
          cancel();
        }

        @Override
        public void beforeSessionResume() {
          cancel();
        }

        private void cancel() {
          AppUIUtil.invokeOnEdt(XDebuggerTreeInplaceEditor.this::cancelEditing);
        }
      }, myDisposable);
    }
  }

  protected XExpression getExpression() {
    return myExpressionEditor.getExpression();
  }

  @Override
  protected JComponent getPreferredFocusedComponent() {
    return myExpressionEditor.getPreferredFocusedComponent();
  }

  @Override
  public Editor getEditor() {
    return myExpressionEditor.getEditor();
  }

  @Override
  public JComponent getEditorComponent() {
    return myExpressionEditor.getEditorComponent();
  }

  @Override
  protected TreePath getNodePath() {
    return myNode.getPath();
  }

  @Override
  protected XDebuggerTree getTree() {
    return myTree;
  }

  @Override
  protected Project getProject() {
    return myNode.getTree().getProject();
  }

  protected final int getAfterIconX() {
    Icon icon = myNode.getIcon();
    if (icon != null) {
      SimpleColoredComponent iconLabel = new SimpleColoredComponent();
      iconLabel.getIpad().right = 0;
      iconLabel.getIpad().left = 0;
      iconLabel.setIcon(myNode.getIcon());
      Border border = iconLabel.getMyBorder();
      return iconLabel.getPreferredSize().width - (border != null ? border.getBorderInsets(iconLabel).right : 0);
    }
    return 0;
  }
}
