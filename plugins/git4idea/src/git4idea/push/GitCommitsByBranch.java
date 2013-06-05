/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.push;

import git4idea.GitBranch;
import git4idea.GitCommit;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Holds Git commits made in a single repository grouped by branches.
 *
 * @author Kirill Likhodedov
 */
final class GitCommitsByBranch {

  private final Map<GitBranch, GitPushBranchInfo> myCommitsByBranch;

  GitCommitsByBranch(@NotNull Map<GitBranch, GitPushBranchInfo> commitsByBranch) {
    myCommitsByBranch = new HashMap<GitBranch, GitPushBranchInfo>(commitsByBranch);
  }

  GitCommitsByBranch(GitCommitsByBranch commitsByBranch) {
    this(new HashMap<GitBranch, GitPushBranchInfo>(commitsByBranch.myCommitsByBranch));
  }

  boolean isEmpty() {
    for (GitPushBranchInfo info : myCommitsByBranch.values()) {
      if (!info.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  int commitsNumber() {
    int sum = 0;
    for (GitPushBranchInfo branchInfo : myCommitsByBranch.values()) {
      sum += branchInfo.getCommits().size();
    }
    return sum;
  }

  @NotNull
  Collection<GitBranch> getBranches() {
    return new HashSet<GitBranch>(myCommitsByBranch.keySet());
  }

  @NotNull
  GitPushBranchInfo get(@NotNull GitBranch branch) {
    return new GitPushBranchInfo(myCommitsByBranch.get(branch));
  }

  /**
   * Returns new GitCommitsByBranch that contains commits only from the given branch (or nothing, if the given branch didn't exist in
   * the original structure).
   */
  @NotNull
  GitCommitsByBranch retain(@NotNull GitBranch branch) {
    Map<GitBranch, GitPushBranchInfo> res = new HashMap<GitBranch, GitPushBranchInfo>();
    if (myCommitsByBranch.containsKey(branch)) {
      res.put(branch, myCommitsByBranch.get(branch));
    }
    return new GitCommitsByBranch(res);
  }

  @NotNull
  public Collection<GitCommit> getAllCommits() {
    Collection<GitCommit> commits = new ArrayList<GitCommit>();
    for (GitPushBranchInfo branchInfo : myCommitsByBranch.values()) {
      commits.addAll(branchInfo.getCommits());
    }
    return commits;
  }
}
