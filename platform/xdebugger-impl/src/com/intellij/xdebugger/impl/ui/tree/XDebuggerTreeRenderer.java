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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerNodeLink;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;

import javax.swing.*;

/**
 * @author nik
 */
class XDebuggerTreeRenderer extends ColoredTreeCellRenderer {
  public void customizeCellRenderer(final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    XDebuggerTreeNode node = (XDebuggerTreeNode)value;
    node.getText().appendToComponent(this);
    final XDebuggerNodeLink link = node.getLink();
    if (link != null) {
      append(link.getLinkText(), SimpleTextAttributes.LINK_ATTRIBUTES, link);
    }
    setIcon(node.getIcon());
  }
}
