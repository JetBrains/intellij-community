// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.ContainingBranchesGetter;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class CommitPanel extends CommitDetailsPanel {
  @NotNull final VcsLogData myLogData;
  @NotNull final VcsLogColorManager myColorManager;
  @Nullable CommitId myCommit;

  public CommitPanel(@NotNull VcsLogData logData,
                     @NotNull VcsLogColorManager colorManager,
                     @NotNull Consumer<? super CommitId> navigate) {
    super(logData.getProject(),
          commitId -> {
            navigate.consume(commitId);
            return Unit.INSTANCE;
          });
    myLogData = logData;
    myColorManager = colorManager;
  }

  @Override
  public void setCommit(@NotNull CommitId commit, @NotNull CommitPresentationUtil.CommitPresentation presentation) {
    myCommit = commit;
    super.setCommit(commit, presentation);
    setBranches(myLogData.getContainingBranchesGetter().requestContainingBranches(commit.getRoot(), commit.getHash()));
    VirtualFile root = commit.getRoot();
    if (myColorManager.hasMultiplePaths()) {
      setRoot(new RootColor(root, VcsLogGraphTable.getRootBackgroundColor(root, myColorManager)));
    }
    else {
      setRoot(null);
    }
  }

  public void setRefs(@NotNull Collection<VcsRef> refs) {
    setRefs(sortRefs(refs));
  }

  @NotNull
  protected List<? extends VcsRef> sortRefs(@NotNull Collection<? extends VcsRef> refs) {
    VcsRef ref = ContainerUtil.getFirstItem(refs);
    if (ref == null) return ContainerUtil.emptyList();
    return ContainerUtil.sorted(refs, myLogData.getLogProvider(ref.getRoot()).getReferenceManager().getLabelsOrderComparator());
  }

  public void updateBranches() {
    if (myCommit != null) {
      ContainingBranchesGetter getter = myLogData.getContainingBranchesGetter();
      setBranches(getter.getContainingBranchesFromCache(myCommit.getRoot(), myCommit.getHash()));
    }
    else {
      setBranches(null);
    }
  }
}
