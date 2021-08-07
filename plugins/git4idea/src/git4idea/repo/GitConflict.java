// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class GitConflict {
  @NotNull private final VirtualFile myRoot;
  @NotNull private final FilePath myFilePath;
  @NotNull private final Status myOurStatus;
  @NotNull private final Status myTheirStatus;

  public GitConflict(@NotNull VirtualFile root,
                     @NotNull FilePath filePath,
                     @NotNull Status ourStatus,
                     @NotNull Status theirStatus) {
    myRoot = root;
    myFilePath = filePath;
    myOurStatus = ourStatus;
    myTheirStatus = theirStatus;
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }

  @NotNull
  public FilePath getFilePath() {
    return myFilePath;
  }

  @NotNull
  public Status getStatus(@NotNull ConflictSide side) {
    return getStatus(side, false);
  }

  @NotNull
  public Status getStatus(@NotNull ConflictSide side, boolean isReversed) {
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
