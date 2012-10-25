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
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

  /**
   * If there is a common remote branch which is commonly tracked by the given branch in all repositories,
   * returns the name of this remote branch. Otherwise returns null. <br/>
   * For one repository just returns the tracked branch or null if there is no tracked branch.
   */
  @Nullable
  public String getTrackedBranch(@NotNull String branch) {
    String trackedName = null;
    for (GitRepository repository : myRepositories) {
      GitRemoteBranch tracked = getTrackedBranch(repository, branch);
      if (tracked == null) {
        return null;
      }
      if (trackedName == null) {
        trackedName = tracked.getNameForLocalOperations();
      }
      else if (!trackedName.equals(tracked.getNameForLocalOperations())) {
        return null;
      }
    }
    return trackedName;
  }

  /**
   * Returns local branches which track the given remote branch. Usually there is 0 or 1 such branches.
   */
  @NotNull
  public Collection<String> getTrackingBranches(@NotNull String remoteBranch) {
    Collection<String> trackingBranches = null;
    for (GitRepository repository : myRepositories) {
      Collection<String> tb = getTrackingBranches(repository, remoteBranch);
      if (trackingBranches == null) {
        trackingBranches = tb;
      }
      else {
        trackingBranches.retainAll(tb);
      }
    }
    return trackingBranches == null ? Collections.<String>emptyList() : trackingBranches;
  }

  @NotNull
  public static Collection<String> getTrackingBranches(@NotNull GitRepository repository, @NotNull String remoteBranch) {
    Collection<String> trackingBranches = new ArrayList<String>(1);
    for (GitBranchTrackInfo trackInfo : repository.getBranchTrackInfos()) {
      if (remoteBranch.equals(trackInfo.getRemote().getName() + "/" + trackInfo.getRemoteBranch())) {
        trackingBranches.add(trackInfo.getLocalBranch().getName());
      }
    }
    return trackingBranches;
  }

  @Nullable
  private static GitRemoteBranch getTrackedBranch(@NotNull GitRepository repository, @NotNull String branchName) {
    GitLocalBranch branch = GitBranchUtil.findLocalBranchByName(repository, branchName);
    return branch == null ? null : branch.findTrackedBranch(repository);
  }

  @NotNull
  private Collection<String> getCommonBranches(boolean local) {
    Collection<String> commonBranches = null;
    for (GitRepository repository : myRepositories) {
      GitBranchesCollection branchesCollection = repository.getBranches();

      Collection<String> names = local
                                 ? GitBranchUtil.convertBranchesToNames(branchesCollection.getLocalBranches())
                                 : GitBranchUtil.getBranchNamesWithoutRemoteHead(branchesCollection.getRemoteBranches());
      if (commonBranches == null) {
        commonBranches = names;
      }
      else {
        commonBranches.retainAll(names);
      }
    }

    if (commonBranches != null) {
      ArrayList<String> common = new ArrayList<String>(commonBranches);
      Collections.sort(common);
      return common;
    }
    else {
      return Collections.emptyList();
    }
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
