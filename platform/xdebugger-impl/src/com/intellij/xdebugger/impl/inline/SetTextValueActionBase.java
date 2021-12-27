// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.TextViewer;
import com.intellij.xdebugger.impl.ui.XValueTextProvider;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Experimental
public abstract class SetTextValueActionBase extends AnAction {

  public SetTextValueActionBase() {
    super(XDebuggerBundle.message("xdebugger.set.text.value.action.title"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    TextViewer textViewer = e.getData(TextViewer.DATA_KEY);
    XValueNodeImpl node = getNode(e);

    Presentation presentation = e.getPresentation();
    presentation.setVisible(node != null && canSetTextValue(node));
    boolean contentChanged = textViewer != null &&
                             Boolean.TRUE.equals(textViewer.getClientProperty(XDebuggerTextInlayPopup.TEXT_VIEWER_CONTENT_CHANGED));
    presentation.setEnabled(contentChanged);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    TextViewer textViewer = e.getData(TextViewer.DATA_KEY);
    XValueNodeImpl node = getNode(e);

    if (textViewer != null && node != null && canSetTextValue(node)) {
      setTextValue(node, textViewer.getText());
      textViewer.documentChanged(new DocumentEventImpl(textViewer.getDocument(), 0, "", "", -1, false, 0, 0, 0));
    }
  }

  private static @Nullable XValueNodeImpl getNode(@NotNull AnActionEvent e) {
    List<XValueNodeImpl> selectedNodes = e.getData(XDebuggerTree.SELECTED_NODES);
    return ContainerUtil.getOnlyItem(selectedNodes);
  }

  protected boolean canSetTextValue(@NotNull XValueNodeImpl node) {
    XValue value = node.getValueContainer();
    return value instanceof XValueTextProvider &&
           ((XValueTextProvider)value).isShowsTextValue() &&
           value.getModifier() != null;
  }

  protected abstract void setTextValue(@NotNull XValueNodeImpl node, @NotNull String text);
}