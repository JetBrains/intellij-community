// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValuePresentationUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public abstract class XDebuggerTreeValueNodeInplaceEditor extends XDebuggerTreeInplaceEditor {
  protected final XValueNodeImpl myValueNode;
  private final JPanel myEditorPanel;
  private final int myNameOffset;

  protected XDebuggerTreeValueNodeInplaceEditor(@NonNls String historyId,
                                                XValueNodeImpl node,
                                                @NotNull final @NlsSafe String nodeName) {
    super(node, historyId);
    myValueNode = node;

    SimpleColoredComponent nameLabel = new SimpleColoredComponent();
    nameLabel.getIpad().right = 0;
    nameLabel.getIpad().left = 0;
    nameLabel.setIcon(myNode.getIcon());
    nameLabel.append(nodeName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
    XValuePresentation presentation = node.getValuePresentation();
    if (presentation != null) {
      XValuePresentationUtil.appendSeparator(nameLabel, presentation.getSeparator());
    }
    Border border = nameLabel.getMyBorder();
    myNameOffset = nameLabel.getPreferredSize().width  - (border != null ? border.getBorderInsets(nameLabel).right : 0);
    myEditorPanel = JBUI.Panels.simplePanel(myExpressionEditor.getComponent());
  }

  @Nullable
  @Override
  protected Rectangle getEditorBounds() {
    Rectangle bounds = super.getEditorBounds();
    if (bounds == null) {
      return null;
    }
    bounds.x += myNameOffset;
    bounds.width -= myNameOffset;
    return bounds;
  }

  @Override
  protected JComponent createInplaceEditorComponent() {
    return myEditorPanel;
  }
}
