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

import com.intellij.openapi.util.Condition;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.ui.VcsLogUI;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class VcsLogImpl implements VcsLog {

  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final VcsLogUI myUi;

  public VcsLogImpl(@NotNull VcsLogDataHolder holder, @NotNull VcsLogUI ui) {
    myDataHolder = holder;
    myUi = ui;
  }

  @Override
  @NotNull
  public List<Hash> getSelectedCommits() {
    List<Hash> hashes = ContainerUtil.newArrayList();
    JBTable table = myUi.getTable();
    for (int row : table.getSelectedRows()) {
      Hash hash = ((AbstractVcsLogTableModel)table.getModel()).getHashAtRow(row);
      if (hash != null) {
        hashes.add(hash);
      }
    }
    return hashes;
  }

  @NotNull
  @Override
  public List<VcsFullCommitDetails> getSelectedDetails() {
    List<VcsFullCommitDetails> details = ContainerUtil.newArrayList();
    JBTable table = myUi.getTable();
    for (int row : table.getSelectedRows()) {
      AbstractVcsLogTableModel model = (AbstractVcsLogTableModel)table.getModel();
      VcsFullCommitDetails commitDetails = model.getFullCommitDetails(row);
      if (commitDetails == null) {
        return ContainerUtil.emptyList();
      }
      details.add(commitDetails);
    }
    return details;
  }

  @Override
  @Nullable
  public VcsFullCommitDetails getDetailsIfAvailable(@NotNull final Hash hash) {
    return myDataHolder.getCommitDetailsGetter().getCommitDataIfAvailable(hash);
  }

  @Nullable
  @Override
  public Collection<String> getContainingBranches(@NotNull Hash commitHash) {
    return null;
  }

  @NotNull
  @Override
  public Collection<VcsRef> getAllReferences() {
    return myDataHolder.getDataPack().getRefsModel().getAllRefs();
  }

  @Override
  public void jumpToReference(final String reference) {
    Collection<VcsRef> references = getAllReferences();
    VcsRef ref = ContainerUtil.find(references, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getName().startsWith(reference);
      }
    });
    if (ref != null) {
      myUi.jumpToCommit(ref.getCommitHash());
    }
    else {
      myUi.jumpToCommitByPartOfHash(reference);
    }
  }

  @NotNull
  @Override
  public Component getToolbar() {
    return myUi.getToolbar();
  }

  @NotNull
  @Override
  public Collection<VcsLogProvider> getLogProviders() {
    return myDataHolder.getLogProviders();
  }

}
