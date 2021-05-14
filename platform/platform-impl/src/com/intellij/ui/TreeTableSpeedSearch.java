// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.openapi.util.Conditions;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.ListIterator;

public class TreeTableSpeedSearch extends SpeedSearchBase<TreeTable> {
  private static final Convertor<TreePath, String> TO_STRING = object -> {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)object.getLastPathComponent();
    return node.toString();
  };
  private final Convertor<? super TreePath, String> myToStringConvertor;
  protected boolean myCanExpand;

  public TreeTableSpeedSearch(TreeTable tree, Convertor<? super TreePath, String> toStringConvertor) {
    super(tree);
    myToStringConvertor = toStringConvertor;
  }

  public TreeTableSpeedSearch(TreeTable tree) {
    this(tree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING);
  }

  public void setCanExpand(boolean canExpand) {
    myCanExpand = canExpand;
  }

  @Override
  protected boolean isSpeedSearchEnabled() {
    return !getComponent().isEditing() && super.isSpeedSearchEnabled();
  }

  @Override
  protected void selectElement(Object element, String selectedText) {
    final TreePath treePath = (TreePath)element;
    TreeTableTree tree = myComponent.getTree();
    if (myCanExpand) tree.expandPath(treePath.getParentPath());
    int row = tree.getRowForPath(treePath);
    TableUtil.selectRows(myComponent, new int[] {
      myComponent.convertRowIndexToView(row)
    });
    TableUtil.scrollSelectionToVisible(myComponent);
  }

  @Override
  protected int getSelectedIndex() {
    if (myCanExpand) {
      return allPaths().indexOf(Conditions.equalTo(myComponent.getTree().getSelectionPath()));
    }
    int[] selectionRows = myComponent.getTree().getSelectionRows();
    return selectionRows == null || selectionRows.length == 0 ? -1 : selectionRows[0];
  }

  @NotNull
  @Override
  protected final ListIterator<Object> getElementIterator(int startingViewIndex) {
    return allPaths().addAllTo(new ArrayList<Object>()).listIterator(startingViewIndex);
  }

  @Override
  protected final int getElementCount() {
    return allPaths().size();
  }

  @NotNull
  protected JBIterable<TreePath> allPaths() {
    return TreeSpeedSearch.allPaths(getComponent().getTree(), myCanExpand);
  }

  @Override
  protected String getElementText(Object element) {
    TreePath path = (TreePath)element;
    String string = myToStringConvertor.convert(path);
    if (string == null) return TreeTableSpeedSearch.TO_STRING.convert(path);
    return string;
  }
}
