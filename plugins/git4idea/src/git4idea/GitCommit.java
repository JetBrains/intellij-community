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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl;
import com.intellij.vcs.log.impl.VcsIndexableDetails;
import git4idea.history.GitChangeType;
import git4idea.history.GitChangesParser;
import git4idea.history.GitLogStatusInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * Represents a Git commit with its meta information (hash, author, message, etc.), its parents and the {@link Change changes}.
 *
 * @author Kirill Likhodedov
 */
public final class GitCommit extends VcsCommitMetadataImpl implements VcsFullCommitDetails, VcsIndexableDetails {
  private static final Logger LOG = Logger.getInstance(GitCommit.class);
  @NotNull private final AtomicReference<Changes> myChanges = new AtomicReference<>();

  public GitCommit(Project project, @NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                   @NotNull String subject, @NotNull VcsUser author, @NotNull String message, @NotNull VcsUser committer,
                   long authorTime, @NotNull List<List<GitLogStatusInfo>> reportedChanges) {
    super(hash, parents, commitTime, root, subject, author, message, committer, authorTime);
    myChanges.set(new UnparsedChanges(project, reportedChanges));
  }

  @NotNull
  @Override
  public Collection<String> getModifiedPaths(int parent) {
    return myChanges.get().getModifiedPaths(parent);
  }

  @NotNull
  @Override
  public Collection<Couple<String>> getRenamedPaths(int parent) {
    return myChanges.get().getRenamedPaths(parent);
  }

  @NotNull
  @Override
  public Collection<Change> getChanges() {
    try {
      return myChanges.get().getMergedChanges();
    }
    catch (VcsException e) {
      LOG.error("Error happened when parsing changes", e);
      return Collections.emptyList();
    }
  }

  @NotNull
  @Override
  public Collection<Change> getChanges(int parent) {
    try {
      return myChanges.get().getChanges(parent);
    }
    catch (VcsException e) {
      LOG.error("Error happened when parsing changes", e);
      return Collections.emptyList();
    }
  }

  @NotNull
  private String absolutePath(@NotNull String path) {
    try {
      return getRoot().getPath() + "/" + GitUtil.unescapePath(path);
    }
    catch (VcsException e) {
      return getRoot().getPath() + "/" + path;
    }
  }

  private interface Changes {
    @NotNull
    Collection<Change> getMergedChanges() throws VcsException;

    @NotNull
    Collection<Change> getChanges(int parent) throws VcsException;

    @NotNull
    Collection<String> getModifiedPaths(int parent);

    @NotNull
    Collection<Couple<String>> getRenamedPaths(int parent);
  }

  private static class ParsedChanges implements Changes {
    @NotNull private final Collection<Change> myMergedChanges;
    @NotNull private final List<Collection<Change>> myChanges;

    private ParsedChanges(@NotNull Collection<Change> mergedChanges,
                          @NotNull List<Collection<Change>> changes) {
      myMergedChanges = mergedChanges;
      myChanges = changes;
    }

    @NotNull
    @Override
    public Collection<Change> getMergedChanges() {
      return myMergedChanges;
    }

    @NotNull
    @Override
    public Collection<Change> getChanges(int parent) {
      return myChanges.get(parent);
    }

    @NotNull
    @Override
    public Collection<String> getModifiedPaths(int parent) {
      Set<String> changes = ContainerUtil.newHashSet();

      for (Change change : getChanges(parent)) {
        if (!change.getType().equals(Change.Type.MOVED)) {
          if (change.getAfterRevision() != null) changes.add(change.getAfterRevision().getFile().getPath());
          if (change.getBeforeRevision() != null) changes.add(change.getBeforeRevision().getFile().getPath());
        }
      }

      return changes;
    }

    @NotNull
    @Override
    public Collection<Couple<String>> getRenamedPaths(int parent) {
      Set<Couple<String>> renames = ContainerUtil.newHashSet();
      for (Change change : getChanges(parent)) {
        if (change.getType().equals(Change.Type.MOVED)) {
          if (change.getAfterRevision() != null && change.getBeforeRevision() != null) {
            renames.add(Couple.of(change.getBeforeRevision().getFile().getPath(), change.getAfterRevision().getFile().getPath()));
          }
        }
      }
      return renames;
    }
  }

  private class UnparsedChanges implements Changes {
    @NotNull private final Project myProject;
    @NotNull private final List<List<GitLogStatusInfo>> myChangesOutput;

    private UnparsedChanges(@NotNull Project project,
                            @NotNull List<List<GitLogStatusInfo>> changesOutput) {
      myProject = project;
      myChangesOutput = changesOutput;
    }

    @NotNull
    private ParsedChanges parseChanges() throws VcsException {
      List<Change> mergedChanges = parseStatusInfo(getMergedStatusInfo(), 0);
      List<Collection<Change>> changes = computeChanges(mergedChanges);
      ParsedChanges parsedChanges = new ParsedChanges(mergedChanges, changes);
      myChanges.compareAndSet(this, parsedChanges);
      return parsedChanges;
    }

