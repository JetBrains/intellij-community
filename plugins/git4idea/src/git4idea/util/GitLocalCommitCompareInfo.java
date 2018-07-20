// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.hash.HashMap;
import git4idea.branch.GitBranchWorker;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

public class GitLocalCommitCompareInfo extends GitCommitCompareInfo {
  @NotNull private final String myBranchName;

  public GitLocalCommitCompareInfo(@NotNull String branchName) {
    myBranchName = branchName;
  }

  public void reloadTotalDiff() throws VcsException {
    Map<GitRepository, Collection<Change>> newDiff = new HashMap<>();
    for (GitRepository repository: getRepositories()) {
      newDiff.put(repository, GitBranchWorker.loadTotalDiff(repository, myBranchName));
    }

    updateTotalDiff(newDiff);
  }
}
