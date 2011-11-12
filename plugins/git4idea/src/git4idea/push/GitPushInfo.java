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

import com.intellij.util.containers.HashMap;
import git4idea.GitBranch;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Holds information about a single Git push action which is to be executed or has been executed.
 *
 * @author Kirill Likhodedov
 */
public final class GitPushInfo {

  @NotNull private final GitCommitsByRepoAndBranch myCommits;
  @NotNull private final Map<GitRepository, GitPushSpec> myPushSpecs;

  /**
   * We pass the complex {@link GitCommitsByRepoAndBranch} structure here instead of just the list of repositories,
   * because later (after successful push, for example) it may be needed for showing useful notifications, such as number of commits pushed.
   */
  public GitPushInfo(@NotNull GitCommitsByRepoAndBranch commits, @NotNull Map<GitRepository, GitPushSpec> pushSpecs) {
    myCommits = commits;
    myPushSpecs = pushSpecs;
  }

  @NotNull
  public Map<GitRepository, GitPushSpec> getPushSpecs() {
    return myPushSpecs;
  }

  @NotNull
  public GitCommitsByRepoAndBranch getCommits() {
    return myCommits;
  }

  @NotNull
  public GitPushInfo retain(Map<GitRepository, GitBranch> repoBranchMap) {
    return new GitPushInfo(myCommits.retainAll(repoBranchMap), new HashMap<GitRepository, GitPushSpec>(myPushSpecs));
  }
}
