// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain.Producer;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT;

@ApiStatus.Internal
public class ShowDiffFromLocalChangesActionProvider implements AnActionExtensionProvider {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(ChangesListView.DATA_KEY) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    updateAvailability(e);
  }

  public static void updateAvailability(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Presentation presentation = e.getPresentation();
    String place = e.getPlace();

    if (e.getData(ChangesListView.DATA_KEY) == null) {
      presentation.setEnabled(false);
      return;
    }

    JBIterable<Change> changes = JBIterable.of(e.getData(VcsDataKeys.CHANGES));
    JBIterable<FilePath> unversionedFiles = JBIterable.from(e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY));

    if (ActionPlaces.MAIN_MENU.equals(place)) {
      presentation.setEnabled(project != null && (changes.isNotEmpty() || unversionedFiles.isNotEmpty()));
    }
    else {
      presentation.setEnabled(project != null && canShowDiff(project, changes, unversionedFiles));
    }

    if (ActionPlaces.CHANGES_VIEW_TOOLBAR.equals(place)) {
      presentation.setVisible(!ExperimentalUI.isNewUI());
    }
  }

  private static boolean canShowDiff(@Nullable Project project,
                                     @NotNull JBIterable<? extends Change> changes,
                                     @NotNull JBIterable<? extends FilePath> paths) {
    return paths.isNotEmpty() ||
           changes.filter(it -> ChangeDiffRequestProducer.canCreate(project, it)).isNotEmpty();
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    ChangesListView view = e.getData(ChangesListView.DATA_KEY);
    if (view == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    List<Change> changes = view.getSelectedChanges().toList();
    List<FilePath> unversioned = view.getSelectedUnversionedFiles().toList();

    final boolean needsConversion = checkIfThereAreFakeRevisions(project, changes);

    DiffRequestChain chain;
    if (needsConversion) {
      CompletableFuture<ListSelection<Producer>> resultRef = new CompletableFuture<>();
      // this trick is essential since we are under some conditions to refresh changes;
      // but we can only rely on callback after refresh
      ChangeListManager.getInstance(project).invokeAfterUpdate(true, () -> {
        ChangesViewManager.getInstanceEx(project).promiseRefresh().onProcessed(__ -> {
          try {
            List<? extends Change> actualChanges = loadFakeRevisions(project, changes);
            resultRef.complete(collectRequestProducers(project, actualChanges, unversioned, view));
          }
          catch (Throwable err) {
            resultRef.completeExceptionally(err);
          }
        });
      });

      chain = new ChangeDiffRequestChain.Async() {
        @Override
        protected @NotNull ListSelection<? extends Producer> loadRequestProducers() throws DiffRequestProducerException {
          try {
            return resultRef.get();
          }
          catch (InterruptedException | ExecutionException ex) {
            throw new DiffRequestProducerException(ex);
          }
        }
      };
    }
    else {
      ListSelection<Producer> producers = collectRequestProducers(project, changes, unversioned, view);
      if (producers.isEmpty()) return;
      chain = new ChangeDiffRequestChain(producers);
    }

    chain.putUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true);
    setAllowExcludeFromCommit(project, chain);
    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT);
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
  private static List<? extends Change> loadFakeRevisions(@NotNull Project project, @NotNull List<? extends Change> changes) {
    Collection<Change> allChanges = ChangeListManager.getInstance(project).getAllChanges();
    return VcsImplUtil.filterChangesUnder(allChanges, ChangesUtil.getPaths(changes)).toList();
  }

  @NotNull
  public static ListSelection<Producer> collectRequestProducers(@NotNull Project project,
                                                                @NotNull List<? extends Change> changes,
                                                                @NotNull List<? extends FilePath> unversioned,
                                                                @NotNull ChangesListView changesView) {
    if (changes.size() == 1 && unversioned.isEmpty()) { // show all changes from this changelist
      Change selectedChange = changes.get(0);
      List<Change> selectedChanges = changesView.getAllChangesFromSameChangelist(selectedChange);
      if (selectedChanges == null) {
        selectedChanges = changesView.getAllChangesFromSameAmendNode(selectedChange);
      }
      if (selectedChanges != null) {
        int selectedIndex = ContainerUtil.indexOf(selectedChanges, it -> ChangeListChange.HASHING_STRATEGY.equals(selectedChange, it));
        if (selectedIndex == -1) selectedIndex = selectedChanges.indexOf(selectedChange);
        return createChangeProducers(project, selectedChanges, selectedIndex);
      }
    }

    if (unversioned.size() == 1 && changes.isEmpty()) { // show all unversioned changes
      FilePath selectedFile = unversioned.get(0);
      List<FilePath> allUnversioned = changesView.getUnversionedFiles().toList();
      int selectedIndex = allUnversioned.indexOf(selectedFile);
      return createUnversionedProducers(project, allUnversioned, selectedIndex);
    }

    ListSelection<Producer> changeProducers = createChangeProducers(project, changes, 0);
    ListSelection<Producer> unversionedProducers = createUnversionedProducers(project, unversioned, 0);
    return ListSelection.createAt(ContainerUtil.concat(changeProducers.getList(), unversionedProducers.getList()), 0)
      .asExplicitSelection();
  }

  private static ListSelection<Producer> createChangeProducers(@NotNull Project project,
                                                               @NotNull List<? extends Change> changes,
                                                               int selected) {
    return ListSelection.createAt(changes, selected).map(change -> ChangeDiffRequestProducer.create(project, change));
  }

  private static ListSelection<Producer> createUnversionedProducers(@NotNull Project project,
                                                                    @NotNull List<? extends FilePath> unversioned,
                                                                    int selected) {
    return ListSelection.createAt(unversioned, selected).map(path -> UnversionedDiffRequestProducer.create(project, path));
  }

  private static void setAllowExcludeFromCommit(@NotNull Project project, @NotNull DiffRequestChain chain) {
    boolean allowExcludeFromCommit = ChangesViewManager.getInstanceEx(project).isAllowExcludeFromCommit();
    chain.putUserData(ALLOW_EXCLUDE_FROM_COMMIT, allowExcludeFromCommit);
  }
}
