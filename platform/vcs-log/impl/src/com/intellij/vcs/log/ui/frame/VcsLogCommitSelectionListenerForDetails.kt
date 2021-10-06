// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.google.common.primitives.Ints;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.ContainingBranchesGetter;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel;
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel;
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil.CommitPresentation;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.intellij.vcs.log.ui.frame.CommitPresentationUtil.buildPresentation;

public class VcsLogCommitSelectionListenerForDetails extends CommitSelectionListener<VcsCommitMetadata> implements Disposable {

  private final @NotNull VcsLogData myLogData;
  private final @NotNull ContainingBranchesGetter myContainingBranchesGetter;
  private final @NotNull VcsLogColorManager myColorManager;

  private final @NotNull CommitDetailsListPanel myDetailsPanel;

  private final @NotNull CommitDataLoader myRefsLoader = new CommitDataLoader();
  private final @NotNull CommitDataLoader myHashesResolver = new CommitDataLoader();

  private @NotNull List<Integer> mySelection = ContainerUtil.emptyList();

  VcsLogCommitSelectionListenerForDetails(@NotNull VcsLogGraphTable graphTable,
                                          @NotNull CommitDetailsListPanel detailsPanel,
                                          @NotNull Disposable parentDisposable) {
    super(graphTable, graphTable.getLogData().getMiniDetailsGetter());
    myLogData = graphTable.getLogData();
    myContainingBranchesGetter = myLogData.getContainingBranchesGetter();
    myColorManager = graphTable.getColorManager();
    myDetailsPanel = detailsPanel;

    Runnable containingBranchesListener = this::branchesChanged;
    myContainingBranchesGetter.addTaskCompletedListener(containingBranchesListener);
    Disposer.register(this, () -> {
      myContainingBranchesGetter.removeTaskCompletedListener(containingBranchesListener);
    });

    Disposer.register(parentDisposable, this);
  }

  @Override
  protected void onDetailsLoaded(@NotNull List<? extends VcsCommitMetadata> detailsList) {
    Set<String> unResolvedHashes = new HashSet<>();
    List<CommitPresentation> presentations =
      ContainerUtil.map(detailsList, detail -> buildPresentation(myLogData.getProject(), detail, unResolvedHashes));
    myDetailsPanel.forEachPanelIndexed((i, panel) -> {
      panel.setCommit(presentations.get(i));
      return Unit.INSTANCE;
    });

    if (!unResolvedHashes.isEmpty()) {
      myHashesResolver.loadData(indicator -> doResolveHashes(presentations, unResolvedHashes),
                                (panel, presentation) -> panel.setCommit(presentation));
    }
  }

  @Override
  protected void onSelection(int @NotNull [] selection) {
    cancelLoading();

    List<CommitId> commits = myGraphTable.getModel().getCommitIds(selection);
    List<CommitId> displayedCommits = myDetailsPanel.rebuildPanel(commits);

    mySelection = Ints.asList(Arrays.copyOf(selection, displayedCommits.size()));
    List<Integer> currentSelection = mySelection;

    myDetailsPanel.forEachPanel((commit, panel) -> {
      panel.setBranches(myContainingBranchesGetter.requestContainingBranches(commit.getRoot(), commit.getHash()));
      VirtualFile root = commit.getRoot();
      if (myColorManager.hasMultiplePaths()) {
        panel.setRoot(new CommitDetailsPanel.RootColor(root, VcsLogGraphTable.getRootBackgroundColor(root, myColorManager)));
      }
      else {
        panel.setRoot(null);
      }
      return Unit.INSTANCE;
    });

    myRefsLoader.loadData(indicator -> ContainerUtil.map2List(currentSelection, row -> myGraphTable.getModel().getRefsAtRow(row)),
                          (panel, refs) -> panel.setRefs(sortRefs(refs)));
  }

  private @NotNull List<? extends VcsRef> sortRefs(@NotNull Collection<? extends VcsRef> refs) {
    VcsRef ref = ContainerUtil.getFirstItem(refs);
    if (ref == null) return ContainerUtil.emptyList();
    return ContainerUtil.sorted(refs, myLogData.getLogProvider(ref.getRoot()).getReferenceManager().getLabelsOrderComparator());
  }

