/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class VcsLogImpl implements VcsLog {
  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogUiImpl myUi;

  public VcsLogImpl(@NotNull VcsLogData manager, @NotNull VcsLogUiImpl ui) {
    myLogData = manager;
    myUi = ui;
  }

  @Override
  @NotNull
  public List<CommitId> getSelectedCommits() {
    return getSelectedDataFromTable(GraphTableModel::getCommitIdAtRow);
  }

  @NotNull
  @Override
  public List<VcsShortCommitDetails> getSelectedShortDetails() {
    return getSelectedDataFromTable(GraphTableModel::getShortDetails);
  }

  @NotNull
  @Override
  public List<VcsFullCommitDetails> getSelectedDetails() {
    return getSelectedDataFromTable(GraphTableModel::getFullDetails);
  }

  @Override
  public void requestSelectedDetails(@NotNull Consumer<List<VcsFullCommitDetails>> consumer, @Nullable ProgressIndicator indicator) {
    List<Integer> rowsList = Ints.asList(myUi.getTable().getSelectedRows());
    myLogData.getCommitDetailsGetter()
      .loadCommitsData(getTable().getModel().convertToCommitIds(rowsList), consumer, indicator);
  }

  @Nullable
  @Override
  public Collection<String> getContainingBranches(@NotNull Hash commitHash, @NotNull VirtualFile root) {
    return myLogData.getContainingBranchesGetter().getContainingBranchesFromCache(root, commitHash);
  }

  @NotNull
  @Override
  public Future<Boolean> jumpToReference(final String reference) {
    SettableFuture<Boolean> future = SettableFuture.create();
    VcsLogRefs refs = myUi.getDataPack().getRefs();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<VcsRef> matchingRefs = refs.stream().filter(ref -> ref.getName().startsWith(reference)).collect(Collectors.toList());
      ApplicationManager.getApplication().invokeLater(() -> {
        if (matchingRefs.isEmpty()) {
          myUi.jumpToCommitByPartOfHash(reference, future);
        }
        else {
          VcsRef ref = Collections.min(matchingRefs, new VcsGoToRefComparator(myUi.getDataPack().getLogProviders()));
          myUi.jumpToCommit(ref.getCommitHash(), ref.getRoot(), future);
        }
      });
    });
    return future;
  }

  @NotNull
  @Override
  public Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myLogData.getLogProviders();
  }

  @NotNull
  private VcsLogGraphTable getTable() {
    return myUi.getTable();
  }

  @NotNull
  private <T> List<T> getSelectedDataFromTable(@NotNull BiFunction<GraphTableModel, Integer, T> dataGetter) {
    final int[] rows = myUi.getTable().getSelectedRows();
    return new AbstractList<T>() {
      @NotNull
      @Override
      public T get(int index) {
        return dataGetter.apply(getTable().getModel(), rows[index]);
      }

      @Override
      public int size() {
        return rows.length;
      }
    };
  }
}
