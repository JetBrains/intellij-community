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

/**
 * Result of pushing a single branch.
 * 
 * @author Kirill Likhodedov
 */
final class GitPushBranchResult {

  private final Type myType;
  private final int myNumberOfPushedCommits;

  enum Type {
    SUCCESS,
    REJECTED,
    ERROR
  }

  private GitPushBranchResult(Type type, int numberOfPushedCommits) {
    myType = type;
    myNumberOfPushedCommits = numberOfPushedCommits;
  }
  
  static GitPushBranchResult success(int numberOfPushedCommits) {
    return new GitPushBranchResult(Type.SUCCESS, numberOfPushedCommits);
  }
  
  static GitPushBranchResult rejected() {
    return new GitPushBranchResult(Type.REJECTED, 0);
  }
  
  static GitPushBranchResult error() {
    return new GitPushBranchResult(Type.ERROR, 0);
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

}
