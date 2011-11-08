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

import com.intellij.openapi.util.text.StringUtil;
import git4idea.GitBranch;
import git4idea.commands.GitCommandResult;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static git4idea.ui.GitUIUtil.bold;
import static git4idea.ui.GitUIUtil.code;

/**
 * If an error happens, all push is unsuccessful, for all branches.
 * Otherwise we've got separate results for branches.
 */
class GitPushRepoResult {

  enum BranchResult {
    SUCCESS,
    REJECTED
  }
  
  private enum Type {
    SUCCESS,
    SOME_REJECTED,
    ERROR
  }

  private final Type myType;
  private final GitCommandResult myOutput;
  private final Map<GitBranch, BranchResult> myBranchResults;

  GitPushRepoResult(Type type, Map<GitBranch, BranchResult> resultsByBranch, GitCommandResult output) {
    myType = type;
    myBranchResults = resultsByBranch;
    myOutput = output;
  }

  static GitPushRepoResult success(Map<GitBranch, BranchResult> resultsByBranch, GitCommandResult output) {
    return new GitPushRepoResult(Type.SUCCESS, resultsByBranch, output);
  }

  static GitPushRepoResult error(Map<GitBranch, BranchResult> resultsByBranch, GitCommandResult output) {
    return new GitPushRepoResult(Type.ERROR, resultsByBranch, output);
  }

  static GitPushRepoResult someRejected(Map<GitBranch, BranchResult> resultsByBranch, GitCommandResult output) {
    return new GitPushRepoResult(Type.SOME_REJECTED, resultsByBranch, output);
  }

  boolean isError() {
    return myType == Type.ERROR;
  }
  
  boolean isSuccess() {
    return myType == Type.SUCCESS;
  }

  GitCommandResult getOutput() {
    return myOutput;
  }

  Map<GitBranch, BranchResult> getBranchResults() {
    return myBranchResults;
  }

  GitPushRepoResult remove(@NotNull GitBranch branch) {
    Map<GitBranch, BranchResult> resultsByBranch = new HashMap<GitBranch, BranchResult>();
    for (Map.Entry<GitBranch, BranchResult> entry : myBranchResults.entrySet()) {
      GitBranch b = entry.getKey();
      if (!b.equals(branch)) {
        resultsByBranch.put(b, entry.getValue());
      }
    }
    return new GitPushRepoResult(myType, resultsByBranch, myOutput);
  }

  boolean isEmpty() {
    return myBranchResults.isEmpty();
  }

  /**
   * Merges the given results to this result.
   * In the case of conflict (i.e. different results for a branch), current result is preferred over the previous one.
   */
  void mergeFrom(@NotNull GitPushRepoResult repoResult) {
    for (Map.Entry<GitBranch, BranchResult> entry : repoResult.myBranchResults.entrySet()) {
      GitBranch branch = entry.getKey();
      BranchResult branchResult = entry.getValue();
      if (!myBranchResults.containsKey(branch)) {   // otherwise current result is preferred
        myBranchResults.put(branch, branchResult);
      }
    }
  }

  String getBranchesDescription(GitCommitsByBranch commitsByBranch) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<GitBranch, BranchResult> entry : myBranchResults.entrySet()) {
      GitBranch branch = entry.getKey();
      BranchResult branchResult = entry.getValue();

      if (branchResult == BranchResult.SUCCESS) {
        sb.append(bold(branch.getName()) + ": pushed " + commits(pushedCommitsNum(commitsByBranch, branch))).append("<br/>");
      } else {
        sb.append(code(branch.getName())).append(": rejected").append("<br/>");
      }
    }
    return sb.toString();
  }

  String getPushedCommitsDescription(GitCommitsByBranch commitsByBranch) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<GitBranch, BranchResult> entry : myBranchResults.entrySet()) {
      GitBranch branch = entry.getKey();
      BranchResult branchResult = entry.getValue();

      if (branchResult == BranchResult.SUCCESS) {
        sb.append(branch.getName() + ": pushed " + commits(pushedCommitsNum(commitsByBranch, branch))).append("<br/>");
      }
    }
    return sb.toString();
  }

  private static int pushedCommitsNum(GitCommitsByBranch commitsByBranch, GitBranch branch) {
    return commitsByBranch.get(branch).getCommits().size();
  }

  private static String commits(int commitNum) {
    return commitNum + " " + StringUtil.pluralize("commit", commitNum);
  }


}
