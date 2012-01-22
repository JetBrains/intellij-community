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
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static git4idea.util.GitUIUtil.bold;
import static git4idea.util.GitUIUtil.code;

/**
 * If an error happens, all push is unsuccessful, for all branches.
 * Otherwise we've got separate results for branches.
 */
final class GitPushRepoResult {

  enum Type {
    NOT_PUSHING,
    SUCCESS,
    SOME_REJECTED,
    ERROR,
    CANCEL,
    NOT_AUTHORIZED
  }

  private final Type myType;
  private final String myOutput;
  private final Map<GitBranch, GitPushBranchResult> myBranchResults;

  GitPushRepoResult(@NotNull Type type, @NotNull Map<GitBranch, GitPushBranchResult> resultsByBranch, @NotNull String output) {
    myType = type;
    myBranchResults = resultsByBranch;
    myOutput = output;
  }

  @NotNull
  static GitPushRepoResult success(@NotNull Map<GitBranch, GitPushBranchResult> resultsByBranch, @NotNull String output) {
    return new GitPushRepoResult(Type.SUCCESS, resultsByBranch, output);
  }

  @NotNull
  static GitPushRepoResult error(@NotNull Map<GitBranch, GitPushBranchResult> resultsByBranch, @NotNull String output) {
    return new GitPushRepoResult(Type.ERROR, resultsByBranch, output);
  }

  @NotNull
  static GitPushRepoResult someRejected(@NotNull Map<GitBranch, GitPushBranchResult> resultsByBranch, @NotNull String output) {
    return new GitPushRepoResult(Type.SOME_REJECTED, resultsByBranch, output);
  }

  @NotNull
  public static GitPushRepoResult cancelled(@NotNull String output) {
    return new GitPushRepoResult(Type.CANCEL, Collections.<GitBranch, GitPushBranchResult>emptyMap(), output);
  }

  @NotNull
  public static GitPushRepoResult notAuthorized(@NotNull String output) {
    return new GitPushRepoResult(Type.NOT_AUTHORIZED, Collections.<GitBranch, GitPushBranchResult>emptyMap(), output);
  }

  @NotNull
  static GitPushRepoResult notPushed() {
    return new GitPushRepoResult(Type.NOT_PUSHING, Collections.<GitBranch, GitPushBranchResult>emptyMap(), "");
  }

  @NotNull
  Type getType() {
    return myType;
  }
  
  boolean isOneOfErrors() {
    return myType == Type.ERROR || myType == Type.CANCEL || myType == Type.NOT_AUTHORIZED;
  }

  @NotNull
  String getOutput() {
    return myOutput;
  }

  @NotNull
  Map<GitBranch, GitPushBranchResult> getBranchResults() {
    return myBranchResults;
  }

  @NotNull
  GitPushRepoResult remove(@NotNull GitBranch branch) {
    Map<GitBranch, GitPushBranchResult> resultsByBranch = new HashMap<GitBranch, GitPushBranchResult>();
    for (Map.Entry<GitBranch, GitPushBranchResult> entry : myBranchResults.entrySet()) {
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
    for (Map.Entry<GitBranch, GitPushBranchResult> entry : repoResult.myBranchResults.entrySet()) {
      GitBranch branch = entry.getKey();
      GitPushBranchResult branchResult = entry.getValue();
      if (!myBranchResults.containsKey(branch)) {   // otherwise current result is preferred
        myBranchResults.put(branch, branchResult);
      }
    }
  }

  @NotNull
  String getPerBranchesNonErrorReport() {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    for (Map.Entry<GitBranch, GitPushBranchResult> entry : myBranchResults.entrySet()) {
      GitBranch branch = entry.getKey();
      GitPushBranchResult branchResult = entry.getValue();

      if (branchResult.isSuccess()) {
        sb.append(bold(branch.getName()) + ": pushed " + commits(branchResult.getNumberOfPushedCommits()));
      } 
      else if (branchResult.isNewBranch()) {
        sb.append(bold(branch.getName()) + " pushed to new branch " + bold(branchResult.getTargetBranchName()));
      } 
      else {
        sb.append(code(branch.getName())).append(": rejected");
      }
      
      if (i < myBranchResults.size() - 1) {
        sb.append("<br/>");
      }
    }
    return sb.toString();
  }

  @NotNull
  private static String commits(int commitNum) {
    return commitNum + " " + StringUtil.pluralize("commit", commitNum);
  }


}
