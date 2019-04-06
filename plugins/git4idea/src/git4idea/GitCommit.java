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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import git4idea.history.GitCommitRequirements;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.List;

/**
 * Represents a Git commit with its meta information (hash, author, message, etc.), its parents and the {@link Change changes}.
 *
 * @author Kirill Likhodedov
 */
public final class GitCommit extends VcsChangesLazilyParsedDetails {
  @NotNull private final GitCommitRequirements.DiffRenameLimit myRenameLimit;

  public GitCommit(@NotNull Project project, @NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                   @NotNull String subject, @NotNull VcsUser author, @NotNull String message, @NotNull VcsUser committer,
                   long authorTime,
                   @NotNull List<List<VcsFileStatusInfo>> reportedChanges,
                   @NotNull GitCommitRequirements.DiffRenameLimit renameLimit) {
    super(project, hash, parents, commitTime, root, subject, author, message, committer, authorTime, reportedChanges,
          new GitChangesParser());
    myRenameLimit = renameLimit;
  }

  @Override
  public boolean hasRenames() {
    switch (myRenameLimit) {
      case INFINITY:
        return true;
      case GIT_CONFIG:
        return false; // need to know the value from git.config to give correct answer
      case NO_RENAMES:
        return false;
      case REGISTRY:
        Changes changes = getChangesHolder();
        int estimate =
          changes instanceof UnparsedChanges ?
          getRenameLimitEstimate(((UnparsedChanges)changes).getChangesOutput()) :
          getRenameLimitEstimate();
        return estimate <= Registry.intValue("git.diff.renameLimit");
    }
    return true;
  }

  private int getRenameLimitEstimate() {
    int size = 0;
    for (int i = 0; i < getParents().size(); i++) {
      int sources = 0;
      int targets = 0;
      for (Change info : getChanges(i)) {
        Change.Type type = info.getType();
        if (ContainerUtil.set(Change.Type.DELETED, Change.Type.MOVED).contains(type)) {
          sources++;
        }
        if (ContainerUtil.set(Change.Type.NEW).contains(type)) {
          targets++;
        }
      }
      size = Math.max(Math.max(sources, targets), size);
    }
    return size;
  }

  private static int getRenameLimitEstimate(@NotNull List<List<VcsFileStatusInfo>> changesOutput) {
    int size = 0;
    for (List<VcsFileStatusInfo> changesWithParent : changesOutput) {
      int sources = 0;
      int targets = 0;
      for (VcsFileStatusInfo info : changesWithParent) {
        Change.Type type = info.getType();
        if (ContainerUtil.set(Change.Type.DELETED, Change.Type.MOVED).contains(type)) {
          sources++;
        }
        if (ContainerUtil.set(Change.Type.NEW).contains(type)) {
          targets++;
        }
      }
      size = Math.max(Math.max(sources, targets), size);
    }
    return size;
  }

  private static class GitChangesParser implements ChangesParser {
    @Override
    public List<Change> parseStatusInfo(@NotNull Project project,
                                        @NotNull VcsShortCommitDetails commit,
                                        @NotNull List<VcsFileStatusInfo> changes,
                                        int parentIndex) throws VcsException {
      String parentHash = null;
      if (parentIndex < commit.getParents().size()) {
        parentHash = commit.getParents().get(parentIndex).asString();
      }
      return git4idea.history.GitChangesParser.parse(project, commit.getRoot(), changes, commit.getId().asString(),
                                                     new Date(commit.getCommitTime()), parentHash);
    }
  }
}
