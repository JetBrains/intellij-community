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
package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.incrementalMerge.Change;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeList;
import com.intellij.openapi.diff.impl.util.DiffPanelOuterComponent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ApplyNonConflicts extends AnAction implements DumbAware {
  @Nullable private final DiffPanelOuterComponent myDiffPanel;

  public ApplyNonConflicts(@Nullable DiffPanelOuterComponent diffPanel) {
    super(DiffBundle.message("merge.dialog.apply.all.non.conflicting.changes.action.name"), null, AllIcons.Diff.ApplyNotConflicts);
    myDiffPanel = diffPanel;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    List<Change> notConflicts = ContainerUtil.collect(getNotConflicts(dataContext));
    for (Change change : notConflicts) {
      Change.apply(change, MergeList.BRANCH_SIDE);
    }
    if (myDiffPanel != null) {
      myDiffPanel.requestScrollEditors();
    }
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(getNotConflicts(e.getDataContext()).hasNext());
  }

  private static Iterator<Change> getNotConflicts(DataContext dataContext) {
    MergeList mergeList = MergeList.fromDataContext(dataContext);
    if (mergeList == null) return new ArrayList<Change>(1).iterator();
    return FilteringIterator.create(mergeList.getAllChanges(), MergeList.NOT_CONFLICTS);
  }
}
