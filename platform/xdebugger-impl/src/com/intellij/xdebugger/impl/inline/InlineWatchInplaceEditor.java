// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.ui.InplaceEditor;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class InlineWatchInplaceEditor extends InplaceEditor {
  private final XSourcePosition myPosition;
  private final XDebuggerTree myTree;
  private final Editor myHostEditor;
  private final XInlineWatchesViewImpl myWatchesView;
  private XDebuggerExpressionComboBox myInplaceEditor;

  public InlineWatchInplaceEditor(XSourcePosition position,
                                  @NotNull XDebuggerTree tree,
                                  Editor editor, XInlineWatchesViewImpl view) {
    myPosition = position;
    myTree = tree;
    myHostEditor = editor;
    myWatchesView = view;
  }

  @Override
  protected void beforeShow() { }

  @Override
  protected JComponent createInplaceEditorComponent() {
    myInplaceEditor = new XDebuggerExpressionComboBox(myTree.getProject(), myTree.getEditorsProvider(), "inlineWatch",
                                    myPosition, true, true);
    return myInplaceEditor.getComponent();
  }

  @Override
  protected JComponent getPreferredFocusedComponent() {
    return myInplaceEditor.getPreferredFocusedComponent();
  }

  @Override
  public Editor getEditor() {
    return myInplaceEditor.getEditor();
  }

  @Override
  public JComponent getEditorComponent() {
    return myInplaceEditor.getEditorComponent();
  }

  protected XExpression getExpression() {
    return myInplaceEditor.getExpression();
  }

  @Override
  public void doOKAction() {
    XExpression expression = getExpression();
    super.doOKAction();
    if (!XDebuggerUtilImpl.isEmptyExpression(expression)) {
      myWatchesView.addInlineWatchExpression(expression, -1, myPosition, true);
    }
  }


  @Override
  protected JComponent getHostComponent() {
    return myHostEditor.getContentComponent();
  }


  @Override
  protected Project getProject() {
    return myTree.getProject();
  }

  @Override
  protected Rectangle getEditorBounds() {
    int caretOffset = myHostEditor.getCaretModel().getOffset();
    Point caretPoint = myHostEditor.offsetToXY(caretOffset);
    int width = myHostEditor.getContentComponent().getWidth() - (caretPoint.x - myHostEditor.getContentComponent().getX());
    int height = myHostEditor.getLineHeight();
    Rectangle bounds = myHostEditor.getContentComponent().getVisibleRect();
    Rectangle lineBounds = new Rectangle(caretPoint.x, caretPoint.y, width, height);
    if (bounds == null) {
      return null;
    }
    if (bounds.y > lineBounds.y || bounds.y + bounds.height < lineBounds.y + lineBounds.height) {
      return null;
    }
    bounds.y = lineBounds.y;
    bounds.height = lineBounds.height;

    if(lineBounds.x > bounds.x) {
      bounds.width = bounds.width - lineBounds.x + bounds.x;
      bounds.x = lineBounds.x;
    }
    return bounds;
  }
}
