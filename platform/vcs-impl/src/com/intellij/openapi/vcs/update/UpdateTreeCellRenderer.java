package com.intellij.openapi.vcs.update;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

/**
 * author: lesya
 */
public class UpdateTreeCellRenderer extends ColoredTreeCellRenderer{

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

    AbstractTreeNode treeNode = (AbstractTreeNode)value;
    append(treeNode.getText(), treeNode.getAttributes());
    final String errorText = treeNode.getErrorText();
    if (errorText != null) {
      append(" - ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append("Error: ", SimpleTextAttributes.ERROR_ATTRIBUTES);
      append(errorText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    setIcon(treeNode.getIcon(expanded));
  }
}
