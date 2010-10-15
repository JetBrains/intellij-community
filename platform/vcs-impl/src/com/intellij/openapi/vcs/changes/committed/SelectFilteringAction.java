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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;

import javax.swing.*;

/**
 * @author yole
 */
public class SelectFilteringAction extends LabeledComboBoxAction {
  private final Project myProject;
  private final CommittedChangesTreeBrowser myBrowser;
  private CommittedChangesFilterKey myPreviousSelection;

  public SelectFilteringAction(final Project project, final CommittedChangesTreeBrowser browser) {
    super(VcsBundle.message("committed.changes.filter.title"));
    myProject = project;
    myBrowser = browser;
    myPreviousSelection = null;
  }

  protected ComboBoxModel createModel() {
    final DefaultComboBoxModel model = new DefaultComboBoxModel(new Object[]{
      ChangeListFilteringStrategy.NONE,
      /*new ColumnFilteringStrategy(ChangeListColumn.NAME, provider.getClass()),*/
      new StructureFilteringStrategy(myProject)
    });
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss();
    boolean addNameFilter = false;
    for(AbstractVcs vcs: vcss) {
      final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      if (provider != null) {
        addNameFilter = true;
        for(ChangeListColumn column: provider.getColumns()) {
          if (ChangeListColumn.isCustom(column)) {
            model.addElement(new ColumnFilteringStrategy(column, provider.getClass()));
          }
        }
      }
    }
    if (addNameFilter) {
      model.addElement(new ColumnFilteringStrategy(ChangeListColumn.NAME, CommittedChangesProvider.class));
    }
    return model;
  }

  protected void selectionChanged(final Object selection) {
    if (selection == null) return;
    if (myPreviousSelection != null) {
        myBrowser.removeFilteringStrategy(myPreviousSelection);
    }
    final ChangeListFilteringStrategy strategy = (ChangeListFilteringStrategy)selection;
    if (!ChangeListFilteringStrategy.NONE.equals(selection)) {
      myBrowser.setFilteringStrategy(strategy);
    }
    myPreviousSelection = strategy.getKey();
  }
}
