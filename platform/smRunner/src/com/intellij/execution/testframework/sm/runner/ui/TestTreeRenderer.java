/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerNodeDescriptor;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.ui.BaseTestProxyNodeDescriptor;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Collection;

/**
 * @author: Roman Chernyatchik
 */
public class TestTreeRenderer extends ColoredTreeCellRenderer {
  @NonNls private static final String SPACE_STRING = " ";

  private final TestConsoleProperties myConsoleProperties;
  private SMRootTestProxyFormatter myAdditionalRootFormatter;
  private int myDurationWidth = 20;
  private int myRow;
  private Object myProperty;

  public TestTreeRenderer(final TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  public void customizeCellRenderer(final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    myRow = row;
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
    final Object userObj = node.getUserObject();
    if (userObj instanceof SMTRunnerNodeDescriptor) {
      final SMTRunnerNodeDescriptor desc = (SMTRunnerNodeDescriptor)userObj;
      final SMTestProxy testProxy = desc.getElement();

      if (testProxy instanceof SMTestProxy.SMRootTestProxy) {
        SMTestProxy.SMRootTestProxy rootTestProxy = (SMTestProxy.SMRootTestProxy) testProxy;
        if (node.isLeaf()) {
          TestsPresentationUtil.formatRootNodeWithoutChildren(rootTestProxy, this);
        } else {
          TestsPresentationUtil.formatRootNodeWithChildren(rootTestProxy, this);
        }
        if (myAdditionalRootFormatter != null) {
          myAdditionalRootFormatter.format(rootTestProxy, this);
        }
      } else {
        TestsPresentationUtil.formatTestProxy(testProxy, this);
      }

      String durationString = testProxy.getDurationString();
      if (durationString != null) {
        durationString = "  [" + durationString + "]";
        myDurationWidth = tree.getFontMetrics(tree.getFont()).stringWidth(durationString);
        myProperty = tree.getClientProperty(ExpandableItemsHandler.EXPANDED_RENDERER);
        if (myProperty != null) {
          append(durationString);
        }
      }
      //Done
      return;
    }

    //strange node
    final String text = node.toString();
    //no icon
    append(text != null ? text : SPACE_STRING, SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }

  @NotNull
  @Override
  public Dimension getPreferredSize() {
    final Dimension preferredSize = super.getPreferredSize();
    return myProperty != null ? preferredSize : JBUI.size(preferredSize.width + myDurationWidth, preferredSize.height);
  }

  public TestConsoleProperties getConsoleProperties() {
    return myConsoleProperties;
  }

  public void setAdditionalRootFormatter(@NotNull SMRootTestProxyFormatter formatter) {
    myAdditionalRootFormatter = formatter;
  }

  public void removeAdditionalRootFormatter() {
    myAdditionalRootFormatter = null;
  }


  @Override
  protected void doPaint(Graphics2D g) {
    
    super.doPaint(g);
    final ExpandableItemsHandler<Integer> handler = ((Tree)myTree).getExpandableItemsHandler();
    final Collection<Integer> items = handler.getExpandedItems();
    if (Registry.is("tests_view_inline_statistics") && myRow >= 0 && !(items.size() == 1 && myRow == items.iterator().next())) {
      Rectangle visibleRect = myTree.getVisibleRect();
      final Rectangle bounds = getBounds();
      Object node = myTree.getPathForRow(myRow).getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        Object data = ((DefaultMutableTreeNode)node).getUserObject();
        if (data instanceof BaseTestProxyNodeDescriptor) {
          final AbstractTestProxy testProxy = ((BaseTestProxyNodeDescriptor)data).getElement();
          String durationString = testProxy.getDurationString();
          if (durationString != null) {
            durationString = "  [" + durationString+ "]";
            final Rectangle fullRowRect =
              new Rectangle(visibleRect.x, visibleRect.y + bounds.y, visibleRect.width - bounds.x, bounds.height);
            paintRowData(durationString, fullRowRect, g, myTree.isRowSelected(myRow));
          }
        }
      }
    }
  }

  private  void paintRowData(String duration, Rectangle bounds, Graphics2D g, boolean isSelected) {
    final Rectangle clipBounds = g.getClipBounds();
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    final FontMetrics metrics = myTree.getFontMetrics(myTree.getFont());
    int totalWidth = metrics.stringWidth(duration);
    int x = bounds.x + bounds.width - totalWidth;
    g.setClip(x, clipBounds.y, bounds.width, clipBounds.height);
    g.setColor(isSelected ? UIUtil.getTreeSelectionBackground(myTree.hasFocus()) : UIUtil.getTreeBackground());
    g.fillRect(0, 0, bounds.width, clipBounds.height);
    g.setColor(isSelected ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeForeground());
    g.drawString(duration, x, (int)metrics.getMaxCharBounds(g).getHeight());
    config.restore();
    g.setClip(clipBounds);
  }
  
}
