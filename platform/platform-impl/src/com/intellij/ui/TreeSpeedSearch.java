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

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

public class TreeSpeedSearch extends SpeedSearchBase<JTree> {
  private boolean myCanExpand;

  private static final Convertor<TreePath, String> TO_STRING = new Convertor<TreePath, String>() {
    @Override
    public String convert(TreePath object) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)object.getLastPathComponent();
      return node.toString();
    }
  };
  private final Convertor<TreePath, String> myToStringConvertor;
  public static final Convertor<TreePath, String> NODE_DESCRIPTOR_TOSTRING = new Convertor<TreePath, String>() {
    @Override
    public String convert(TreePath path) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      final Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        NodeDescriptor descr = (NodeDescriptor)userObject;
        return descr.toString();
      }
      return TO_STRING.convert(path);
    }
  };

  public TreeSpeedSearch(JTree tree, Convertor<TreePath, String> toStringConvertor) {
    this(tree, toStringConvertor, false);
  }

  public TreeSpeedSearch(JTree tree) {
    this(tree, TO_STRING);
  }

  public TreeSpeedSearch(Tree tree, Convertor<TreePath, String> toString) {
    this(tree, toString, false);
  }

  public TreeSpeedSearch(Tree tree, Convertor<TreePath, String> toString, boolean canExpand) {
    this((JTree)tree, toString, canExpand);
  }

  public TreeSpeedSearch(JTree tree, Convertor<TreePath, String> toString, boolean canExpand) {
    super(tree);
    setComparator(new SpeedSearchComparator(false, true));
    myToStringConvertor = toString;
    myCanExpand = canExpand;
  }

  @Override
  protected void selectElement(Object element, String selectedText) {
    TreeUtil.selectPath(myComponent, (TreePath)element);
  }

  @Override
  protected int getSelectedIndex() {
    if (myCanExpand) {
      return ArrayUtilRt.find(getAllElements(), myComponent.getSelectionPath());
    }
    int[] selectionRows = myComponent.getSelectionRows();
    return selectionRows == null || selectionRows.length == 0 ? -1 : selectionRows[0];
  }

  @Override
  protected Object[] getAllElements() {
    if (myCanExpand) {
      final Object root = myComponent.getModel().getRoot();
      if (root instanceof DefaultMutableTreeNode || root instanceof PathAwareTreeNode) {
        final List<TreePath> paths = new ArrayList<>();
        TreeUtil.traverseDepth((TreeNode)root, new TreeUtil.Traverse() {
          @Override
          public boolean accept(Object node) {
            if (node instanceof DefaultMutableTreeNode) {
              paths.add(new TreePath(((DefaultMutableTreeNode)node).getPath()));
            }
            else if (node instanceof PathAwareTreeNode) {
              paths.add(((PathAwareTreeNode)node).getPath());
            }
            return true;
          }
        });
        return paths.toArray(new TreePath[paths.size()]);
      }
    }
    TreePath[] paths = new TreePath[myComponent.getRowCount()];
    for (int i = 0; i < paths.length; i++) {
      paths[i] = myComponent.getPathForRow(i);
    }
    return paths;

  }

  @Override
  protected String getElementText(Object element) {
    TreePath path = (TreePath)element;
    String string = myToStringConvertor.convert(path);
    if (string == null) return TO_STRING.convert(path);
    return string;
  }

  public interface PathAwareTreeNode extends TreeNode {
    TreePath getPath();
  }
}