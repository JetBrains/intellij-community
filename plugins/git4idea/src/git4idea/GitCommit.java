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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a Git commit with its meta information (hash, author, message, etc.), its parents and the {@link Change changes}.
 *
 * @author Kirill Likhodedov
 */
public final class GitCommit extends VcsChangesLazilyParsedDetails {

  public GitCommit(@NotNull Project project, @NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                   @NotNull String subject, @NotNull VcsUser author, @NotNull String message, @NotNull VcsUser committer,
                   long authorTime,
                   @NotNull List<List<VcsFileStatusInfo>> reportedChanges) {
    super(project, hash, parents, commitTime, root, subject, author, message, committer, authorTime, reportedChanges,
          new GitChangesParser());
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
