/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.ui.branch;

import git4idea.GitBranch;
import git4idea.util.GitUtil;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Kirill Likhodedov
 */
public class GitMultiRootBranchConfig {
  
  private final Collection<GitRepository> myRepositories;

  public GitMultiRootBranchConfig(@NotNull Collection<GitRepository> repositories) {
    myRepositories = repositories;
  }

  boolean diverged() {
    return getCurrentBranch() == null;
  }
  
  @Nullable
  public String getCurrentBranch() {
    String commonBranch = null;
    for (GitRepository repository : myRepositories) {
      GitBranch branch = repository.getCurrentBranch();
      if (branch == null) {
        return null;
      }
      // NB: if all repositories are in the rebasing state on the same branches, this branch is returned
      if (commonBranch == null) {
        commonBranch = branch.getName();
      } else if (!commonBranch.equals(branch.getName())) {
        return null;
      }
    }
    return commonBranch;
  }
  
  @Nullable
  GitRepository.State getState() {
    GitRepository.State commonState = null;
    for (GitRepository repository : myRepositories) {
      GitRepository.State state = repository.getState();
      if (commonState == null) {
        commonState = state;
      } else if (!commonState.equals(state)) {
        return null;
      }
    }
    return commonState;
  }
  
  @NotNull
  Collection<String> getLocalBranches() {
    return getCommonBranches(true);
  }  

  @NotNull
  Collection<String> getRemoteBranches() {
    return getCommonBranches(false);
  }  

  @NotNull
  private Collection<String> getCommonBranches(boolean local) {
    Collection<String> commonLocal = null;
    for (GitRepository repository : myRepositories) {
      GitBranchesCollection branchesCollection = repository.getBranches();
      Collection<GitBranch> branches = local ? branchesCollection.getLocalBranches() : branchesCollection.getRemoteBranches();
      if (commonLocal == null) {
        commonLocal = GitUtil.getBranchNames(branches);
      } else {
        commonLocal.retainAll(GitUtil.getBranchNames(branches));
      }
    }
    return commonLocal != null ? commonLocal : Collections.<String>emptyList();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (GitRepository repository : myRepositories) {
      sb.append(repository.getPresentableUrl()).append(":").append(repository.getCurrentBranch()).append(":").append(repository.getState());
    }
    return sb.toString();
  }
}
