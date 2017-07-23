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

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public abstract class CommitSelectionListenerForDiff extends CommitSelectionListener {
  protected CommitSelectionListenerForDiff(@NotNull VcsLogData logData, @NotNull VcsLogGraphTable graphTable) {
    super(logData, graphTable);
  }

  @Override
  protected void onDetailsLoaded(@NotNull List<VcsFullCommitDetails> detailsList) {
    List<Change> changes = ContainerUtil.newArrayList();
    List<VcsFullCommitDetails> detailsListReversed = ContainerUtil.reverse(detailsList);
    for (VcsFullCommitDetails details : detailsListReversed) {
      changes.addAll(details.getChanges());
    }
    changes = CommittedChangesTreeBrowser.zipChanges(changes);
    setChangesToDisplay(changes);
  }

  @Override
  protected void onSelection(@NotNull int[] selection) {
    // just reset and wait for details to be loaded
    clearChanges();
  }

  @Override
  protected void onEmptySelection() {
    setChangesToDisplay(Collections.emptyList());
  }

  protected abstract void setChangesToDisplay(@NotNull List<Change> changes);

  protected abstract void clearChanges();
}