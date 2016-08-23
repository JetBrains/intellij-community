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

package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesTreeList;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class MoveChangesDialog extends DialogWrapper {
  private final ChangesTreeList<Change> myTreeList;

  public MoveChangesDialog(final Project project, Collection<Change> selected, final Set<ChangeList> changeLists, String title) {
    super(project, true);
    setTitle(title);
    myTreeList = new ChangesTreeList<Change>(project, selected, true, false, null, null) {

      @Override
      protected DefaultTreeModel buildTreeModel(List<Change> changes, ChangeNodeDecorator changeNodeDecorator) {
        TreeModelBuilder builder = new TreeModelBuilder(project, isShowFlatten());
        return builder.buildModel(new ArrayList<>(changeLists));
      }

      @Override
      protected List<Change> getSelectedObjects(ChangesBrowserNode<Change> node) {
        return node.getAllChangesUnder();
      }

      @Override
      protected Change getLeadSelectedObject(ChangesBrowserNode node) {
        final Object o = node.getUserObject();
        if (o instanceof Change) {
          return (Change) o;
        }
        return null;
      }
    };
    ArrayList<Change> changes = new ArrayList<>();
    for (ChangeList list : changeLists) {
      changes.addAll(list.getChanges());
    }
    myTreeList.setChangesToDisplay(changes);

    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(ScrollPaneFactory.createScrollPane(myTreeList), BorderLayout.CENTER);

    DefaultActionGroup actionGroup = new DefaultActionGroup(myTreeList.getTreeActions());
    panel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true).getComponent(), BorderLayout.NORTH);
    myTreeList.expandAll();
    myTreeList.repaint();
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTreeList;
  }

  public Collection<Change> getIncludedChanges() {
    return myTreeList.getIncludedChanges();
  }

  @Override
  public boolean isOKActionEnabled() {
    return !getIncludedChanges().isEmpty();
  }
}
