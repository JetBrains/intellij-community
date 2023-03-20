// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import git4idea.history.GitCommitRequirements;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a Git commit with its meta information (hash, author, message, etc.), its parents and the {@link Change changes}.
 * <br/>
 * Changes for a commit could be reported differently depending on the use-case: changes could be skipped for root commits,
 * inexact renames detection could be turned off or limited,
 * changes for merge commits could be computed to the parents separately or combined, etc.
 * {@link GitCommit} stores a {@link GitCommitRequirements} object with these settings accessible by {@link GitCommit#getRequirements()} method.
 *
 * @see GitCommitRequirements
 *
 * @author Kirill Likhodedov
 */
public final class GitCommit extends VcsChangesLazilyParsedDetails {
  private final @NotNull GitCommitRequirements myRequirements;

  public GitCommit(@NotNull Project project, @NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                   @NotNull String subject, @NotNull VcsUser author, @NotNull String message, @NotNull VcsUser committer,
                   long authorTime,
                   @NotNull List<List<VcsFileStatusInfo>> reportedChanges,
                   @NotNull GitCommitRequirements requirements) {
    super(project, hash, parents, commitTime, root, subject, author, message, committer, authorTime, reportedChanges,
          new GitChangesParser());
    myRequirements = requirements;
  }

  @ApiStatus.Internal
  @NotNull
  public Set<FilePath> getAffectedPaths() {
    Changes changesObject = getChangesObject();
    if (changesObject instanceof UnparsedChanges) {
      Set<FilePath> result = new HashSet<>();

      for (VcsFileStatusInfo statusInfo : ((UnparsedChanges)changesObject).getMergedStatuses()) {
        result.add(GitContentRevision.createPath(getRoot(), statusInfo.getFirstPath()));

        String secondPath = statusInfo.getSecondPath();
        if (secondPath != null) {
          result.add(GitContentRevision.createPath(getRoot(), secondPath));
        }
      }

      return result;
    }

    Set<FilePath> result = new HashSet<>();
    for (Change change : getChanges()) {
      FilePath beforePath = ChangesUtil.getBeforePath(change);
      if (beforePath != null) result.add(beforePath);
      FilePath afterPath = ChangesUtil.getAfterPath(change);
      if (afterPath != null) result.add(afterPath);
    }
    return result;
  }

  public @NotNull GitCommitRequirements getRequirements() {
    return myRequirements;
  }

  private static class GitChangesParser implements ChangesParser {
    @Override
    public List<Change> parseStatusInfo(@NotNull Project project,
                                        @NotNull VcsShortCommitDetails commit,
                                        @NotNull List<VcsFileStatusInfo> changes,
                                        int parentIndex) {
      String parentHash = null;
      if (parentIndex < commit.getParents().size()) {
        parentHash = commit.getParents().get(parentIndex).asString();
      }
      return git4idea.history.GitChangesParser.parse(project, commit.getRoot(), changes, commit.getId().asString(),
                                                     new Date(commit.getCommitTime()), parentHash);
    }
  }
}
