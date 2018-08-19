// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.incrementalMerge.Change;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeList;
import com.intellij.openapi.diff.impl.util.DiffPanelOuterComponent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ApplyNonConflicts extends AnAction implements DumbAware {
  @Nullable private final DiffPanelOuterComponent myDiffPanel;

  public ApplyNonConflicts(@Nullable DiffPanelOuterComponent diffPanel) {
    ActionUtil.copyFrom(this, "Diff.ApplyNonConflicts");
    myDiffPanel = diffPanel;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final MergeList mergeList = MergeList.fromDataContext(e.getDataContext());
    assert mergeList != null;

    final List<Change> notConflicts = ContainerUtil.collect(getNotConflicts(mergeList));
    final Project project = mergeList.getLeftChangeList().getProject();

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, () -> {
      mergeList.startBulkUpdate();
      try {
        for (Change change : notConflicts) {
          Change.doApply(change, MergeList.BRANCH_SIDE);
        }
      }
      finally {
        mergeList.finishBulkUpdate();
      }
    }, null, DiffBundle.message("save.merge.result.command.name")));

    if (myDiffPanel != null) {
      myDiffPanel.requestScrollEditors();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    MergeList mergeList = MergeList.fromDataContext(e.getDataContext());
    e.getPresentation().setEnabled(getNotConflicts(mergeList).hasNext());
  }

  private static Iterator<Change> getNotConflicts(MergeList mergeList) {
    if (mergeList == null) return new ArrayList<Change>(1).iterator();
    return FilteringIterator.create(mergeList.getAllChanges(), MergeList.NOT_CONFLICTS);
  }
}
