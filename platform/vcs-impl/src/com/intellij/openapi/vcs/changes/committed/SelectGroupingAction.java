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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;
import java.util.List;

/**
 * @author yole
 */
public class SelectGroupingAction extends LabeledComboBoxAction implements DumbAware {

  @NotNull private final Project myProject;
  @NotNull private final CommittedChangesTreeBrowser myBrowser;

  public SelectGroupingAction(@NotNull Project project, @NotNull CommittedChangesTreeBrowser browser) {
    super(VcsBundle.message("committed.changes.group.title"));
    myProject = project;
    myBrowser = browser;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setText(myBrowser.getGroupingStrategy().toString());
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return new DefaultActionGroup(
      ContainerUtil.map(collectStrategies(),
                        (NotNullFunction<ChangeListGroupingStrategy, DumbAwareAction>)strategy -> new SetGroupingAction(strategy)));
  }

  @NotNull
  @Override
  protected Condition<AnAction> getPreselectCondition() {
    return action -> ((SetGroupingAction)action).myStrategy.equals(myBrowser.getGroupingStrategy());
  }

  @NotNull
  private List<ChangeListGroupingStrategy> collectStrategies() {
    List<ChangeListGroupingStrategy> result = ContainerUtil.newArrayList();

    result.add(new DateChangeListGroupingStrategy());
    result.add(ChangeListGroupingStrategy.USER);

    for (AbstractVcs vcs : ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss()) {
      CommittedChangesProvider provider = vcs.getCommittedChangesProvider();

      if (provider != null) {
        for (ChangeListColumn column : provider.getColumns()) {
          if (ChangeListColumn.isCustom(column) && column.getComparator() != null) {
            result.add(new CustomChangeListColumnGroupingStrategy(column));
          }
        }
      }
    }

    return result;
  }

  private class SetGroupingAction extends DumbAwareAction {

    @NotNull private final ChangeListGroupingStrategy myStrategy;

    private SetGroupingAction(@NotNull ChangeListGroupingStrategy strategy) {
      super(strategy.toString());
      myStrategy = strategy;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myBrowser.setGroupingStrategy(myStrategy);
    }
  }

  private static class CustomChangeListColumnGroupingStrategy implements ChangeListGroupingStrategy {

    @NotNull private final ChangeListColumn<CommittedChangeList> myColumn;

    private CustomChangeListColumnGroupingStrategy(@NotNull ChangeListColumn column) {
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
    public String getGroupName(@NotNull CommittedChangeList changeList) {
      Object value = myColumn.getValue(changeList);

      return value != null ? value.toString() : null;
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
