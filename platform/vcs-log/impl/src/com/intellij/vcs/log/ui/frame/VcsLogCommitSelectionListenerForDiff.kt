// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.data.CommitDetailsGetter;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class VcsLogCommitSelectionListenerForDiff extends CommitSelectionListener<VcsFullCommitDetails> {
  private final @NotNull JBLoadingPanel myChangesLoadingPane;
  private final @NotNull VcsLogChangesBrowser myChangesBrowser;

  public VcsLogCommitSelectionListenerForDiff(
    @NotNull JBLoadingPanel changesLoadingPane,
    @NotNull VcsLogChangesBrowser changesBrowser,
    @NotNull VcsLogGraphTable graphTable,
    @NotNull CommitDetailsGetter commitDetailsGetter
  ) {
    super(graphTable, commitDetailsGetter);
    myChangesLoadingPane = changesLoadingPane;
    myChangesBrowser = changesBrowser;
  }

  @Override
  protected void onEmptySelection() {
    myChangesBrowser.setSelectedDetails(Collections.emptyList());
  }

  @Override
  protected void onDetailsLoaded(@NotNull List<? extends VcsFullCommitDetails> detailsList) {
    int maxSize = VcsLogUtil.getMaxSize(detailsList);
    if (maxSize > VcsLogUtil.getShownChangesLimit()) {
      String sizeText = VcsLogUtil.getSizeText(maxSize);
      myChangesBrowser.setEmptyWithText(statusText -> {
        statusText.setText(VcsLogBundle.message("vcs.log.changes.too.many.status", detailsList.size(), sizeText));
        statusText.appendSecondaryText(VcsLogBundle.message("vcs.log.changes.too.many.show.anyway.status.action"),
                                       SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                       e -> myChangesBrowser.setSelectedDetails(detailsList));
      });
    }
    else {
      myChangesBrowser.setSelectedDetails(detailsList);
    }
  }

  @Override
  protected int @NotNull [] onSelection(int @NotNull [] selection) {
    myChangesBrowser.setEmpty();
    return selection;
  }

  @Override
  protected void onLoadingStarted() {
    myChangesLoadingPane.startLoading();
  }

  @Override
  protected void onLoadingStopped() {
    myChangesLoadingPane.stopLoading();
  }

  @Override
  protected void onError(@NotNull Throwable error) {
    myChangesBrowser.setEmptyWithText(statusText -> statusText.setText(VcsLogBundle.message("vcs.log.error.loading.changes.status")));
  }
}
