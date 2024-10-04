// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public abstract class FileHistoryModel {
  private final @NotNull VcsLogData myLogData;
  private final @NotNull VcsLogDiffHandler myDiffHandler;
  private final @NotNull VirtualFile myRoot;

  public FileHistoryModel(@NotNull VcsLogData data, @NotNull VcsLogDiffHandler handler, @NotNull VirtualFile root) {
    myLogData = data;
    myDiffHandler = handler;
    myRoot = root;
  }

  protected abstract @NotNull VisiblePack getVisiblePack();

  @NotNull
  VcsLogDiffHandler getDiffHandler() {
    return myDiffHandler;
  }

  public @Nullable VcsFileRevision createRevision(@Nullable VcsCommitMetadata commit) {
    if (commit == null) return null;
    if (isFileDeletedInCommit(commit.getId())) return VcsFileRevision.NULL;
    FilePath path = getPathInCommit(commit.getId());
    if (path == null) return null;
    return new VcsLogFileRevision(commit, myDiffHandler.createContentRevision(path, commit.getId()), path, false);
  }

  public @Nullable FilePath getPathInCommit(@NotNull Hash hash) {
    int commitIndex = myLogData.getStorage().getCommitIndex(hash, myRoot);
    return FileHistoryPaths.filePath(getVisiblePack(), commitIndex);
  }

  private boolean isFileDeletedInCommit(@NotNull Hash hash) {
    int commitIndex = myLogData.getStorage().getCommitIndex(hash, myRoot);
    return FileHistoryPaths.isDeletedInCommit(getVisiblePack(), commitIndex);
  }

  public @Nullable Change getSelectedChange(int @NotNull [] rows) {
    if (rows.length == 0) return null;

    int row = rows[0];
    VisiblePack visiblePack = getVisiblePack();
    List<Integer> parentRows;
    if (rows.length == 1) {
      if (VisiblePack.NO_GRAPH_INFORMATION.get(visiblePack, false) &&
          row + 1 < visiblePack.getVisibleGraph().getVisibleCommitCount()) {
        parentRows = Collections.singletonList(row + 1);
      }
      else {
        parentRows = visiblePack.getVisibleGraph().getRowInfo(row).getAdjacentRows(true);
      }
    }
    else {
      parentRows = Collections.singletonList(rows[rows.length - 1]);
    }
    return FileHistoryUtil.createChangeToParents(row, parentRows, visiblePack, myDiffHandler, myLogData);
  }

  public @NotNull FileHistoryModel createSnapshot() {
    return new Snapshot(myLogData, myDiffHandler, myRoot, getVisiblePack());
  }

  private static class Snapshot extends FileHistoryModel {
    private final @NotNull VisiblePack myVisiblePack;

    private Snapshot(@NotNull VcsLogData data,
                     @NotNull VcsLogDiffHandler handler,
                     @NotNull VirtualFile root,
                     @NotNull VisiblePack visiblePack) {
      super(data, handler, root);
      myVisiblePack = visiblePack;
    }

    @Override
    protected @NotNull VisiblePack getVisiblePack() {
      return myVisiblePack;
    }
  }
}
