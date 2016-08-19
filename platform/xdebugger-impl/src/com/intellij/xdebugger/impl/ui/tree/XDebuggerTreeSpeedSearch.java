/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Convertor;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.LinkedList;
import java.util.List;

class XDebuggerTreeSpeedSearch extends TreeSpeedSearch {
  public XDebuggerTreeSpeedSearch(XDebuggerTree tree, Convertor<TreePath, String> toStringConvertor) {
    super(tree, toStringConvertor, true);
  }

  @Nullable
  @Override
  protected Object findElement(String s) {
    String string = s.trim();

    XDebuggerTreeNode node = ObjectUtils.tryCast(myComponent.getLastSelectedPathComponent(), XDebuggerTreeNode.class);
    if (node == null) {
      node = ObjectUtils.tryCast(myComponent.getModel().getRoot(), XDebuggerTreeNode.class);
      if (node == null) {
        return null;
      }
    }
    return findPath(string, node, true);
  }

  private Object findPath(String string, XDebuggerTreeNode node, boolean checkChildren) {
    TreePath path = node.getPath();
    if (isMatchingElement(path, string)) {
      return path;
    }

    XDebuggerTreeNode parent = ObjectUtils.tryCast(node.getParent(), XDebuggerTreeNode.class);
    int nodeIndex;
    List<? extends TreeNode> parentChildren;
    if (parent != null) {
      parentChildren = parent.getChildren();
      nodeIndex = parentChildren.indexOf(node);
      if (nodeIndex != -1) {
        for (int i = nodeIndex + 1; i < parentChildren.size(); i++) {
          TreePath result = match(parentChildren.get(i), string);
          if (result != null) {
            return result;
          }
        }

        for (int i = nodeIndex - 1; i >= 0; i--) {
          TreePath result = match(parentChildren.get(i), string);
          if (result != null) {
            return result;
          }
        }
      }
    }
    else {
      nodeIndex = -1;
      parentChildren = null;
    }

    // check children up to x level
    if (checkChildren && !node.isLeaf()) {
      TreePath result = findInChildren(node, string);
      if (result != null) {
        return result;
      }
    }

    // check siblings children up to x level
    if (parent != null) {
      for (int i = nodeIndex + 1; i < parentChildren.size(); i++) {
        TreePath result = findInChildren(parentChildren.get(i), string);
        if (result != null) {
          return result;
        }
      }

      for (int i = nodeIndex - 1; i >= 0; i--) {
        TreePath result = findInChildren(parentChildren.get(i), string);
        if (result != null) {
          return result;
        }
      }

      return findPath(string, parent, false);
    }

    //myComponent.getSelectionPath()
    return null;
  }

  private TreePath findInChildren(TreeNode node, String string) {
    if (node.isLeaf() || !(node instanceof XDebuggerTreeNode)) {
      return null;
    }

    LinkedList<XDebuggerTreeNode> queue = new LinkedList<>();
    queue.addLast((XDebuggerTreeNode)node);

    int initialLevel = ((XDebuggerTreeNode)node).getPath().getPathCount();

    while (!queue.isEmpty()) {
      XDebuggerTreeNode p = queue.removeFirst();

      if ((p.getPath().getPathCount() - initialLevel) > 3) {
        return null;
      }

      List<? extends TreeNode> children = p.getChildren();
      if (children.isEmpty()) {
        continue;
      }

      for (TreeNode child : children) {
        if (!(child instanceof XDebuggerTreeNode)) {
          continue;
        }

        TreePath result = match(child, string);
        if (result != null) {
          return result;
        }

        if (!child.isLeaf()) {
          queue.addLast((XDebuggerTreeNode)child);
        }
      }
    }

    return null;
  }

  @Nullable
  private TreePath match(TreeNode node, String string) {
    TreePath path = node instanceof XDebuggerTreeNode ? ((XDebuggerTreeNode)node).getPath() : null;
    return isMatchingElement(path, string) ? path : null;
  }
}