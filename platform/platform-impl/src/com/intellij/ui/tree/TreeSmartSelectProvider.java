/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.tree;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.SmartSelectProvider;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class TreeSmartSelectProvider implements SmartSelectProvider<JTree> {
  @Override
  public void increaseSelection(JTree tree) {
    TreePath path = tree.getLeadSelectionPath();
    if (path == null) return;
    increaseSelection(path, tree);
  }

  private static void increaseSelection(TreePath path, JTree tree) {
    TreePath parentPath = path.getParentPath();

    if (parentPath == null) {
      return;
    }

    boolean madeSelection = false;

    for (int i = 0; i < tree.getRowCount(); i++) {
      TreePath row = tree.getPathForRow(i);
      if (parentPath.isDescendant(row) && !row.equals(parentPath)) {
        if (!tree.isRowSelected(i)) {
          madeSelection = true;
          addSelection(tree, row);
        }
      }
    }

    if (!madeSelection) {
      if (tree.isRowSelected(tree.getRowForPath(parentPath))) {
        increaseSelection(parentPath, tree);
      } else {
        addSelection(tree, parentPath);
      }
    }
  }

  private static void addSelection(JTree tree, TreePath path) {
    TreePath leadPath = tree.getLeadSelectionPath();
    tree.getSelectionModel().addSelectionPath(path);
    tree.setLeadSelectionPath(leadPath);
  }

  @Override
  public void decreaseSelection(JTree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    TreePath leadSelection = tree.getLeadSelectionPath();

    if (paths == null || paths.length < 2) return;
    Object[] selected = leadSelection.getPath();
    if (selected.length < 2) return;

    int i = 0;
    ArrayList<TreePath> toRemove = new ArrayList<>();
    while (i < selected.length) {
      for (TreePath path : paths) {
        if (!hasCommonStart(path, leadSelection, i + 1)) {
          toRemove.add(path);
        }
      }

      if (!toRemove.isEmpty()) {
        if (toRemove.size() == paths.length - 1 && tree.isPathSelected(leadSelection.getParentPath())) {
          tree.removeSelectionPath(leadSelection.getParentPath());
        } else {
          for (TreePath path : toRemove) {
            tree.removeSelectionPath(path);
          }
        }

        tree.setLeadSelectionPath(leadSelection);
        return;
      } else {
        i++;
      }
    }

    //in case lead selection is not leaf
    if (paths.length == tree.getSelectionCount()) {
      for (TreePath path : paths) {
        tree.removeSelectionPath(path);
      }
      tree.addSelectionPath(leadSelection);
      tree.setLeadSelectionPath(leadSelection);
    }
  }

  private static boolean hasCommonStart(TreePath path, TreePath leadSelection, int commonStartLength) {
    Object[] pathObjects = path.getPath();
    if (pathObjects.length < commonStartLength) return false;
    Object[] leadSelectionObjects = leadSelection.getPath();
    for (int i = 0; i < pathObjects.length && i < leadSelectionObjects.length && i < commonStartLength; i++) {
      if (pathObjects[i] == leadSelectionObjects[i]) {
        continue;
      }

      if (pathObjects[i] == null || !pathObjects[i].equals(leadSelectionObjects[i])) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Override
  public JTree getSource(DataContext context) {
    Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(context);
    return component instanceof JTree ? (JTree)component : null;
  }
}