    @NotNull
    @Override
    public Collection<Change> getMergedChanges() throws VcsException {
      return parseChanges().getMergedChanges();
    }

    @NotNull
    @Override
    public Collection<Change> getChanges(int parent) throws VcsException {
      return parseChanges().getChanges(parent);
    }

    @NotNull
    @Override
    public Collection<String> getModifiedPaths(int parent) {
      Set<String> changes = ContainerUtil.newHashSet();
      for (GitLogStatusInfo status : myChangesOutput.get(parent)) {
        if (status.getSecondPath() == null) {
          changes.add(absolutePath(status.getFirstPath()));
        }
      }
      return changes;
    }

    @NotNull
    @Override
    public Collection<Couple<String>> getRenamedPaths(int parent) {
      Set<Couple<String>> renames = ContainerUtil.newHashSet();
      for (GitLogStatusInfo status : myChangesOutput.get(parent)) {
        if (status.getSecondPath() != null) {
          renames.add(Couple.of(absolutePath(status.getFirstPath()), absolutePath(status.getSecondPath())));
        }
      }
      return renames;
    }

    @NotNull
    private List<Collection<Change>> computeChanges(@NotNull Collection<Change> mergedChanges)
      throws VcsException {
      if (myChangesOutput.size() == 1) {
        return Collections.singletonList(mergedChanges);
      }
      else {
        List<Collection<Change>> changes = ContainerUtil.newArrayListWithCapacity(myChangesOutput.size());
        for (int i = 0; i < myChangesOutput.size(); i++) {
          changes.add(parseStatusInfo(myChangesOutput.get(i), i));
        }
        return changes;
      }
    }

    @NotNull
    private List<Change> parseStatusInfo(@NotNull List<GitLogStatusInfo> changes, int parentIndex) throws VcsException {
      String parentHash = null;
      if (parentIndex < getParents().size()) {
        parentHash = getParents().get(parentIndex).asString();
      }
      return GitChangesParser.parse(myProject, getRoot(), changes, getId().asString(), new Date(getCommitTime()), parentHash);
    }

    /*
     * This method mimics result of `-c` option added to `git log` command.
     * It calculates statuses for files that were modified in all parents of a merge commit.
     * If a commit is not a merge, all statuses are returned.
     */
    @NotNull
    private List<GitLogStatusInfo> getMergedStatusInfo() {
      List<GitLogStatusInfo> firstParent = myChangesOutput.get(0);
      if (myChangesOutput.size() == 1) return firstParent;

      List<Map<String, GitLogStatusInfo>> affectedMap =
        ContainerUtil.map(myChangesOutput, infos -> {
          LinkedHashMap<String, GitLogStatusInfo> map = ContainerUtil.newLinkedHashMap();

          for (GitLogStatusInfo info : infos) {
            String path = getPath(info);
            if (path != null) map.put(path, info);
          }

          return map;
        });

      List<GitLogStatusInfo> result = ContainerUtil.newArrayList();

      outer:
      for (String path : affectedMap.get(0).keySet()) {

        List<GitLogStatusInfo> statuses = ContainerUtil.newArrayList();
        for (Map<String, GitLogStatusInfo> infoMap : affectedMap) {
          GitLogStatusInfo status = infoMap.get(path);
          if (status == null) continue outer;
          statuses.add(status);
        }

        result.add(getMergedStatusInfo(path, statuses));
      }

      return result;
    }

    @NotNull
    private GitLogStatusInfo getMergedStatusInfo(@NotNull String path, @NotNull List<GitLogStatusInfo> statuses) {
      Set<GitChangeType> types = ContainerUtil.map2Set(statuses, GitLogStatusInfo::getType);

      if (types.size() == 1) {
        GitChangeType type = notNull(ContainerUtil.getFirstItem(types));
        if (type.equals(GitChangeType.COPIED) || type.equals(GitChangeType.RENAMED)) {
          String renamedFrom = null;
          for (GitLogStatusInfo status : statuses) {
            if (renamedFrom == null) {
              renamedFrom = status.getFirstPath();
            }
            else if (!renamedFrom.equals(status.getFirstPath())) {
              return new GitLogStatusInfo(GitChangeType.MODIFIED, path, null);
            }
          }
        }
        return statuses.get(0);
      }

      if (types.contains(GitChangeType.DELETED)) return new GitLogStatusInfo(GitChangeType.DELETED, path, null);
      return new GitLogStatusInfo(GitChangeType.MODIFIED, path, null);
    }

    @Nullable
    private String getPath(@NotNull GitLogStatusInfo info) {
      switch (info.getType()) {
        case MODIFIED:
        case ADDED:
        case TYPE_CHANGED:
        case DELETED:
          return info.getFirstPath();
        case COPIED:
        case RENAMED:
          return info.getSecondPath();
        case UNRESOLVED:
          LOG.error("Unsupported status info " + info);
      }
      return null;
    }
  }
}
