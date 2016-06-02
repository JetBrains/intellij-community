/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.frame;

import com.google.common.primitives.Ints;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.data.VcsLogData;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.List;

public abstract class CommitSelectionListener implements ListSelectionListener {
  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final JBLoadingPanel myLoadingPanel;

  @Nullable private ProgressIndicator myLastRequest;

  protected CommitSelectionListener(@NotNull VcsLogData data, @NotNull VcsLogGraphTable table, @NotNull JBLoadingPanel panel) {
    myLogData = data;
    myGraphTable = table;
    myLoadingPanel = panel;
  }

  @Override
  public void valueChanged(@Nullable ListSelectionEvent event) {
    if (event != null && event.getValueIsAdjusting()) return;

    if (myLastRequest != null) myLastRequest.cancel();
    myLastRequest = null;

    int rows = myGraphTable.getSelectedRowCount();
    if (rows < 1) {
      myLoadingPanel.stopLoading();
      onEmptySelection();
    }
    else {
      onSelection(myGraphTable.getSelectedRows());
      myLoadingPanel.startLoading();

      final EmptyProgressIndicator indicator = new EmptyProgressIndicator();
      myLastRequest = indicator;

      myLogData.getCommitDetailsGetter()
            .loadCommitsData(myGraphTable.getModel().convertToCommitIds(getSelectionToLoad()), detailsList -> {
        if (myLastRequest == indicator && !(indicator.isCanceled())) {
          myLastRequest = null;
          onDetailsLoaded(detailsList);
          myLoadingPanel.stopLoading();
        }
      }, indicator);
    }
  }

  @NotNull
  protected List<Integer> getSelectionToLoad() {
    return Ints.asList(myGraphTable.getSelectedRows());
  }

  @CalledInAwt
  protected abstract void onDetailsLoaded(@NotNull List<VcsFullCommitDetails> detailsList);

  @CalledInAwt
  protected abstract void onSelection(@NotNull int[] selection);

  @CalledInAwt
  protected abstract void onEmptySelection();
}