  @Override
  protected void onEmptySelection() {
    cancelLoading();
    setEmpty(VcsLogBundle.message("vcs.log.changes.details.no.commits.selected.status"));
  }

  @Override
  protected @NotNull List<Integer> getSelectionToLoad() {
    return mySelection;
  }

  @Override
  protected void onLoadingStarted() {
    myDetailsPanel.startLoadingDetails();
  }

  @Override
  protected void onLoadingStopped() {
    myDetailsPanel.stopLoadingDetails();
  }

  @Override
  protected void onError(@NotNull Throwable error) {
    setEmpty(VcsLogBundle.message("vcs.log.error.loading.status"));
  }

  private void setEmpty(@Nls @NotNull String text) {
    myDetailsPanel.setStatusText(text);
    mySelection = ContainerUtil.emptyList();
    myDetailsPanel.rebuildPanel(ContainerUtil.emptyList());
  }

  private @NotNull List<CommitPresentation> doResolveHashes(@NotNull List<? extends CommitPresentation> presentations,
                                                            @NotNull Set<String> unResolvedHashes) {
    MultiMap<String, CommitId> resolvedHashes = new MultiMap<>();

    Set<String> fullHashes = new HashSet<>(ContainerUtil.filter(unResolvedHashes, h -> h.length() == VcsLogUtil.FULL_HASH_LENGTH));
    for (String fullHash : fullHashes) {
      Hash hash = HashImpl.build(fullHash);
      for (VirtualFile root : myLogData.getRoots()) {
        CommitId id = new CommitId(hash, root);
        if (myLogData.getStorage().containsCommit(id)) {
          resolvedHashes.putValue(fullHash, id);
        }
      }
    }
    unResolvedHashes.removeAll(fullHashes);

    if (!unResolvedHashes.isEmpty()) {
      myLogData.getStorage().iterateCommits(commitId -> {
        for (String hashString : unResolvedHashes) {
          if (StringUtil.startsWithIgnoreCase(commitId.getHash().asString(), hashString)) {
            resolvedHashes.putValue(hashString, commitId);
          }
        }
        return true;
      });
    }

    return ContainerUtil.map2List(presentations, presentation -> presentation.resolve(resolvedHashes));
  }

  private void branchesChanged() {
    myDetailsPanel.forEachPanel((commit, panel) -> {
      panel.setBranches(myContainingBranchesGetter.requestContainingBranches(commit.getRoot(), commit.getHash()));
      return Unit.INSTANCE;
    });
  }

  private void cancelLoading() {
    myHashesResolver.cancelLoading();
    myRefsLoader.cancelLoading();
  }

  @Override
  public void dispose() {
    cancelLoading();
  }

  private class CommitDataLoader {
    @Nullable private ProgressIndicator myProgressIndicator = null;

    private <T> void loadData(@NotNull Function<ProgressIndicator, List<T>> loadData,
                              @NotNull BiConsumer<CommitDetailsPanel, T> setData) {
      List<Integer> currentSelection = mySelection;
      myProgressIndicator = BackgroundTaskUtil.executeOnPooledThread(VcsLogCommitSelectionListenerForDetails.this, () -> {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        List<T> loaded = loadData.apply(indicator);
        ApplicationManager.getApplication()
          .invokeLater(() -> {
                         myProgressIndicator = null;
                         myDetailsPanel.forEachPanelIndexed((i, panel) -> {
                           setData.accept(panel, loaded.get(i));
                           return Unit.INSTANCE;
                         });
                       },
                       Conditions.or(o -> myProgressIndicator != indicator,
                                     o -> currentSelection != mySelection
                       ));
      });
    }

    private void cancelLoading() {
      if (myProgressIndicator != null) {
        myProgressIndicator.cancel();
        myProgressIndicator = null;
      }
    }
  }

  public static void install(@NotNull VcsLogGraphTable graphTable,
                             @NotNull CommitDetailsListPanel detailsPanel,
                             @NotNull Disposable disposable) {
    VcsLogCommitSelectionListenerForDetails listener =
      new VcsLogCommitSelectionListenerForDetails(graphTable, detailsPanel, disposable);
    graphTable.getSelectionModel().addListSelectionListener(listener);
  }
}