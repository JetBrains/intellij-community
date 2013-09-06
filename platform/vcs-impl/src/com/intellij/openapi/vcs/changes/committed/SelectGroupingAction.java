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
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import javax.swing.*;
import java.util.Comparator;

/**
 * @author yole
 */
public class SelectGroupingAction extends LabeledComboBoxAction {
  private final Project myProject;
  private final CommittedChangesTreeBrowser myBrowser;

  public SelectGroupingAction(Project project, final CommittedChangesTreeBrowser browser) {
    super(VcsBundle.message("committed.changes.group.title"));
    myProject = project;
    myBrowser = browser;
    getComboBox().setPrototypeDisplayValue("Date+");
  }

  protected void selectionChanged(Object selection) {
    myBrowser.setGroupingStrategy((ChangeListGroupingStrategy)selection);
  }

  protected ComboBoxModel createModel() {
    DefaultComboBoxModel model =
      new DefaultComboBoxModel(new Object[]{new DateChangeListGroupingStrategy(), ChangeListGroupingStrategy.USER});
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss();
    for (AbstractVcs vcs : vcss) {
      final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      if (provider != null) {
        for (ChangeListColumn column : provider.getColumns()) {
          if (ChangeListColumn.isCustom(column) && column.getComparator() != null) {
            model.addElement(new CustomChangeListColumnGroupingStrategy(column));
          }
        }
      }
    }
    return model;
  }

  private static class CustomChangeListColumnGroupingStrategy
    implements ChangeListGroupingStrategy {

    private final ChangeListColumn<CommittedChangeList> myColumn;

    private CustomChangeListColumnGroupingStrategy(ChangeListColumn column) {
      // The column is coming from a call to CommittedChangesProvider::getColumns(), which is typed as
      //  simply "ChangeListColumn[]" without any additional type info. Inspecting the implementations
      //  of that method shows that all the ChangeListColumn's that are returned are actually
      //  ChangeListColumn<? extends CommittedChangeList>. Hence this cast, while ugly, is currently OK.
      //noinspection unchecked
      myColumn = (ChangeListColumn<CommittedChangeList>)column;
    }

    @Override
    public void beforeStart() {
    }

    @Override
    public boolean changedSinceApply() {
      return false;
    }

    @Override
    public String getGroupName(CommittedChangeList changeList) {
      return changeList.getBranch();
    }

    @Override
    public Comparator<CommittedChangeList> getComparator() {
      return myColumn.getComparator();
    }

    @Override
    public String toString() {
      return myColumn.getTitle();
    }
  }
}
