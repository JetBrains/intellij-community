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
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class TreeToolTipHandler extends AbstractToolTipHandler<Integer, JTree> {
  public static TreeToolTipHandler install(JTree tree) {
    return new TreeToolTipHandler(tree);
  }

  protected TreeToolTipHandler(final JTree tree) {
    super(tree);
    tree.getSelectionModel().addTreeSelectionListener(
      new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          try {
            updateSelection(tree);
          }
          catch (Exception e1) {
            // Workaround for some race conditions in Swing, see
            // http://www.intellij.net/tracker/idea/viewSCR?publicId=53961
          }
        }
      }
    );

    final TreeModelListener l = new TreeModelListener() {
      @Override
      public void treeNodesChanged(TreeModelEvent e) {
        updateSelection(tree);
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
    
    tree.getModel().addTreeModelListener(l);
    tree.addPropertyChangeListener("model", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateSelection(tree);

        if (evt.getOldValue() != null) {
          ((TreeModel)evt.getOldValue()).removeTreeModelListener(l);
        }
        if (evt.getNewValue() != null) {
          ((TreeModel)evt.getNewValue()).addTreeModelListener(l);
        }
      }
    });
  }

  private void updateSelection(JTree tree) {
    int selection = tree.getSelectionModel().getLeadSelectionRow();
    handleSelectionChange(selection == -1 ? null : new Integer(selection));
  }

  protected Integer getCellKeyForPoint(Point point) {
    int rowIndex = myComponent.getRowForLocation(point.x, point.y);
    return rowIndex != -1 ? new Integer(rowIndex) : null;
  }

  protected Rectangle getCellBounds(Integer key, Component rendererComponent) {
    final int rowIndex = key.intValue();
    final TreePath path = myComponent.getPathForRow(rowIndex);
    return myComponent.getPathBounds(path);
  }

  protected Component getRendererComponent(Integer key) {
    int rowIndex = key.intValue();
    Component rComponent;
    TreeCellRenderer renderer = myComponent.getCellRenderer();
    if (renderer == null) {
      rComponent = null;
    }
    else {
      TreePath path = myComponent.getPathForRow(rowIndex);
      if (path == null) {
        rComponent = null;
      }
      else {
        Object node = path.getLastPathComponent();
        rComponent = renderer.getTreeCellRendererComponent(
          myComponent,
          node,
          myComponent.isRowSelected(rowIndex),
          myComponent.isExpanded(rowIndex),
          myComponent.getModel().isLeaf(node),
          rowIndex,
          myComponent.hasFocus()
        );

      }
    }
    return rComponent;
  }

  @Override
  protected BufferedImage createImage(int height, int width) {
    final TreeUI ui = myComponent.getUI();
    return super.createImage(ui instanceof UIUtil.MacTreeUI && ((UIUtil.MacTreeUI)ui).isWideSelection() ? height - 1 : height, width);
  }

  @Override
  protected void doFillBackground(final int height, final int width, final Graphics2D g) {
    final TreeUI ui = myComponent.getUI();
    super.doFillBackground(ui instanceof UIUtil.MacTreeUI && ((UIUtil.MacTreeUI)ui).isWideSelection()? height - 1 : height, width, g);
  }

  @Override
  protected boolean doPaintBorder(final Integer row) {
    final TreeUI ui = myComponent.getUI();
    return (!(ui instanceof UIUtil.MacTreeUI) || !((UIUtil.MacTreeUI)ui).isWideSelection()) || !myComponent.isRowSelected(row);
  }

  @Override
  protected void doPaintTooltipImage(final Component rComponent, final Rectangle cellBounds, final int height, final Graphics2D g, Integer key) {
    if (myComponent.isRowSelected(key) && rComponent instanceof JComponent) {
      if (myComponent.hasFocus()) {
        ((JComponent)rComponent).setOpaque(true);
        rComponent.setBackground(UIUtil.getTreeSelectionBackground());
      } else if (myComponent.getUI() instanceof UIUtil.MacTreeUI && ((UIUtil.MacTreeUI)myComponent.getUI()).isWideSelection()) {
        ((JComponent)rComponent).setOpaque(true);
        rComponent.setBackground(UIUtil.getListUnfocusedSelectionBackground());
      }
    }

    super.doPaintTooltipImage(rComponent, cellBounds, height, g, key);
  }
}