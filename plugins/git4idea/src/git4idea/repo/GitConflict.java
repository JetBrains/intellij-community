// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class GitConflict {
  private final @NotNull VirtualFile myRoot;
  private final @NotNull FilePath myFilePath;
  private final @NotNull Status myOurStatus;
  private final @NotNull Status myTheirStatus;

  public GitConflict(@NotNull VirtualFile root,
                     @NotNull FilePath filePath,
                     @NotNull Status ourStatus,
                     @NotNull Status theirStatus) {
    myRoot = root;
    myFilePath = filePath;
    myOurStatus = ourStatus;
    myTheirStatus = theirStatus;
  }

  public @NotNull VirtualFile getRoot() {
    return myRoot;
  }

  public @NotNull FilePath getFilePath() {
    return myFilePath;
  }

  public @NotNull Status getStatus(@NotNull ConflictSide side) {
    return getStatus(side, false);
  }

  public @NotNull Status getStatus(@NotNull ConflictSide side, boolean isReversed) {
    if (side == ConflictSide.OURS) {
      return !isReversed ? myOurStatus : myTheirStatus;
    }
    else {
      return !isReversed ? myTheirStatus : myOurStatus;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GitConflict conflict = (GitConflict)o;
    return myRoot.equals(conflict.myRoot) &&
           myFilePath.equals(conflict.myFilePath) &&
           myOurStatus == conflict.myOurStatus &&
           myTheirStatus == conflict.myTheirStatus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myRoot, myFilePath, myOurStatus, myTheirStatus);
  }

  public enum Status {
    MODIFIED,
    DELETED,
    ADDED
  }

  /**
   * Conflict side in Git terms (--ours or --theirs), ignoring IDE-level visible sides reversal.
   *
   * @see git4idea.merge.GitMergeUtil#isReverseRoot(GitRepository)
   */
  public enum ConflictSide {
    OURS, THEIRS
  }
}
