// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.google.common.primitives.Ints;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel;
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil.CommitPresentation;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.vcs.log.ui.frame.CommitPresentationUtil.buildPresentation;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogCommitDetailsListPanel extends CommitDetailsListPanel<CommitPanel> implements Disposable {
  @NotNull private final VcsLogData myLogData;

  @NotNull private final VcsLogColorManager myColorManager;

  @NotNull private List<Integer> mySelection = ContainerUtil.emptyList();
  @Nullable private ProgressIndicator myResolveIndicator = null;

  public VcsLogCommitDetailsListPanel(@NotNull VcsLogData logData,
                                      @NotNull VcsLogColorManager colorManager,
                                      @NotNull Disposable parent) {
    super(parent);
    myLogData = logData;
    myColorManager = colorManager;

    logData.getProject().getMessageBus().connect(this).subscribe(CommitMessageInspectionProfile.TOPIC, () -> update());

    setStatusText("Commit details");
    Disposer.register(parent, this);
  }

  public void installCommitSelectionListener(@NotNull VcsLogGraphTable graphTable) {
    graphTable.getSelectionModel().addListSelectionListener(new CommitSelectionListenerForDetails(graphTable));
  }

  public void branchesChanged() {
    forEachPanelIndexed((i, panel) -> {
      panel.updateBranches();
      return Unit.INSTANCE;
    });
  }

  private void resolveHashes(@NotNull List<? extends CommitId> ids,
                             @NotNull List<? extends CommitPresentation> presentations,
                             @NotNull Set<String> unResolvedHashes,
                             @NotNull Condition<Object> expired) {
    if (!unResolvedHashes.isEmpty()) {
      myResolveIndicator = BackgroundTaskUtil.executeOnPooledThread(this, () -> {
        MultiMap<String, CommitId> resolvedHashes = MultiMap.createSmart();

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
            return false;
          });
        }

        List<CommitPresentation> resolvedPresentations = ContainerUtil.map2List(presentations,
                                                                                presentation -> presentation.resolve(resolvedHashes));
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        ApplicationManager.getApplication().invokeLater(() -> {
                                                          myResolveIndicator = null;
                                                          setPresentations(ids, resolvedPresentations);
                                                        },
                                                        Conditions.or(o -> myResolveIndicator != indicator, expired));
      });
    }
  }

  private void cancelResolve() {
    if (myResolveIndicator != null) {
      myResolveIndicator.cancel();
      myResolveIndicator = null;
    }
  }

  private void setPresentations(@NotNull List<? extends CommitId> ids,
                                @NotNull List<? extends CommitPresentation> presentations) {
    forEachPanelIndexed((i, panel) -> {
      panel.setCommit(ids.get(i), presentations.get(i));
      return Unit.INSTANCE;
    });
  }

  @Override
  public void dispose() {
    cancelResolve();
  }

  @NotNull
  @Override
  protected CommitPanel getCommitDetailsPanel() {
    return new CommitPanel(myLogData, myColorManager, this::navigate);
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
      setPresentations(ids, presentations);

      List<Integer> currentSelection = mySelection;
      resolveHashes(ids, presentations, unResolvedHashes, o -> currentSelection != mySelection);
    }

    @Override
    protected void onSelection(@NotNull int[] selection) {
      cancelResolve();
      setStatusText("");

      int shownPanelsCount = rebuildPanel(selection.length);
      mySelection = Ints.asList(Arrays.copyOf(selection, shownPanelsCount));

      List<Integer> currentSelection = mySelection;
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        List<Collection<VcsRef>> result = new ArrayList<>();
        for (Integer row : currentSelection) {
          result.add(myGraphTable.getModel().getRefsAtRow(row));
        }
        ApplicationManager.getApplication().invokeLater(() -> {
          if (currentSelection == mySelection) {
            forEachPanelIndexed((i, panel) -> {
              panel.setRefs(result.get(i));
              return Unit.INSTANCE;
            });
          }
        });
      });
    }

    @Override
    protected void onEmptySelection() {
      cancelResolve();
      setEmpty("No commits selected");
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
      setEmpty("Error loading commits");
    }

    private void setEmpty(@NotNull String text) {
      setStatusText(text);
      mySelection = ContainerUtil.emptyList();
    }
  }
}
