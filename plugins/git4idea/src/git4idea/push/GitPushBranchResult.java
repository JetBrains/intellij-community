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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of pushing a single branch.
 * 
 * @author Kirill Likhodedov
 */
final class GitPushBranchResult {

  private final Type myType;
  private final int myNumberOfPushedCommits;
  private final String myTargetBranchName;

  enum Type {
    SUCCESS,
    NEW_BRANCH,
    REJECTED,
    ERROR
  }

  private GitPushBranchResult(Type type, int numberOfPushedCommits, @Nullable String targetBranchName) {
    myType = type;
    myNumberOfPushedCommits = numberOfPushedCommits;
    myTargetBranchName = targetBranchName;
  }
  
  static GitPushBranchResult success(int numberOfPushedCommits) {
    return new GitPushBranchResult(Type.SUCCESS, numberOfPushedCommits, null);
  }
  
  static GitPushBranchResult newBranch(String targetBranchName) {
    return new GitPushBranchResult(Type.NEW_BRANCH, 0, targetBranchName);
  }
  
  static GitPushBranchResult rejected() {
    return new GitPushBranchResult(Type.REJECTED, 0, null);
  }
  
  static GitPushBranchResult error() {
    return new GitPushBranchResult(Type.ERROR, 0, null);
  }
  
  int getNumberOfPushedCommits() {
    return myNumberOfPushedCommits;
  }
  
  boolean isSuccess() {
    return myType == Type.SUCCESS;
  }

  boolean isRejected() {
    return myType == Type.REJECTED;
  }
  
  boolean isError() {
    return myType == Type.ERROR;
  }

  boolean isNewBranch() {
    return myType == Type.NEW_BRANCH;
  }
  
  @NotNull
  String getTargetBranchName() {
    return myTargetBranchName != null ? myTargetBranchName : "";
  }

}
