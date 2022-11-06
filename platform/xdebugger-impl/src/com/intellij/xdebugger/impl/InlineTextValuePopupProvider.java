// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.inline.InlineValuePopupProvider;
import com.intellij.xdebugger.impl.inline.XDebuggerTextInlayPopup;
import com.intellij.xdebugger.impl.ui.XValueTextProvider;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.xdebugger.impl.inline.InlineDebugRenderer.getXValueDescriptor;

public class InlineTextValuePopupProvider implements InlineValuePopupProvider {
  @Override
  public boolean accepts(@NotNull XValueNodeImpl xValueNode) {
    XValue value = xValueNode.getValueContainer();
    return value instanceof XValueTextProvider && ((XValueTextProvider)value).shouldShowTextValue();
  }

  @Override
  public void showPopup(@NotNull XValueNodeImpl xValueNode,
                        @NotNull XDebugSession session,
                        @NotNull XSourcePosition position,
                        @NotNull XDebuggerTreeCreator treeCreator,
                        @NotNull Editor editor,
                        @NotNull Point point,
                        @Nullable Runnable hideRunnable) {
    Pair<XValue, String> descriptor = getXValueDescriptor(xValueNode);

    XValue value = xValueNode.getValueContainer();
    String initialText = ((XValueTextProvider)value).getValueText();
    String text = StringUtil.notNullize(initialText);
    XDebuggerTextInlayPopup.showTextPopup(text, treeCreator, descriptor, xValueNode, editor, point, position, session, hideRunnable);
  }
}
