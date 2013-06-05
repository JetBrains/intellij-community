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

import git4idea.GitCommit;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the list of commits and the destination branch (which a branch associated with this GitPushBranchInfo is going to be pushed to).
 *
 * @author Kirill Likhodedov
 */
final class GitPushBranchInfo {

  private final GitLocalBranch mySourceBranch;
  private final GitRemoteBranch myDestBranch;
  private final Type myType;
  private final List<GitCommit> myCommits;

  enum Type {
    STANDARD,             // the branch this branch is targeted, exists (and is either the tracked/matched branch or manually specified)
    NEW_BRANCH,           // the source branch will be pushed to a new branch
    NO_TRACKED_OR_TARGET  // the branch has no tracked/matched, and target was not manually specified
  }

  GitPushBranchInfo(@NotNull GitLocalBranch sourceBranch, @NotNull GitRemoteBranch destBranch,
                    @NotNull List<GitCommit> commits, @NotNull Type type) {
    mySourceBranch = sourceBranch;
    myCommits = commits;
    myDestBranch = destBranch;
    myType = type;
  }

  GitPushBranchInfo(@NotNull GitPushBranchInfo pushBranchInfo) {
    this(pushBranchInfo.getSourceBranch(), pushBranchInfo.getDestBranch(), pushBranchInfo.getCommits(), pushBranchInfo.getType());
  }

  @NotNull
  Type getType() {
    return myType;
  }
  
  boolean isNewBranchCreated() {
    return myType == Type.NEW_BRANCH;
  }

  @NotNull
  GitRemoteBranch getDestBranch() {
    return myDestBranch;
  }

  @NotNull
  List<GitCommit> getCommits() {
    return new ArrayList<GitCommit>(myCommits);
  }

  @NotNull
  public GitLocalBranch getSourceBranch() {
    return mySourceBranch;
  }

  boolean isEmpty() {
    return myCommits.isEmpty();
  }

}
