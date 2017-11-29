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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsStatusDescriptor;
import git4idea.history.GitChangeType;
import git4idea.history.GitChangesParser;
import git4idea.history.GitLogStatusInfo;
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

  public GitCommit(Project project, @NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                   @NotNull String subject, @NotNull VcsUser author, @NotNull String message, @NotNull VcsUser committer,
                   long authorTime, @NotNull List<List<GitLogStatusInfo>> reportedChanges) {
    super(hash, parents, commitTime, root, subject, author, message, committer, authorTime);
    myChanges.set(new UnparsedChanges(project, reportedChanges));
  }

  private class UnparsedChanges extends VcsChangesLazilyParsedDetails.UnparsedChanges<GitLogStatusInfo> {
    private UnparsedChanges(@NotNull Project project,
                            @NotNull List<List<GitLogStatusInfo>> changesOutput) {
      super(project, changesOutput, new GitChangesDescriptor());
    }

    @NotNull
    protected String absolutePath(@NotNull String path) {
      try {
        return getRoot().getPath() + "/" + GitUtil.unescapePath(path);
      }
      catch (VcsException e) {
        return getRoot().getPath() + "/" + path;
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
  }

  private static class GitChangesDescriptor extends VcsStatusDescriptor<GitLogStatusInfo> {
    @NotNull
    @Override
    protected GitLogStatusInfo createStatus(@NotNull Change.Type type, @NotNull String path, @Nullable String secondPath) {
      return new GitLogStatusInfo(getType(type), path, secondPath);
    }

    @NotNull
    private GitChangeType getType(@NotNull Change.Type type) {
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
        case UNRESOLVED:
          LOG.error("Unsupported status info " + info);
      }
      return null;
    }
  }
}
