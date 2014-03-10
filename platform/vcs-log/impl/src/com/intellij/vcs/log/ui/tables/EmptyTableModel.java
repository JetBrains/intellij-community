/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.LoadMoreStage;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class EmptyTableModel extends AbstractVcsLogTableModel<GraphCommitCell> {

  public EmptyTableModel(@NotNull DataPack dataPack, @NotNull VcsLogDataHolder logDataHolder,
                         @NotNull VcsLogUiImpl ui, @NotNull LoadMoreStage loadMoreStage) {
    super(logDataHolder, ui, dataPack, loadMoreStage);
  }

  @Override
  public int getRowCount() {
    return 0;
  }

  @NotNull
  @Override
  public VirtualFile getRoot(int rowIndex) {
    return FAKE_ROOT;
  }

  @NotNull
  @Override
  protected GraphCommitCell getCommitColumnCell(int index, @Nullable VcsShortCommitDetails details) {
    return new GraphCommitCell("", Collections.<VcsRef>emptyList());
  }

  @NotNull
  @Override
  protected Class<GraphCommitCell> getCommitColumnClass() {
    return GraphCommitCell.class;
  }

  @Nullable
  @Override
  public Hash getHashAtRow(int row) {
    return null;
  }

  @Override
  public int getRowOfCommit(@NotNull Hash hash) {
    return -1;
  }

  @Override
  public int getRowOfCommitByPartOfHash(@NotNull String hash) {
    return -1;
  }

}
