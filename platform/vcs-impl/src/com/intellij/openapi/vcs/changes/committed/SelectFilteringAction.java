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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.*;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
 */
public class SelectFilteringAction extends LabeledComboBoxAction {

  @NotNull private final Project myProject;
  @NotNull private final CommittedChangesTreeBrowser myBrowser;
  @NotNull private ChangeListFilteringStrategy myPreviousSelection;

  public SelectFilteringAction(@NotNull Project project, @NotNull CommittedChangesTreeBrowser browser) {
    super(VcsBundle.message("committed.changes.filter.title"));
    myProject = project;
    myBrowser = browser;
    myPreviousSelection = ChangeListFilteringStrategy.NONE;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setText(myPreviousSelection.toString());
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return new DefaultActionGroup(ContainerUtil.map(collectStrategies(), new NotNullFunction<ChangeListFilteringStrategy, AnAction>() {
      @NotNull
      @Override
      public AnAction fun(@NotNull ChangeListFilteringStrategy strategy) {
        return new SetFilteringAction(strategy);
      }
    }));
  }

  @NotNull
  @Override
  protected Condition<AnAction> getPreselectCondition() {
    return new Condition<AnAction>() {
      @Override
      public boolean value(@NotNull AnAction action) {
        return ((SetFilteringAction)action).myStrategy.getKey().equals(myPreviousSelection.getKey());
      }
    };
  }

  @NotNull
  private List<ChangeListFilteringStrategy> collectStrategies() {
    List<ChangeListFilteringStrategy> result = ContainerUtil.newArrayList();

    result.add(ChangeListFilteringStrategy.NONE);
    result.add(new StructureFilteringStrategy(myProject));

    boolean addNameFilter = false;
    for (AbstractVcs vcs : ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss()) {
      CommittedChangesProvider provider = vcs.getCommittedChangesProvider();

      if (provider != null) {
        addNameFilter = true;

        for (ChangeListColumn column : provider.getColumns()) {
          if (ChangeListColumn.isCustom(column)) {
            result.add(new ColumnFilteringStrategy(column, provider.getClass()));
          }
        }
      }
    }
    if (addNameFilter) {
      result.add(new ColumnFilteringStrategy(ChangeListColumn.NAME, CommittedChangesProvider.class));
    }

    return result;
  }

  private class SetFilteringAction extends DumbAwareAction {

    @NotNull private final ChangeListFilteringStrategy myStrategy;

    private SetFilteringAction(@NotNull ChangeListFilteringStrategy strategy) {
      super(strategy.toString());
      myStrategy = strategy;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (!ChangeListFilteringStrategy.NONE.equals(myPreviousSelection)) {
        myBrowser.removeFilteringStrategy(myPreviousSelection.getKey());
      }
      if (!ChangeListFilteringStrategy.NONE.equals(myStrategy)) {
        myBrowser.setFilteringStrategy(myStrategy);
      }
      myPreviousSelection = myStrategy;
    }
  }
}
