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
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class TreeToolTipHandler extends AbstractToolTipHandler<Integer, JTree> {
  protected TreeToolTipHandler(JTree tree) {
    super(tree);
    tree.getSelectionModel().addTreeSelectionListener(
      new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          try {
            repaintHint();
          }
          catch (Exception e1) {
            // Workaround for some race conditions in Swing, see
            // http://www.intellij.net/tracker/idea/viewSCR?publicId=53961
          }
        }
      }
    );
  }

  public static void install(JTree tree) {
    new TreeToolTipHandler(tree);
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
    return super.createImage(myComponent.getUI() instanceof UIUtil.MacTreeUI ? height - 1 : height, width);
  }

  @Override
  protected void doFillBackground(final int height, final int width, final Graphics2D g) {
    super.doFillBackground(myComponent.getUI() instanceof UIUtil.MacTreeUI ? height - 1 : height, width, g);
  }

  @Override
  protected boolean doPaintBorder(final Integer row) {
    return !(myComponent.getUI() instanceof UIUtil.MacTreeUI) || !myComponent.isRowSelected(row);
  }

  @Override
  protected void doPaintTooltipImage(final Component rComponent, final Rectangle cellBounds, final int height, final Graphics2D g, Integer key) {
    if (myComponent.isRowSelected(key) && rComponent instanceof JComponent) {
      if (myComponent.hasFocus()) {
        ((JComponent)rComponent).setOpaque(true);
        rComponent.setBackground(UIUtil.getTreeSelectionBackground());
      } else if (myComponent.getUI() instanceof UIUtil.MacTreeUI) {
        ((JComponent)rComponent).setOpaque(true);
        rComponent.setBackground(UIUtil.getListUnfocusedSelectionBackground());
      }
    }

    super.doPaintTooltipImage(rComponent, cellBounds, height, g, key);
  }
}