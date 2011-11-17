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
import git4idea.history.browser.GitCommit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the list of commits and the destination branch (which a branch associated with this GitPushBranchInfo is going to be pushed to).
 *
 * @author Kirill Likhodedov
 */
final class GitPushBranchInfo {

  private final GitBranch mySourceBranch;
  private final GitBranch myDestBranch;
  private final boolean myNewBranch;
  private final List<GitCommit> myCommits;

  GitPushBranchInfo(@NotNull GitBranch sourceBranch, @NotNull GitBranch destBranch, @NotNull List<GitCommit> commits, boolean newBranch) {
    mySourceBranch = sourceBranch;
    myCommits = commits;
    myDestBranch = destBranch;
    myNewBranch = newBranch;
  }

  GitPushBranchInfo(@NotNull GitPushBranchInfo pushBranchInfo) {
    this(pushBranchInfo.getSourceBranch(), pushBranchInfo.getDestBranch(), pushBranchInfo.getCommits(), pushBranchInfo.isNewBranchCreated());
  }

  boolean isNewBranchCreated() {
    return myNewBranch;
  }

  @NotNull
  GitBranch getDestBranch() {
    return myDestBranch;
  }

  @NotNull
  List<GitCommit> getCommits() {
    return new ArrayList<GitCommit>(myCommits);
  }

  @NotNull
  public GitBranch getSourceBranch() {
    return mySourceBranch;
  }
}
