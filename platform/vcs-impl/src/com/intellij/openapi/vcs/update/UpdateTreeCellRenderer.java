// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;

import javax.swing.*;

/**
 * author: lesya
 */
public class UpdateTreeCellRenderer extends ColoredTreeCellRenderer{

  @Override
  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

    AbstractTreeNode treeNode = (AbstractTreeNode)value;
    append(treeNode.getText(), treeNode.getAttributes());
    final String errorText = treeNode.getErrorText();
    if (errorText != null) {
      append(" - ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append("Error: ", SimpleTextAttributes.ERROR_ATTRIBUTES);
      append(errorText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    setIcon(treeNode.getIcon(false));
    SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, selected);
  }
}
