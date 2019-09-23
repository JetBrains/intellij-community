// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT;
import static java.util.stream.Collectors.toList;

public class ShowDiffFromLocalChangesActionProvider implements AnActionExtensionProvider {
  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(ChangesListView.DATA_KEY) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    ChangesListView view = e.getRequiredData(ChangesListView.DATA_KEY);

    Stream<Change> changes = view.getSelectedChanges();
    Stream<FilePath> unversionedFiles = view.getSelectedUnversionedFiles();

    if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      e.getPresentation().setEnabled(project != null && (changes.findAny().isPresent() || unversionedFiles.findAny().isPresent()));
    }
    else {
      e.getPresentation().setEnabled(project != null && canShowDiff(project, changes, unversionedFiles));
    }
  }

  private static boolean canShowDiff(@Nullable Project project, @NotNull Stream<? extends Change> changes, @NotNull Stream<FilePath> paths) {
    return paths.findAny().isPresent() ||
           changes.anyMatch(it -> ChangeDiffRequestProducer.canCreate(project, it));
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    ChangesListView view = e.getRequiredData(ChangesListView.DATA_KEY);
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    List<Change> changes = view.getSelectedChanges().collect(toList());
    List<VirtualFile> unversioned =
      view.getSelectedUnversionedFiles().map(FilePath::getVirtualFile).filter(Objects::nonNull).collect(toList());

    final boolean needsConversion = checkIfThereAreFakeRevisions(project, changes);

    if (needsConversion) {
      // this trick is essential since we are under some conditions to refresh changes;
      // but we can only rely on callback after refresh
      ChangeListManager.getInstance(project).invokeAfterUpdate(
        () -> {
          ChangesViewManager.getInstanceEx(project).refreshImmediately();
          List<Change> actualChanges = loadFakeRevisions(project, changes);
          showDiff(project, actualChanges, unversioned, view);
        },
        InvokeAfterUpdateMode.BACKGROUND_CANCELLABLE,
        ActionsBundle.actionText(IdeActions.ACTION_SHOW_DIFF_COMMON),
        ModalityState.current());
    }
    else {
      showDiff(project, changes, unversioned, view);
    }
  }


  private static boolean checkIfThereAreFakeRevisions(@NotNull Project project, @NotNull List<? extends Change> changes) {
    boolean needsConversion = false;
    for (Change change : changes) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision instanceof FakeRevision) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(beforeRevision.getFile());
        needsConversion = true;
      }
      if (afterRevision instanceof FakeRevision) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(afterRevision.getFile());
        needsConversion = true;
      }
    }
    return needsConversion;
  }

  @NotNull
  private static List<Change> loadFakeRevisions(@NotNull Project project, @NotNull List<? extends Change> changes) {
    List<Change> actualChanges = new ArrayList<>();
    for (Change change : changes) {
      actualChanges.addAll(ChangeListManager.getInstance(project).getChangesIn(ChangesUtil.getFilePath(change)));
    }
    return actualChanges;
  }


  private static void showDiff(@NotNull Project project,
                               @NotNull List<? extends Change> changes,
                               @NotNull List<? extends VirtualFile> unversioned,
                               @NotNull ChangesListView changesView) {
    if (changes.size() == 1 && unversioned.isEmpty()) { // show all changes from this changelist
      Change selectedChange = changes.get(0);
      List<Change> changelistChanges = changesView.getAllChangesFromSameChangelist(selectedChange);
      if (changelistChanges != null) {
        int selectedIndex = ContainerUtil.indexOf(changelistChanges, it -> ChangeListChange.HASHING_STRATEGY.equals(selectedChange, it));
        if (selectedIndex != -1) {
          showChangesDiff(project, ListSelection.createAt(changelistChanges, selectedIndex));
        }
        else {
          showChangesDiff(project, ListSelection.create(changelistChanges, selectedChange));
        }
        return;
      }
    }

    if (unversioned.size() == 1 && changes.isEmpty()) { // show all unversioned changes
      VirtualFile selectedFile = unversioned.get(0);
      List<VirtualFile> allUnversioned =
        changesView.getUnversionedFiles().map(FilePath::getVirtualFile).filter(Objects::nonNull).collect(toList());
      showUnversionedDiff(project, ListSelection.create(allUnversioned, selectedFile));
      return;
    }

    showSelectionDiff(project, changes, unversioned);
  }


  private static void showChangesDiff(@Nullable Project project,
                                      @NotNull ListSelection<? extends Change> selection) {
    ListSelection<ChangeDiffRequestChain.Producer> producers =
      selection.map(change -> ChangeDiffRequestProducer.create(project, change));

    showDiff(project, producers.getList(), producers.getSelectedIndex());
  }

  private static void showUnversionedDiff(@Nullable Project project,
                                          @NotNull ListSelection<? extends VirtualFile> selection) {
    ListSelection<ChangeDiffRequestChain.Producer> producers =
      selection.map(change -> UnversionedDiffRequestProducer.create(project, change));

    showDiff(project, producers.getList(), producers.getSelectedIndex());
  }

  private static void showSelectionDiff(@Nullable Project project,
                                        @NotNull List<? extends Change> changes,
                                        @NotNull List<? extends VirtualFile> unversioned) {
    List<ChangeDiffRequestChain.Producer> changeRequests =
      ContainerUtil.mapNotNull(changes, change -> ChangeDiffRequestProducer.create(project, change));
    List<ChangeDiffRequestChain.Producer> unversionedRequests =
      ContainerUtil.mapNotNull(unversioned, file -> UnversionedDiffRequestProducer.create(project, file));

    showDiff(project, ContainerUtil.concat(changeRequests, unversionedRequests), 0);
  }

  private static void showDiff(@Nullable Project project,
                               @NotNull List<? extends ChangeDiffRequestChain.Producer> producers,
                               int selected) {
    if (producers.isEmpty()) return;
    DiffRequestChain chain = new ChangeDiffRequestChain(producers, selected);
    chain.putUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true);
    if (project != null) setAllowExcludeFromCommit(project, chain);
    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT);
  }

  private static void setAllowExcludeFromCommit(@NotNull Project project, @NotNull DiffRequestChain chain) {
    boolean allowExcludeFromCommit = ChangesViewManager.getInstanceEx(project).isAllowExcludeFromCommit();
    chain.putUserData(ALLOW_EXCLUDE_FROM_COMMIT, allowExcludeFromCommit);
  }
}
