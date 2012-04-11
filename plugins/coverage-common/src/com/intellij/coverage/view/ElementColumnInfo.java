package com.intellij.coverage.view;

import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.util.ui.ColumnInfo;

import java.util.Comparator;

/**
* User: anna
* Date: 1/9/12
*/
class ElementColumnInfo extends ColumnInfo<NodeDescriptor, String> {
  public ElementColumnInfo() {
    super("Element");
  }

  @Override
  public Comparator<NodeDescriptor> getComparator() {
    return AlphaComparator.INSTANCE;
  }

  @Override
  public String valueOf(NodeDescriptor node) {
    return node.toString();
  }
}
