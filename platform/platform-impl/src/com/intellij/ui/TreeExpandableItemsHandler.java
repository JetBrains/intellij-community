// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.hover.TreeHoverListener;
import com.intellij.ui.tree.ui.DefaultTreeUI;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public final class TreeExpandableItemsHandler extends AbstractExpandableItemsHandler<Integer, JTree> {
  protected TreeExpandableItemsHandler(final JTree tree) {
    super(tree);
    final TreeSelectionListener selectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        updateSelection(tree);
      }
    };
    tree.getSelectionModel().addTreeSelectionListener(selectionListener);
    tree.addPropertyChangeListener(JTree.SELECTION_MODEL_PROPERTY, new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateSelection(tree);

        if (evt.getOldValue() != null) {
          ((TreeSelectionModel)evt.getOldValue()).removeTreeSelectionListener(selectionListener);
        }
        if (evt.getNewValue() != null) {
          ((TreeSelectionModel)evt.getNewValue()).addTreeSelectionListener(selectionListener);
        }
      }
    });

    final TreeModelListener modelListener = new TreeModelListener() {
      @Override
      public void treeNodesChanged(TreeModelEvent e) {
        updateCurrentSelection();
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e) {
        updateSelection(tree);
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent e) {
        updateSelection(tree);
      }

      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        updateSelection(tree);
      }
    };

    if (tree.getModel() != null) tree.getModel().addTreeModelListener(modelListener);
    tree.addPropertyChangeListener("model", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateSelection(tree);

        if (evt.getOldValue() != null) {
          ((TreeModel)evt.getOldValue()).removeTreeModelListener(modelListener);
        }
        if (evt.getNewValue() != null) {
          ((TreeModel)evt.getNewValue()).addTreeModelListener(modelListener);
        }
      }
    });
  }

  private void updateSelection(JTree tree) {
    int selection = tree.getSelectionCount() == 1 ? tree.getSelectionModel().getLeadSelectionRow() : -1;
    handleSelectionChange(selection == -1 ? null : selection);
  }

  @Override
  protected Integer getCellKeyForPoint(Point point) {
    int row = TreeHoverListener.getHoveredRow(myComponent);
    if (row >= 0) return row;
    int rowIndex = myComponent.getRowForLocation(point.x, point.y);
    return rowIndex != -1 ? rowIndex : null;
  }

  @Override
  protected Pair<Component, Rectangle> getCellRendererAndBounds(Integer key) {
    int rowIndex = key.intValue();

    TreePath path = myComponent.getPathForRow(rowIndex);
    if (path == null) return null;

    Rectangle bounds = myComponent.getPathBounds(path);
    if (bounds == null) return null;

    TreeCellRenderer renderer = myComponent.getCellRenderer();
    if (renderer == null) return null;

    Object node = path.getLastPathComponent();
    Component rendererComponent = renderer.getTreeCellRendererComponent(
      myComponent,
      node,
      myComponent.isRowSelected(rowIndex),
      myComponent.isExpanded(rowIndex),
      myComponent.getModel().isLeaf(node),
      rowIndex,
      myComponent.hasFocus()
    );
    return Pair.create(rendererComponent, bounds);
  }

  @Override
  protected void doPaintTooltipImage(Component rComponent,
                                     Rectangle cellBounds,
                                     Graphics2D g,
                                     Integer key) {
    DefaultTreeUI.setBackground(myComponent, rComponent, key);
    super.doPaintTooltipImage(rComponent, cellBounds, g, key);
  }
}