/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.breakpoints.ui.tree;

import com.intellij.ui.CheckboxTree;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;

import javax.swing.*;

class BreakpointsTreeCellRenderer  {
  private static void customizeRenderer(JTree tree,
                                       Object value,
                                       boolean selected,
                                       boolean expanded,
                                       boolean leaf,
                                       int row,
                                       boolean hasFocus,
                                       ColoredTreeCellRenderer renderer) {
    if (value instanceof BreakpointItemNode) {
      BreakpointItemNode node = (BreakpointItemNode)value;
      BreakpointItem breakpoint = node.getBreakpointItem();
      breakpoint.setupRenderer(renderer);
    }
    else if (value instanceof BreakpointsGroupNode) {
      XBreakpointGroup group = ((BreakpointsGroupNode)value).getGroup();
      renderer.setIcon(group.getIcon(expanded));
      renderer.append(group.getName(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }
  }

  public static class BreakpointsCheckboxTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      BreakpointsTreeCellRenderer.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus, getTextRenderer());
    }
  }

  public static class BreakpointsSimpleTreeCellRenderer extends ColoredTreeCellRenderer {

    @Override
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      BreakpointsTreeCellRenderer.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus, this);
    }
  }
}
