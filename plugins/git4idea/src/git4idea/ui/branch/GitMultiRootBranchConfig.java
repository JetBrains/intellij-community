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

import com.intellij.dvcs.branch.DvcsMultiRootBranchConfig;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchUtil;
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
public class GitMultiRootBranchConfig extends DvcsMultiRootBranchConfig<GitRepository> {

  public GitMultiRootBranchConfig(@NotNull Collection<? extends GitRepository> repositories) {
    super(repositories);
  }

  @Override
  @NotNull
  public Collection<String> getLocalBranchNames() {
    return GitBranchUtil.getCommonBranches(myRepositories, true);
  }

  @NotNull
  Collection<String> getRemoteBranches() {
    return GitBranchUtil.getCommonBranches(myRepositories, false);
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
   * <p>Returns common local branches which track the given common remote branch.</p>
   * <p>Usually, in "synchronous case" there is 0 (the remote branch is not being tracked) or 1 (it is tracked) such branches.</p>
   * <p>If the remote branch is being tracked not in all repositories, or if its local tracking branches have different names
   * in different repositories, it means that there is no common tracking branches, so an empty list is returned.</p>
   */
  @NotNull
  public Collection<String> getCommonTrackingBranches(@NotNull String remoteBranch) {
    Collection<String> trackingBranches = null;
    for (GitRepository repository : myRepositories) {
      Collection<String> tb = getTrackingBranches(repository, remoteBranch);
      if (trackingBranches == null) {
        trackingBranches = tb;
      }
      else {
        trackingBranches = ContainerUtil.intersection(trackingBranches, tb);
      }
    }
    return trackingBranches == null ? Collections.emptyList() : trackingBranches;
  }

  @NotNull
  private static Collection<String> getTrackingBranches(@NotNull GitRepository repository, @NotNull String remoteBranch) {
    Collection<String> trackingBranches = new ArrayList<>(1);
    for (GitBranchTrackInfo trackInfo : repository.getBranchTrackInfos()) {
      if (remoteBranch.equals(trackInfo.getRemoteBranch().getNameForLocalOperations())) {
        trackingBranches.add(trackInfo.getLocalBranch().getName());
      }
    }
    return trackingBranches;
  }

  @Nullable
  private static GitRemoteBranch getTrackedBranch(@NotNull GitRepository repository, @NotNull String branchName) {
    GitLocalBranch branch = repository.getBranches().findLocalBranch(branchName);
    return branch == null ? null : branch.findTrackedBranch(repository);
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
