// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vcs.vfs.VcsVirtualFolder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsChangesMerger;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.containers.ContainerUtil.*;

public final class FileHistoryUtil {
  @Nullable
  public static VirtualFile createVcsVirtualFile(@Nullable VcsFileRevision revision) {
    if (!VcsHistoryUtil.isEmpty(revision)) {
      if (revision instanceof VcsFileRevisionEx) {
        FilePath path = ((VcsFileRevisionEx)revision).getPath();
        return path.isDirectory()
               ? new VcsVirtualFolder(path.getPath(), null, VcsFileSystem.getInstance())
               : new VcsVirtualFile(path.getPath(), revision, VcsFileSystem.getInstance());
      }
    }
    return null;
  }

  public static boolean affectsFile(@NotNull Change change, @NotNull FilePath file, boolean isDeleted) {
    ContentRevision revision = isDeleted ? change.getBeforeRevision() : change.getAfterRevision();
    if (revision == null) return false;
    return file.equals(revision.getFile());
  }

  public static boolean affectsDirectory(@NotNull Change change, @NotNull FilePath directory) {
    return affectsDirectory(directory, change.getAfterRevision()) || affectsDirectory(directory, change.getBeforeRevision());
  }

  private static boolean affectsDirectory(@NotNull FilePath directory, @Nullable ContentRevision revision) {
    if (revision == null) return false;
    return VfsUtilCore.isAncestor(directory.getIOFile(), revision.getFile().getIOFile(), false);
  }

  @Nullable
  static Change createChangeToParents(int commitRow, @NotNull List<Integer> parentRows,
                                      @NotNull VisiblePack visiblePack, @NotNull VcsLogDiffHandler diffHandler,
                                      @NotNull VcsLogData logData) {
    int commitIndex = visiblePack.getVisibleGraph().getRowInfo(commitRow).getCommit();
    FilePath path = FileHistoryPaths.filePath(visiblePack, commitIndex);
    if (path == null) return null;
    Hash commitHash = logData.getCommitId(commitIndex).getHash();
    ContentRevision afterRevision = createContentRevision(commitHash, commitIndex, visiblePack, diffHandler);

    List<Integer> parentCommits = map(parentRows, r -> visiblePack.getVisibleGraph().getRowInfo(r).getCommit());
    if (parentCommits.isEmpty() && commitRow + 1 < visiblePack.getVisibleGraph().getVisibleCommitCount()) {
      parentCommits = Collections.singletonList(visiblePack.getVisibleGraph().getRowInfo(commitRow + 1).getCommit());
    }
    if (parentCommits.isEmpty()) {
      if (afterRevision == null) return null;
      return new Change(null, afterRevision);
    }

    List<Hash> parentHashes = map(parentCommits, c -> logData.getCommitId(c).getHash());
    List<Change> parentChanges = mapNotNull(toCollection(zip(parentCommits, parentHashes)), parent -> {
      ContentRevision beforeRevision = createContentRevision(parent.second, parent.first, visiblePack, diffHandler);
      if (afterRevision == null && beforeRevision == null) return null;
      return new Change(beforeRevision, afterRevision);
    });
    if (parentChanges.size() <= 1) {
      return getFirstItem(parentChanges);
    }
    return new MyVcsChangesMerger(commitHash, parentHashes, diffHandler).merge(path, parentChanges);
  }

  @Nullable
  private static ContentRevision createContentRevision(@NotNull Hash commitHash, int commitIndex, @NotNull VcsLogDataPack visiblePack,
                                                       @NotNull VcsLogDiffHandler diffHandler) {
    boolean isDeleted = FileHistoryPaths.isDeletedInCommit(visiblePack, commitIndex);
    if (isDeleted) return null;
    FilePath path = FileHistoryPaths.filePath(visiblePack, commitIndex);
    if (path == null) return null;
    return diffHandler.createContentRevision(path, commitHash);
  }

  private static final class MyVcsChangesMerger extends VcsChangesMerger {
    @NotNull private final Hash myCommit;
    @NotNull private final Hash myFirstParent;
    @NotNull private final VcsLogDiffHandler myDiffHandler;

    private MyVcsChangesMerger(@NotNull Hash commit, @NotNull List<Hash> parentCommits, @NotNull VcsLogDiffHandler diffHandler) {
      myCommit = commit;
      myFirstParent = Objects.requireNonNull(getFirstItem(parentCommits));
      myDiffHandler = diffHandler;
    }

    @NotNull
    @Override
    protected Change createChange(@NotNull Change.Type type, @Nullable FilePath beforePath, @Nullable FilePath afterPath) {
      ContentRevision beforeRevision = beforePath == null ? null : myDiffHandler.createContentRevision(beforePath, myFirstParent);
      ContentRevision afterRevision = afterPath == null ? null : myDiffHandler.createContentRevision(afterPath, myCommit);
      return new Change(beforeRevision, afterRevision);
    }
  }
}
