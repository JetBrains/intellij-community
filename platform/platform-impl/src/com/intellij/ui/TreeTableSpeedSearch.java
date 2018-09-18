// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public class TreeTableSpeedSearch extends SpeedSearchBase<TreeTable> {
  private static final Convertor<TreePath, String> TO_STRING = object -> {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)object.getLastPathComponent();
    return node.toString();
  };
  private final Convertor<TreePath, String> myToStringConvertor;

  public TreeTableSpeedSearch(TreeTable tree, Convertor<TreePath, String> toStringConvertor) {
    super(tree);
    myToStringConvertor = toStringConvertor;
  }

  public TreeTableSpeedSearch(TreeTable tree) {
    this(tree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING);
  }


  @Override
  protected boolean isSpeedSearchEnabled() {
    return !getComponent().isEditing() && super.isSpeedSearchEnabled();
  }

  @Override
  protected void selectElement(Object element, String selectedText) {
    final TreePath treePath = (TreePath)element;
    final int row = myComponent.getTree().getRowForPath(treePath);
    TableUtil.selectRows(myComponent, new int[] {
      myComponent.convertRowIndexToView(row)
    });
    TableUtil.scrollSelectionToVisible(myComponent);
  }

  @Override
  protected int getSelectedIndex() {
    int[] selectionRows = myComponent.getTree().getSelectionRows();
    return selectionRows == null || selectionRows.length == 0 ? -1 : selectionRows[0];
  }

  @NotNull
  @Override
  protected Object[] getAllElements() {
    TreePath[] paths = new TreePath[myComponent.getTree().getRowCount()];
    for(int i = 0; i < paths.length; i++){
      paths[i] = myComponent.getTree().getPathForRow(i);
    }
    return paths;
  }

  @Override
  protected String getElementText(Object element) {
    TreePath path = (TreePath)element;
    String string = myToStringConvertor.convert(path);
    if (string == null) return TreeTableSpeedSearch.TO_STRING.convert(path);
    return string;
  }
}
