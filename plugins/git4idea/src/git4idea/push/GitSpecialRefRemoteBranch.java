/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import git4idea.GitRemoteBranch;
import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;

/**
 * Semi-fake remote branch if pushing to special push specs like "HEAD:refs/for/master".
 */
class GitSpecialRefRemoteBranch extends GitRemoteBranch {
  private final String myRef;
  private final GitRemote myRemote;

  public GitSpecialRefRemoteBranch(@NotNull String ref, @NotNull GitRemote remote) {
    super(ref);
    myRef = ref;
    myRemote = remote;
  }

  @NotNull
  @Override
  public String getNameForRemoteOperations() {
    return myRef;
  }

  @NotNull
  @Override
  public String getNameForLocalOperations() {
    return myRef;
  }

  @NotNull
  @Override
  public GitRemote getRemote() {
    return myRemote;
  }

  @NotNull
  @Override
  public String getFullName() {
    return myRef;
  }
}
