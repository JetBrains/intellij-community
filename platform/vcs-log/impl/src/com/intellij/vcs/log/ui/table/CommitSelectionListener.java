// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.data.DataGetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.Arrays;
import java.util.List;

public abstract class CommitSelectionListener<T extends VcsCommitMetadata> implements ListSelectionListener {
  private static final Logger LOG = Logger.getInstance(CommitSelectionListener.class);
  protected final @NotNull VcsLogGraphTable myGraphTable;
  private final @NotNull DataGetter<? extends T> myCommitDetailsGetter;

  private @Nullable ListSelectionEvent myLastEvent;
  private @Nullable ProgressIndicator myLastRequest;

  protected CommitSelectionListener(@NotNull VcsLogGraphTable table,
                                    @NotNull DataGetter<? extends T> dataGetter) {
    myGraphTable = table;
    myCommitDetailsGetter = dataGetter;
  }

  @Override
  public void valueChanged(@Nullable ListSelectionEvent event) {
    if (event != null && event.getValueIsAdjusting()) return;

    myLastEvent = event;
    if (myLastRequest != null) myLastRequest.cancel();
    myLastRequest = null;

    ApplicationManager.getApplication().invokeLater(this::processEvent, o -> myLastEvent != event);
    onLoadingScheduled();
  }

  public void processEvent() {
    int rows = myGraphTable.getSelectedRowCount();
    if (rows < 1) {
      onLoadingStopped();
      onEmptySelection();
    }
    else {
      int[] toLoad = onSelection(myGraphTable.getSelectedRows());
      onLoadingStarted();

      EmptyProgressIndicator indicator = new EmptyProgressIndicator();
      myLastRequest = indicator;

      myCommitDetailsGetter.loadCommitsData(myGraphTable.getModel().createSelection(toLoad).getIds(), detailsList -> {
        if (myLastRequest == indicator && !(indicator.isCanceled())) {
          if (toLoad.length != detailsList.size()) {
            LOG.error("Loaded incorrect number of details " + detailsList + " for selection " + Arrays.toString(toLoad));
          }
          myLastRequest = null;
          onDetailsLoaded(detailsList);
          onLoadingStopped();
        }
      }, t -> {
        if (myLastRequest == indicator && !(indicator.isCanceled())) {
          myLastRequest = null;
          LOG.error("Error loading details for selection " + Arrays.toString(toLoad), t);
          onError(t);
          onLoadingStopped();
        }
      }, indicator);
    }
  }

  @RequiresEdt
  protected void onLoadingScheduled() {
  }

  @RequiresEdt
  protected abstract void onLoadingStarted();

  @RequiresEdt
  protected abstract void onLoadingStopped();

  @RequiresEdt
  protected abstract void onError(@NotNull Throwable error);

  @RequiresEdt
  protected abstract void onDetailsLoaded(@NotNull List<? extends T> detailsList);

  @RequiresEdt
  protected abstract int @NotNull [] onSelection(int @NotNull [] selection);

  @RequiresEdt
  protected abstract void onEmptySelection();
}