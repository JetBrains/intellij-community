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

import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class TreeExpandableItemsHandler extends AbstractExpandableItemsHandler<Integer, JTree> {
  protected TreeExpandableItemsHandler(final JTree tree) {
    super(tree);
    final TreeSelectionListener selectionListener = new TreeSelectionListener() {
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
    handleSelectionChange(selection == -1 ? null : new Integer(selection));
  }

  protected Integer getCellKeyForPoint(Point point) {
    int rowIndex = myComponent.getRowForLocation(point.x, point.y);
    return rowIndex != -1 ? new Integer(rowIndex) : null;
  }

  protected Pair<Component, Rectangle> getRendererAndBounds(Integer key) {
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
  protected BufferedImage createImage(int height, int width) {
    final TreeUI ui = myComponent.getUI();
    return super.createImage(ui instanceof UIUtil.MacTreeUI && ((UIUtil.MacTreeUI)ui).isWideSelection() ? height - 1 : height, width);
  }

  @Override
  protected void doFillBackground(final int height, final int width, final Graphics2D g) {
    final TreeUI ui = myComponent.getUI();
    super.doFillBackground(ui instanceof UIUtil.MacTreeUI && ((UIUtil.MacTreeUI)ui).isWideSelection() ? height - 1 : height, width, g);
  }

  @Override
  protected void doPaintTooltipImage(final Component rComponent,
                                     final Rectangle cellBounds,
                                     final int height,
                                     final Graphics2D g,
                                     Integer key) {
    if (myComponent.isRowSelected(key) && rComponent instanceof JComponent) {
      if (myComponent.hasFocus()) {
        ((JComponent)rComponent).setOpaque(true);
        rComponent.setBackground(UIUtil.getTreeSelectionBackground());
      }
      else if (myComponent.getUI() instanceof UIUtil.MacTreeUI && ((UIUtil.MacTreeUI)myComponent.getUI()).isWideSelection()) {
        ((JComponent)rComponent).setOpaque(true);
        rComponent.setBackground(UIUtil.getListUnfocusedSelectionBackground());
      }
    }

    super.doPaintTooltipImage(rComponent, cellBounds, height, g, key);
  }
}