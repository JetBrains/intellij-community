// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.TriConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel;
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil.CommitPresentation;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.vcs.log.ui.frame.CommitPresentationUtil.buildPresentation;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogCommitDetailsListPanel extends CommitDetailsListPanel<CommitPanel> implements Disposable {
  @NotNull private final VcsLogData myLogData;

  @NotNull private final VcsLogColorManager myColorManager;

  @NotNull private List<Integer> mySelection = ContainerUtil.emptyList();
  @NotNull private final CommitDataLoader myRefsLoader = new CommitDataLoader();
  @NotNull private final CommitDataLoader myHashesResolver = new CommitDataLoader();

  public VcsLogCommitDetailsListPanel(@NotNull VcsLogData logData,
                                      @NotNull VcsLogColorManager colorManager,
                                      @NotNull Disposable parent) {
    super(parent);
    myLogData = logData;
    myColorManager = colorManager;

    logData.getProject().getMessageBus().connect(this).subscribe(CommitMessageInspectionProfile.TOPIC, () -> update());

    Runnable containingBranchesListener = this::branchesChanged;
    myLogData.getContainingBranchesGetter().addTaskCompletedListener(containingBranchesListener);
    Disposer.register(this, () -> {
      myLogData.getContainingBranchesGetter().removeTaskCompletedListener(containingBranchesListener);
    });

    setStatusText(VcsLogBundle.message("vcs.log.commit.details.status"));
    Disposer.register(parent, this);
  }

  public void installCommitSelectionListener(@NotNull VcsLogGraphTable graphTable) {
    graphTable.getSelectionModel().addListSelectionListener(new CommitSelectionListenerForDetails(graphTable));
  }

  private void branchesChanged() {
    forEachPanelIndexed((i, panel) -> {
      panel.updateBranches();
      return Unit.INSTANCE;
    });
  }

  @NotNull
  private List<CommitPresentation> doResolveHashes(@NotNull List<? extends CommitPresentation> presentations,
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

  private void setPresentations(@NotNull List<? extends CommitId> ids,
                                @NotNull List<? extends CommitPresentation> presentations) {
    forEachPanelIndexed((i, panel) -> {
      panel.setCommit(ids.get(i), presentations.get(i));
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

  @NotNull
  @Override
  protected CommitPanel getCommitDetailsPanel() {
    return new CommitPanel(myLogData, myColorManager, this::navigate);
  }

  private class CommitDataLoader {
    @Nullable private ProgressIndicator myProgressIndicator = null;

    private <T> void loadData(@NotNull Function<ProgressIndicator, List<T>> loadData,
                              @NotNull TriConsumer<CommitPanel, Integer, T> setData) {
      List<Integer> currentSelection = mySelection;
      myProgressIndicator = BackgroundTaskUtil.executeOnPooledThread(VcsLogCommitDetailsListPanel.this, () -> {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        List<T> loaded = loadData.apply(indicator);
        ApplicationManager.getApplication()
          .invokeLater(() -> {
                         myProgressIndicator = null;
                         forEachPanelIndexed((i, panel) -> {
                           setData.accept(panel, i, loaded.get(i));
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

  private class CommitSelectionListenerForDetails extends CommitSelectionListener<VcsCommitMetadata> {
    CommitSelectionListenerForDetails(VcsLogGraphTable graphTable) {
      super(graphTable, VcsLogCommitDetailsListPanel.this.myLogData.getMiniDetailsGetter());
    }

    @Override
    protected void onDetailsLoaded(@NotNull List<? extends VcsCommitMetadata> detailsList) {
      List<CommitId> ids = ContainerUtil.map(detailsList,
                                             detail -> new CommitId(detail.getId(), detail.getRoot()));
      Set<String> unResolvedHashes = new HashSet<>();
      List<CommitPresentation> presentations = ContainerUtil.map(detailsList,
                                                                 detail -> buildPresentation(myLogData.getProject(), detail,
                                                                                             unResolvedHashes));
      forEachPanelIndexed((i, panel) -> {
        panel.setCommit(ids.get(i), presentations.get(i));
        return Unit.INSTANCE;
      });

      if (!unResolvedHashes.isEmpty()) {
        myHashesResolver.loadData(indicator -> doResolveHashes(presentations, unResolvedHashes),
                                  (panel, index, presentation) -> panel.setCommit(ids.get(index), presentation));
      }
    }

    @Override
    protected void onSelection(int @NotNull [] selection) {
      cancelLoading();

      int shownPanelsCount = rebuildPanel(selection.length);
      mySelection = Ints.asList(Arrays.copyOf(selection, shownPanelsCount));

      List<Integer> currentSelection = mySelection;
      myRefsLoader.loadData(indicator -> ContainerUtil.map2List(currentSelection, row -> myGraphTable.getModel().getRefsAtRow(row)),
                            (panel, index, refs) -> panel.setRefs(panel.sortRefs(refs)));
    }

    @Override
    protected void onEmptySelection() {
      cancelLoading();
      setEmpty(VcsLogBundle.message("vcs.log.changes.details.no.commits.selected.status"));
    }

    @NotNull
    @Override
    protected List<Integer> getSelectionToLoad() {
      return mySelection;
    }

    @Override
    protected void startLoading() {
      startLoadingDetails();
    }

    @Override
    protected void stopLoading() {
      stopLoadingDetails();
    }

    @Override
    protected void onError(@NotNull Throwable error) {
      setEmpty(VcsLogBundle.message("vcs.log.error.loading.status"));
    }

    private void setEmpty(@Nls @NotNull String text) {
      setStatusText(text);
      mySelection = ContainerUtil.emptyList();
      setCommits(ContainerUtil.emptyList());
    }
  }
}
