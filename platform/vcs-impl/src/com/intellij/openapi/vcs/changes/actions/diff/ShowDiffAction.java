// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ShowDiffAction implements AnActionExtensionProvider {
  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return true; // order="last"
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      e.getPresentation().setEnabled(project != null && changes != null && changes.length > 0);
    }
    else {
      e.getPresentation().setEnabled(project != null && canShowDiff(project, changes));
    }
  }

  public static boolean canShowDiff(@Nullable Project project, Change @Nullable [] changes) {
    return changes != null && canShowDiff(project, Arrays.asList(changes));
  }

  public static boolean canShowDiff(@Nullable Project project, @Nullable List<? extends Change> changes) {
    if (changes == null || changes.size() == 0) return false;
    for (Change change : changes) {
      if (ChangeDiffRequestProducer.canCreate(project, change)) return true;
    }
    return false;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final Change[] changes = e.getRequiredData(VcsDataKeys.CHANGES);

    List<Change> result = ContainerUtil.newArrayList(changes);
    showDiffForChange(project, result, 0);
  }

  //
  // Impl
  //

  public static void showDiffForChange(@Nullable Project project, @NotNull Iterable<? extends Change> changes) {
    showDiffForChange(project, changes, 0);
  }

  public static void showDiffForChange(@Nullable Project project, @NotNull Iterable<? extends Change> changes, int index) {
    showDiffForChange(project, changes, index, new ShowDiffContext());
  }

  public static void showDiffForChange(@Nullable Project project,
                                       @NotNull ListSelection<? extends Change> changes) {
    showDiffForChange(project, changes, new ShowDiffContext());
  }

  public static void showDiffForChange(@Nullable Project project,
                                       @NotNull Iterable<? extends Change> changes,
                                       @NotNull Condition<? super Change> condition,
                                       @NotNull ShowDiffContext context) {
    List<Change> list = ContainerUtil.newArrayList(changes);
    int index = ContainerUtil.indexOf(list, condition);
    showDiffForChange(project, ListSelection.createAt(list, index), context);
  }

  public static void showDiffForChange(@Nullable Project project,
                                       @NotNull Iterable<? extends Change> changes,
                                       int index,
                                       @NotNull ShowDiffContext context) {
    List<Change> list = ContainerUtil.newArrayList(changes);
    showDiffForChange(project, ListSelection.createAt(list, index), context);
  }

  public static void showDiffForChange(@Nullable Project project,
                                       @NotNull ListSelection<? extends Change> changes,
                                       @NotNull ShowDiffContext context) {
    ListSelection<ChangeDiffRequestProducer> presentables = changes.map(change -> ChangeDiffRequestProducer.create(project, change, context.getChangeContext(change)));
    if (presentables.isEmpty()) return;

    DiffRequestChain chain = new ChangeDiffRequestChain(presentables.getList(), presentables.getSelectedIndex());

    for (Map.Entry<Key<?>, Object> entry : context.getChainContext().entrySet()) {
      //noinspection unchecked,rawtypes
      chain.putUserData((Key)entry.getKey(), entry.getValue());
    }
    chain.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, context.getActions());

    DiffManager.getInstance().showDiff(project, chain, context.getDialogHints());
  }
}
