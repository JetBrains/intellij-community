/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ui;

import com.intellij.util.containers.Convertor;
import com.intellij.ui.treeStructure.treetable.TreeTable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public class TreeTableSpeedSearch extends SpeedSearchBase<TreeTable> {
  private static final Convertor<TreePath, String> TO_STRING = new Convertor<TreePath, String>() {
    public String convert(TreePath object) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)object.getLastPathComponent();
      return node.toString();
    }
  };
  private final Convertor<TreePath, String> myToStringConvertor;

  public TreeTableSpeedSearch(TreeTable tree, Convertor<TreePath, String> toStringConvertor) {
    super(tree);
    myToStringConvertor = toStringConvertor;
  }

  public TreeTableSpeedSearch(TreeTable tree) {
    this(tree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING);
  }


  protected boolean isSpeedSearchEnabled() {
    return !getComponent().isEditing() && super.isSpeedSearchEnabled();
  }

  protected void selectElement(Object element, String selectedText) {
    final TreePath treePath = (TreePath)element;
    TableUtil.selectRows(myComponent, new int[] {myComponent.convertRowIndexToView(myComponent.getTree().getRowForPath(treePath))});
    TableUtil.scrollSelectionToVisible(myComponent);
  }

  protected int getSelectedIndex() {
    int[] selectionRows = myComponent.getTree().getSelectionRows();
    return selectionRows == null || selectionRows.length == 0 ? -1 : selectionRows[0];
  }

  protected Object[] getAllElements() {
    TreePath[] paths = new TreePath[myComponent.getTree().getRowCount()];
    for(int i = 0; i < paths.length; i++){
      paths[i] = myComponent.getTree().getPathForRow(i);
    }
    return paths;
  }

  protected String getElementText(Object element) {
    TreePath path = (TreePath)element;
    String string = myToStringConvertor.convert(path);
    if (string == null) return TreeTableSpeedSearch.TO_STRING.convert(path);
    return string;
  }
}
