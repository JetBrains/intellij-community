/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;

public class ChooseActionsDialog extends DialogWrapper {
  private final ActionsTree myActionsTree;

  public ChooseActionsDialog(Component parent, Keymap keymap, QuickList[] quicklists) {
    super(parent, true);

    myActionsTree = new ActionsTree();
    myActionsTree.reset(keymap, quicklists);
    myActionsTree.getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    setTitle("Add Actions to Quick List");
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    panel.add(myActionsTree.getComponent());
    panel.setPreferredSize(new Dimension(400, 500));
    
    return panel;
  }
  
  public String[] getTreeSelectedActionIds() {
    TreePath[] paths = myActionsTree.getTree().getSelectionPaths();
    if (paths == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    ArrayList<String> actions = new ArrayList<String>();
    for (TreePath path : paths) {
      Object node = path.getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode defNode = (DefaultMutableTreeNode)node;
        Object userObject = defNode.getUserObject();
        if (userObject instanceof String) {
          actions.add((String)userObject);
        }
        else if (userObject instanceof QuickList) {
          actions.add(((QuickList)userObject).getActionId());
        }
      }
    }
    return ArrayUtil.toStringArray(actions);
  }
}
