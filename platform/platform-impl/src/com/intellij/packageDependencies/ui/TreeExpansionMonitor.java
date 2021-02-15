// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies.ui;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.BiPredicate;

import static com.intellij.ui.tree.TreePathUtil.toTreePathArray;

public abstract class TreeExpansionMonitor<T> {
  public static TreeExpansionMonitor<DefaultMutableTreeNode> install(final JTree tree) {
    return install(tree, (o1, o2) -> Comparing.equal(o1.getUserObject(), o2.getUserObject()));
  }

  public static TreeExpansionMonitor<DefaultMutableTreeNode> install(final JTree tree, final BiPredicate<? super DefaultMutableTreeNode, ? super DefaultMutableTreeNode> equality) {
    return new TreeExpansionMonitor<>(tree) {
      @Override
      protected TreePath findPathByNode(final DefaultMutableTreeNode node) {
        Enumeration enumeration = ((DefaultMutableTreeNode)tree.getModel().getRoot()).breadthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
          final Object nextElement = enumeration.nextElement();
          if (nextElement instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode)nextElement;
            if (equality.test(child, node)) {
              return new TreePath(child.getPath());
            }
          }
        }
        return null;
      }
    };
  }

  private final Set<TreePath> myExpandedPaths = new HashSet<>();
  private List<T> mySelectionNodes = new ArrayList<>();
  private final JTree myTree;
  private boolean myFrozen = false;

  protected TreeExpansionMonitor(JTree tree) {
    myTree = tree;
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (myFrozen) return;
        mySelectionNodes = new ArrayList<>();
        TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          for (TreePath path : paths) {
            mySelectionNodes.add((T)path.getLastPathComponent());
          }
        }
      }
    });

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        if (myFrozen) return;
        TreePath path = event.getPath();
        if (path != null) {
          myExpandedPaths.add(path);
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        if (myFrozen) return;
        TreePath path = event.getPath();
        if (path != null) {
          TreePath[] allPaths = toTreePathArray(myExpandedPaths);
          for (TreePath treePath : allPaths) {
            if (treePath.equals(path) || path.isDescendant(treePath)) {
              myExpandedPaths.remove(treePath);
            }
          }
        }
      }
    });
  }

  public void freeze() {
    myFrozen = true;
  }

  public void unfreeze() {
    myFrozen = false;
  }

  public void restore() {
    freeze();
    for (final TreePath myExpandedPath : myExpandedPaths) {
      myTree.expandPath(findPathByNode((T)myExpandedPath.getLastPathComponent()));
    }
    for (T mySelectionNode : mySelectionNodes) {
      myTree.getSelectionModel().addSelectionPath(findPathByNode(mySelectionNode));
    }
    int selected = myTree.getLeadSelectionRow();
    if (selected != -1) {
      TreeUtil.showRowCentered(myTree, selected, false);
    }
    myFrozen = false;
  }

  protected abstract TreePath findPathByNode(final T node);

  public boolean isFreeze() {
    return myFrozen;
  }
}
