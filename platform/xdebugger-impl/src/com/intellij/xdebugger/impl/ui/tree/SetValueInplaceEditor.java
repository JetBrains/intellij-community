// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SetValueInplaceEditor extends XDebuggerTreeValueNodeInplaceEditor {
  private final XValueModifier myModifier;

  private SetValueInplaceEditor(final XValueNodeImpl node, @NotNull final @NlsSafe String nodeName) {
    super("setValue", node, nodeName);
    myModifier = myValueNode.getValueContainer().getModifier();
  }

  public static void show(final XValueNodeImpl node, @NotNull final String nodeName) {
    final SetValueInplaceEditor editor = new SetValueInplaceEditor(node, nodeName);

    if (editor.myModifier != null) {
      editor.myModifier.calculateInitialValueEditorText(initialValue -> AppUIUtil.invokeOnEdt(() -> {
        if (editor.getTree().isShowing()) {
          editor.show(initialValue);
        }
      }));
    }
    else {
      editor.show(null);
    }
  }

  private void show(String initialValue) {
    myExpressionEditor.setExpression(XExpressionImpl.fromText(initialValue));
    myExpressionEditor.selectAll();

    show();
  }

  @Override
  public void doOKAction() {
    if (myModifier == null) return;

    DebuggerUIUtil.setTreeNodeValue(myValueNode, getExpression(), (@Nls var errorMessage) -> {
      Editor editor = getEditor();
      if (editor != null) {
        HintManager.getInstance().showErrorHint(editor, errorMessage);
      }
      else {
        Messages.showErrorDialog(myTree, errorMessage);
      }
    });

    super.doOKAction();
  }
}
