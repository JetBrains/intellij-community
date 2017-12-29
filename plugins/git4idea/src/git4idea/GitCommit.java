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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsStatusDescriptor;
import git4idea.history.GitChangeType;
import git4idea.history.GitChangesParser;
import git4idea.history.GitLogStatusInfo;
import git4idea.history.GitLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * Represents a Git commit with its meta information (hash, author, message, etc.), its parents and the {@link Change changes}.
 *
 * @author Kirill Likhodedov
 */
public final class GitCommit extends VcsChangesLazilyParsedDetails {
  private static final Logger LOG = Logger.getInstance(GitCommit.class);
  @NotNull private final GitLogUtil.DiffRenameLimit myRenameLimit;

  public GitCommit(Project project, @NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                   @NotNull String subject, @NotNull VcsUser author, @NotNull String message, @NotNull VcsUser committer,
                   long authorTime, @NotNull List<List<GitLogStatusInfo>> reportedChanges, @NotNull GitLogUtil.DiffRenameLimit renameLimit) {
    super(hash, parents, commitTime, root, subject, author, message, committer, authorTime);
    myRenameLimit = renameLimit;
    myChanges.set(reportedChanges.isEmpty() ? EMPTY_CHANGES : new UnparsedChanges(project, reportedChanges));
  }

  @Override
  public boolean hasRenames() {
    switch (myRenameLimit) {
      case INFINITY:
        return true;
      case GIT_CONFIG:
        return false; // need to know the value from git.config to give correct answer
      case REGISTRY:
        Changes changes = myChanges.get();
        int estimate =
          changes instanceof UnparsedChanges ? ((UnparsedChanges)changes).getRenameLimitEstimate() : getRenameLimitEstimate();
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

  private class UnparsedChanges extends VcsChangesLazilyParsedDetails.UnparsedChanges<GitLogStatusInfo> {
    @NotNull private final String myRootPath = getRoot().getPath();

    private UnparsedChanges(@NotNull Project project,
                            @NotNull List<List<GitLogStatusInfo>> changesOutput) {
      super(project, changesOutput, new GitChangesDescriptor());
    }

    @Override
    @NotNull
    protected String absolutePath(@NotNull String path) {
      try {
        return myRootPath + "/" + GitUtil.unescapePath(path);
      }
      catch (VcsException e) {
        return myRootPath + "/" + path;
      }
    }

    @NotNull
    @Override
    protected List<Change> parseStatusInfo(@NotNull List<GitLogStatusInfo> changes, int parentIndex) throws VcsException {
      String parentHash = null;
      if (parentIndex < getParents().size()) {
        parentHash = getParents().get(parentIndex).asString();
      }
      return GitChangesParser.parse(myProject, getRoot(), changes, getId().asString(), new Date(getCommitTime()), parentHash);
    }

    int getRenameLimitEstimate() {
      int size = 0;
      for (List<GitLogStatusInfo> changesWithParent : myChangesOutput) {
        int sources = 0;
        int targets = 0;
        for (GitLogStatusInfo info : changesWithParent) {
          GitChangeType type = info.getType();
          if (ContainerUtil.set(GitChangeType.DELETED, GitChangeType.RENAMED).contains(type)) {
            sources++;
          }
          if (ContainerUtil.set(GitChangeType.ADDED).contains(type)) {
            targets++;
          }
        }
        size = Math.max(Math.max(sources, targets), size);
      }
      return size;
    }
  }

  private static class GitChangesDescriptor extends VcsStatusDescriptor<GitLogStatusInfo> {
    @NotNull
    @Override
    protected GitLogStatusInfo createStatus(@NotNull Change.Type type, @NotNull String path, @Nullable String secondPath) {
      return new GitLogStatusInfo(getType(type), path, secondPath);
    }

    @NotNull
    private static GitChangeType getType(@NotNull Change.Type type) {
      switch (type) {
        case MODIFICATION:
          return GitChangeType.MODIFIED;
        case NEW:
          return GitChangeType.ADDED;
        case DELETED:
          return GitChangeType.DELETED;
        case MOVED:
          return GitChangeType.RENAMED;
      }
      return null;
    }

    @NotNull
    @Override
    public String getFirstPath(@NotNull GitLogStatusInfo info) {
      return info.getFirstPath();
    }

    @Nullable
    @Override
    public String getSecondPath(@NotNull GitLogStatusInfo info) {
      return info.getSecondPath();
    }

    @NotNull
    @Override
    public Change.Type getType(@NotNull GitLogStatusInfo info) {
      switch (info.getType()) {
        case ADDED:
          return Change.Type.NEW;
        case MODIFIED:
        case TYPE_CHANGED:
          return Change.Type.MODIFICATION;
        case DELETED:
          return Change.Type.DELETED;
        case COPIED:
        case RENAMED:
          return Change.Type.MOVED;
        default:
        case UNRESOLVED:
          LOG.error("Unsupported status info " + info);
          throw new RuntimeException(info.toString());
      }
    }
  }
}
