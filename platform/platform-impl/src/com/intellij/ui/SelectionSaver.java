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

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;

public class SelectionSaver implements TreeSelectionListener, TreeModelListener, PropertyChangeListener{

  private final JTree myTree;
  private Collection<TreeNode> myCurrentSelection = new ArrayList<>();


  private SelectionSaver(JTree tree) {
    myTree = tree;
  }

  public static void installOn(JTree tree){
    new SelectionSaver(tree).install();
  }

  private void install(){
    myTree.getModel().addTreeModelListener(this);
    myTree.getSelectionModel().addTreeSelectionListener(this);
    myTree.addPropertyChangeListener(this);
  }

  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals(JTree.TREE_MODEL_PROPERTY)){
      ((TreeModel)evt.getOldValue()).removeTreeModelListener(this);
      ((TreeModel)evt.getNewValue()).addTreeModelListener(this);
    }
  }

  public void treeNodesRemoved(TreeModelEvent treeModelEvent) {
    TreePath pathToDelete = treeModelEvent.getTreePath();
    Object[] children = treeModelEvent.getChildren();

    for (int i = 0; i < children.length; i++) {
      Object nodeToDelete = children[i];
      if (myCurrentSelection.contains(nodeToDelete)){
        int deletedRow = myTree.getRowForPath(pathToDelete.pathByAddingChild(nodeToDelete));
        if (deletedRow == 0) return;
        TreePath treePath = new TreePath(myTree.getPathForRow(deletedRow - 1));
        myTree.getSelectionModel().addSelectionPath((TreePath)treePath.getLastPathComponent());
      }
    }

  }

  public void valueChanged(TreeSelectionEvent e) {
    myCurrentSelection = new ArrayList<>();
    TreePath[] selection = myTree.getSelectionModel().getSelectionPaths();
    if (selection == null) return;
    for (int i = 0; i < selection.length; i++) {
      TreePath treePath = selection[i];
      myCurrentSelection.add((TreeNode)treePath.getLastPathComponent());
    }
  }

  public void treeNodesChanged(TreeModelEvent e) {

  }

  public void treeNodesInserted(TreeModelEvent e) {

  }

  public void treeStructureChanged(TreeModelEvent e) {

  }
}
