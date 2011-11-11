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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitBranch;
import git4idea.branch.GitBranchPair;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitPushSpec {

  private static final Logger LOG = Logger.getInstance(GitPushSpec.class);
  
  private final GitRemote myRemote;
  @NotNull private final GitBranch mySource;
  @NotNull private final GitBranch myDest;
  private final boolean myPushAll;

  GitPushSpec(@NotNull GitRemote remote, @NotNull GitBranch source, @NotNull GitBranch dest) {
    myRemote = remote;
    mySource = source;
    myDest = dest;
    myPushAll = false;
  }

  private GitPushSpec() {
    myRemote = null;
    mySource = null;
    myDest = null;
    myPushAll = true;
  }

  static GitPushSpec pushAllSpec() {
    return new GitPushSpec();
  }

  @Nullable
  public GitRemote getRemote() {
    return myRemote;
  }

  @NotNull
  public GitBranch getSource() {
    return mySource;
  }

  @NotNull
  public GitBranch getDest() {
    return myDest;
  }

  /**
   * Parses the refspec to identify local branches that are to be pushed together with remote "destination" branches. 
   * @throws VcsException When looking for thacking branches.
   * TODO read tracking information from the config file, i.e. getting rid from the possible exception here.
   */
  @NotNull
  static List<GitBranchPair> getBranchesForPushAll(@NotNull GitRepository repository) throws VcsException {
    List<GitBranchPair> sourceDests = new ArrayList<GitBranchPair>();
    for (GitBranch branch : repository.getBranches().getLocalBranches()) {
      GitBranchPair forBranch = findSourceDestForBranch(repository, branch);
      if (forBranch != null) {
        sourceDests.add(forBranch);
      }
    }
    return sourceDests;
  }

  public boolean isPushAll() {
    return myPushAll;
  }

  @Nullable
  private static GitBranchPair findSourceDestForBranch(GitRepository repository, GitBranch branch) throws VcsException {
    GitBranch trackedBranch = branch.tracked(repository.getProject(), repository.getRoot());
    if (trackedBranch != null) {
      return new GitBranchPair(branch, trackedBranch);
    }
    GitBranch matchingRemoteBranch = findMatchingRemoteBranch(repository, branch);
    if (matchingRemoteBranch != null) {
      return new GitBranchPair(branch, matchingRemoteBranch);
    }
    return null;
  }

  @Nullable
  private static GitBranch findMatchingRemoteBranch(GitRepository repository, GitBranch branch) throws VcsException {
    /*
    from man git-push:
    git push
               Works like git push <remote>, where <remote> is the current branch's remote (or origin, if no
               remote is configured for the current branch).

     */
    String remoteName = branch.getTrackedRemoteName(repository.getProject(), repository.getRoot());
    if (remoteName == null) {
      if (originExists(repository.getRemotes())) {
        remoteName = "origin";
      } else {
        return null;
      }
    }

    for (GitBranch remoteBranch : repository.getBranches().getRemoteBranches()) {
      if (remoteBranch.getName().equals(remoteName + "/" + branch.getName())) {
        return remoteBranch;
      }
    }
    return null;
  }

  private static boolean originExists(Collection<GitRemote> remotes) {
    for (GitRemote remote : remotes) {
      if (remote.getName().equals("origin")) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return myRemote + " " + mySource + "->" + myDest;
  }
}
