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

import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 *
 */
public class VcsLogImpl implements VcsLog {

  private final VcsLogManager myLogManager;

  public VcsLogImpl(VcsLogManager vcsLogManager) {
    myLogManager = vcsLogManager;
  }

  /**
   * Checks if the log is initialized.
   * TODO Temporary method until the old Git log is switched off and removed
   */
  public boolean isReady() {
    return myLogManager.getDataHolder() != null && myLogManager.getLogUi() != null;
  }

  @Override
  @NotNull
  public List<Hash> getSelectedCommits() {
    List<Hash> hashes = ContainerUtil.newArrayList();
    JBTable table = myLogManager.getLogUi().getTable();
    for (int row : table.getSelectedRows()) {
      Hash hash = ((AbstractVcsLogTableModel)table.getModel()).getHashAtRow(row);
      if (hash != null) {
        hashes.add(hash);
      }
    }
    return hashes;
  }

  @Override
  @Nullable
  public VcsFullCommitDetails getDetailsIfAvailable(@NotNull final Hash hash) {
    return myLogManager.getDataHolder().getCommitDetailsGetter().getCommitDataIfAvailable(hash);
  }

  @Nullable
  @Override
  public Collection<String> getContainingBranches(@NotNull Hash commitHash) {
    return null;
  }

}
