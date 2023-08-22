// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util;

import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import git4idea.GitCommit;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@Deprecated(forRemoval = true)
public class GitCommitCompareInfo extends CommitCompareInfo {
  public GitCommitCompareInfo() {
  }

  public GitCommitCompareInfo(@NotNull InfoType infoType) {
    super(infoType.getDelegate());
  }

  public void put(@NotNull GitRepository repository, @NotNull Pair<List<GitCommit>, List<GitCommit>> commits) {
    super.put(repository, commits.first, commits.second);
  }

  public void put(@NotNull GitRepository repository, @NotNull Collection<Change> totalDiff) {
    super.putTotalDiff(repository, totalDiff);
  }

  @SuppressWarnings("MethodOverloadsMethodOfSuperclass")
  @NotNull
  public List<GitCommit> getHeadToBranchCommits(@NotNull GitRepository repo) {
    //noinspection unchecked
    return (List)super.getHeadToBranchCommits(repo);
  }

  @SuppressWarnings("MethodOverloadsMethodOfSuperclass")
  @NotNull
  public List<GitCommit> getBranchToHeadCommits(@NotNull GitRepository repo) {
    //noinspection unchecked
    return (List)super.getBranchToHeadCommits(repo);
  }

  public enum InfoType {
    BOTH(CommitCompareInfo.InfoType.BOTH),
    HEAD_TO_BRANCH(CommitCompareInfo.InfoType.HEAD_TO_BRANCH),
    BRANCH_TO_HEAD(CommitCompareInfo.InfoType.BRANCH_TO_HEAD);

    @NotNull private final CommitCompareInfo.InfoType myDelegate;

    InfoType(@NotNull CommitCompareInfo.InfoType delegate) {
      myDelegate = delegate;
    }

    @NotNull
    public CommitCompareInfo.InfoType getDelegate() {
      return myDelegate;
    }
  }
}
