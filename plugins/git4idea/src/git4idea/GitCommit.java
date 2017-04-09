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
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import git4idea.history.GitChangeType;
import git4idea.history.GitChangesParser;
import git4idea.history.GitLogStatusInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.notNull;

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
    super(hash, parents, commitTime, root, subject, author, message, committer, authorTime,
          new MyChangesComputable(new Data(project, root, reportedChanges, hash, commitTime, parents)));
  }

  @NotNull
  @Override
  public Collection<String> getModifiedPaths() {
    Data data = ((MyChangesComputable)myChangesGetter).getData();
    if (data != null) {
      Set<String> changes = ContainerUtil.newHashSet();
      for (GitLogStatusInfo status : ((MyChangesComputable)myChangesGetter).getMergedChanges()) {
        if (status.getSecondPath() == null) {
          changes.add(absolutePath(status.getFirstPath()));
        }
      }
      return changes;
    }
    return super.getModifiedPaths();
  }

  @NotNull
  @Override
  public Collection<Couple<String>> getRenamedPaths() {
    Data data = ((MyChangesComputable)myChangesGetter).getData();
    if (data != null) {
      Set<Couple<String>> changes = ContainerUtil.newHashSet();
      for (GitLogStatusInfo status : ((MyChangesComputable)myChangesGetter).getMergedChanges()) {
        if (status.getSecondPath() != null) {
          changes.add(Couple.of(absolutePath(status.getFirstPath()), absolutePath(status.getSecondPath())));
        }
      }
      return changes;
    }
    return super.getRenamedPaths();
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

  private static class MyChangesComputable implements ThrowableComputable<Collection<Change>, VcsException> {
    private Data myData;
    private Collection<Change> myChanges;

    public MyChangesComputable(Data data) {
      myData = data;
    }

    @Override
    public Collection<Change> compute() throws VcsException {
      if (myChanges == null) {
        assert myData != null;
        myChanges = GitChangesParser.parse(myData.project, myData.root, getMergedChanges(), myData.hash.asString(),
                                           new Date(myData.time), ContainerUtil.map(myData.parents, Hash::asString));
        myData = null; // don't hold the not-yet-parsed string
      }
      return myChanges;
    }

    /*
     * This method mimics result of `-c` option added to `git log` command.
     * It calculates statuses for files that were modified in all parents of a merge commit.
     * If a commit is not a merge, all statuses are returned.
     */
    @NotNull
    private List<GitLogStatusInfo> getMergedChanges() {
      assert myData != null;

      List<GitLogStatusInfo> firstParent = myData.changesOutput.get(0);
      if (myData.changesOutput.size() == 1) return firstParent;

      List<Map<String, GitLogStatusInfo>> affectedMap =
        ContainerUtil.map(myData.changesOutput, infos -> {
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
    private static GitLogStatusInfo getMergedStatusInfo(@NotNull String path, @NotNull List<GitLogStatusInfo> statuses) {
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
    private static String getPath(@NotNull GitLogStatusInfo info) {
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

    public Data getData() {
      return myData;
    }
  }

  private static class Data {
    @NotNull private final Project project;
    @NotNull private final VirtualFile root;
    @NotNull private final List<List<GitLogStatusInfo>> changesOutput;
    @NotNull private final Hash hash;
    private final long time;
    @NotNull private final List<Hash> parents;

    public Data(@NotNull Project project,
                @NotNull VirtualFile root,
                @NotNull List<List<GitLogStatusInfo>> changesOutput,
                @NotNull Hash hash,
                long time,
                @NotNull List<Hash> parents) {
      this.project = project;
      this.root = root;
      this.changesOutput = changesOutput;
      this.hash = hash;
      this.time = time;
      this.parents = parents;
    }
  }
}
