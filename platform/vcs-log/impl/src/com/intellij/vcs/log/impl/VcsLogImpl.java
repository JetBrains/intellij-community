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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogDataManager;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

public class VcsLogImpl implements VcsLog {
  @NotNull private final VcsLogDataManager myDataManager;
  @NotNull private final VcsLogUiImpl myUi;

  public VcsLogImpl(@NotNull VcsLogDataManager manager, @NotNull VcsLogUiImpl ui) {
    myDataManager = manager;
    myUi = ui;
  }

  @Override
  @NotNull
  public List<CommitId> getSelectedCommits() {
    final int[] rows = myUi.getTable().getSelectedRows();
    return new AbstractList<CommitId>() {
      @Nullable
      @Override
      public CommitId get(int index) {
        return getTable().getGraphTableModel().getCommitIdAtRow(rows[index]);
      }

      @Override
      public int size() {
        return rows.length;
      }
    };
  }

  private VcsLogGraphTable getTable() {
    return myUi.getTable();
  }

  @NotNull
  @Override
  public List<VcsFullCommitDetails> getSelectedDetails() {
    final int[] rows = myUi.getTable().getSelectedRows();
    return new AbstractList<VcsFullCommitDetails>() {
      @NotNull
      @Override
      public VcsFullCommitDetails get(int index) {
        return getTable().getGraphTableModel().getFullDetails(rows[index]);
      }

      @Override
      public int size() {
        return rows.length;
      }
    };
  }

  @Override
  public void requestSelectedDetails(@NotNull Consumer<List<VcsFullCommitDetails>> consumer, @Nullable ProgressIndicator indicator) {
    List<Integer> rowsList = Ints.asList(myUi.getTable().getSelectedRows());
    myDataManager.getCommitDetailsGetter()
      .loadCommitsData(getTable().getGraphTableModel().convertToHashesAndRoots(rowsList), consumer, indicator);
  }

  @Nullable
  @Override
  public Collection<String> getContainingBranches(@NotNull Hash commitHash, @NotNull VirtualFile root) {
    return myDataManager.getContainingBranchesGetter().getContainingBranchesFromCache(root, commitHash);
  }

  @NotNull
  @Override
  public Collection<VcsRef> getAllReferences() {
    return myUi.getDataPack().getRefs().getAllRefs();
  }

  @NotNull
  @Override
  public Future<Boolean> jumpToReference(final String reference) {
    Collection<VcsRef> references = getAllReferences();
    VcsRef ref = ContainerUtil.find(references, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getName().startsWith(reference);
      }
    });
    if (ref != null) {
      return myUi.jumpToCommit(ref.getCommitHash(), ref.getRoot());
    }
    else {
      return myUi.jumpToCommitByPartOfHash(reference);
    }
  }

  @NotNull
  @Override
  public Collection<VcsLogProvider> getLogProviders() {
    return myDataManager.getLogProviders();
  }
}
