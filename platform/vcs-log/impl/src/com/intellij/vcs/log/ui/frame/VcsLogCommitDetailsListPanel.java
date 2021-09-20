// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

public class VcsLogCommitDetailsListPanel extends CommitDetailsListPanel<CommitPanel> implements Disposable {
  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogColorManager myColorManager;

  public VcsLogCommitDetailsListPanel(@NotNull VcsLogData logData, @NotNull VcsLogColorManager colorManager, @NotNull Disposable parent) {
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
    CommitSelectionListenerForDetails listener = new CommitSelectionListenerForDetails(graphTable, this, this);
    graphTable.getSelectionModel().addListSelectionListener(listener);
  }

  private void branchesChanged() {
    forEachPanelIndexed((i, panel) -> {
      panel.updateBranches();
      return Unit.INSTANCE;
    });
  }

  @NotNull
  @Override
  protected CommitPanel getCommitDetailsPanel() {
    return new CommitPanel(myLogData, myColorManager, this::navigate);
  }

  @Override
  public void dispose() { }
}
