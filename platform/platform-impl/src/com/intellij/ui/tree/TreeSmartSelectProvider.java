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
    if (parentPath == null) return;
    boolean madeSelection = false;
    for (int i = 0; i < tree.getVisibleRowCount(); i++) {
      TreePath row = tree.getPathForRow(i);
      if (parentPath.isDescendant(row) && !row.equals(parentPath)) {
        if (!tree.isRowSelected(i)) {
          madeSelection = true;
          tree.getSelectionModel().addSelectionPath(row);
        }
      }
    }
    if (!madeSelection) {
      increaseSelection(parentPath, tree);
    }
  }

  @Override
  public void decreaseSelection(JTree tree) {
    //todo[kb]
  }

  @Nullable
  @Override
  public JTree getSource(DataContext context) {
    Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(context);
    return component instanceof JTree ? (JTree)component : null;
  }
}
