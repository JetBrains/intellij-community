/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.repo;

import com.intellij.dvcs.repo.Repository;
import com.intellij.vcs.log.Hash;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

class GitBranchState {
  @Nullable private final String currentRevision;
  @Nullable private final GitLocalBranch currentBranch;
  @NotNull private final Repository.State state;
  @NotNull private final Map<GitLocalBranch, Hash> localBranches;
  @NotNull private final Map<GitRemoteBranch, Hash> remoteBranches;

  GitBranchState(@Nullable String currentRevision,
                 @Nullable GitLocalBranch currentBranch,
                 @NotNull Repository.State state,
                 @NotNull Map<GitLocalBranch, Hash> localBranches,
                 @NotNull Map<GitRemoteBranch, Hash> remoteBranches) {
    this.currentRevision = currentRevision;
    this.currentBranch = currentBranch;
    this.state = state;
    this.localBranches = localBranches;
    this.remoteBranches = remoteBranches;
  }

  @Nullable
  public String getCurrentRevision() {
    return currentRevision;
  }

  @Nullable
  public GitLocalBranch getCurrentBranch() {
    return currentBranch;
  }

  @NotNull
  public Repository.State getState() {
    return state;
  }

  @NotNull
  public Map<GitLocalBranch, Hash> getLocalBranches() {
    return localBranches;
  }

  @NotNull
  public Map<GitRemoteBranch, Hash> getRemoteBranches() {
    return remoteBranches;
  }
}
