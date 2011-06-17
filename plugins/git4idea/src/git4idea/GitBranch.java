/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitConfigUtil;
import git4idea.history.GitHistoryUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * This data class represents a Git branch
 */
public class GitBranch extends GitReference {
  @NonNls public static final String NO_BRANCH_NAME = "(no branch)"; // The name that specifies that git is on specific commit rather then on some branch ({@value}) 
  @NonNls public static final String REFS_HEADS_PREFIX = "refs/heads/"; // Prefix for local branches ({@value})
  @NonNls public static final String REFS_REMOTES_PREFIX = "refs/remotes/"; // Prefix for remote branches ({@value})

  private final boolean myRemote;
  private boolean myActive;
  private static final Logger LOG = Logger.getInstance(GitBranch.class);

  public GitBranch(@NotNull String name, boolean active, boolean remote) {
    super(name);
    myRemote = remote;
    myActive = active;
  }

  /**
   * @return true if the branch is remote
   */
  public boolean isRemote() {
    return myRemote;
  }

  /**
   * @return true if the branch is active
   */
  public boolean isActive() {
    return myActive;
  }

  @NotNull
  public String getFullName() {
    return (myRemote ? REFS_REMOTES_PREFIX : REFS_HEADS_PREFIX) + myName;
  }

  /**
   * Get tracked remote for the branch
   *
   * @param project the context project
   * @param root    the VCS root to investigate
   * @return the remote name for tracked branch, "." meaning the current repository, or null if no branch is tracked
   * @throws VcsException if there is a problem with running Git
   */
  @Nullable
  public String getTrackedRemoteName(Project project, VirtualFile root) throws VcsException {
    return GitConfigUtil.getValue(project, root, trackedRemoteKey());
  }

  /**
   * Get tracked the branch
   *
   * @param project the context project
   * @param root    the VCS root to investigate
   * @return the name of tracked branch
   * @throws VcsException if there is a problem with running Git
   */
  @Nullable
  public String getTrackedBranchName(Project project, VirtualFile root) throws VcsException {
    return GitConfigUtil.getValue(project, root, trackedBranchKey());
  }

  /**
   * Checks if the branch exists in the repository.
   * @return true if the branch exists, false otherwise.
   */
  public boolean exists(VirtualFile root) {
    final VirtualFile remoteBranch = root.findFileByRelativePath(".git/refs/remotes/" + myName);
    if (remoteBranch != null && remoteBranch.exists()) {
      return true;
    }
    final VirtualFile packedRefs = root.findFileByRelativePath(".git/packed-refs");
    if (packedRefs != null && packedRefs.exists()) {
      final byte[] contents;
      try {
        contents = packedRefs.contentsToByteArray();
        return new String(contents).contains(myName);
      } catch (IOException e) {
        LOG.info("exists ", e);
        return false;
      }
    }
    return false;
  }

  /**
   * Get current branch from Git.
   *
   * @param project a project
   * @param root    vcs root
   * @return the current branch or null if there is no current branch or if specific commit has been checked out.
   * @throws VcsException if there is a problem running git
   */
  @Nullable
  public static GitBranch current(Project project, VirtualFile root) throws VcsException {
    return list(project, root, false, false, null, null);
  }

  /**
   * List branches for the git root as strings.
   * @see #list(com.intellij.openapi.project.Project, com.intellij.openapi.vfs.VirtualFile, boolean, boolean, java.util.Collection, String) 
   */
  @Nullable
  public static GitBranch listAsStrings(final Project project, final VirtualFile root, final boolean remote, final boolean local,
                                        final Collection<String> branches, @Nullable final String containingCommit) throws VcsException {
    final Collection<GitBranch> gitBranches = new ArrayList<GitBranch>();
    final GitBranch result = list(project, root, local, remote, gitBranches, containingCommit);
    for (GitBranch b : gitBranches) {
      branches.add(b.getName());
    }
    return result;
  }

  /**
   * List branches in the repository. Supply a Collection to this method, and it will be filled by branches.
   * @param project          the context project
   * @param root             the git root
   * @param localWanted      should local branches be collected.
   * @param remoteWanted     should remote branches be collected.
   * @param branches         the collection which will be used to store branches.
   *                         Can be null - then the method does the same as {@link #current(com.intellij.openapi.project.Project, com.intellij.openapi.vfs.VirtualFile)}
   * @param containingCommit show only branches which contain the specified commit. If null, no commit filtering is performed.
   * @return current branch. May be null if no branch is active.
   * @throws VcsException if there is a problem with running git
   */
  @Nullable
  public static GitBranch list(final Project project, final VirtualFile root, final boolean localWanted, final boolean remoteWanted,
                               @Nullable final Collection<GitBranch> branches, @Nullable final String containingCommit) throws VcsException {
    // preparing native command executor
    final GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.BRANCH);
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.addParameters("--no-color");
    boolean remoteOnly = false;
    if (remoteWanted && localWanted) {
      handler.addParameters("-a");
      remoteOnly = false;
    } else if (remoteWanted) {
      handler.addParameters("-r");
      remoteOnly = true;
    }
    if (containingCommit != null) {
      handler.addParameters("--contains", containingCommit);
    }
    final String output = handler.run();

    if (output.trim().length() == 0) {
      // the case after git init and before first commit - there is no branch and no output, and we'll take refs/heads/master
      String head;
      try {
        head = FileUtil.loadFile(new File(root.getPath(), ".git/HEAD"), GitUtil.UTF8_ENCODING).trim();
        final String prefix = "ref: refs/heads/";
        return head.startsWith(prefix) ? new GitBranch(head.substring(prefix.length()), true, false) : null;
      } catch (IOException e) {
        return null;
      }
    }

    // standard situation. output example:
    //  master
    //* my_feature
    //  remotes/origin/HEAD -> origin/master
    //  remotes/origin/eap
    //  remotes/origin/feature
    //  remotes/origin/master
    // also possible:
    //* (no branch)
    // and if we call with -r instead of -a, remotes/ prefix is omitted:
    // origin/HEAD -> origin/master
    final String[] split = output.split("\n");
    GitBranch currentBranch = null;
    String activeRemoteName = null;
    for (String b : split) {
      boolean current = b.charAt(0) == '*';
      b = b.substring(2).trim();
      if (b.equals(NO_BRANCH_NAME)) { continue; }

      String remotePrefix = null;
      if (b.startsWith("remotes/")) {
        remotePrefix = "remotes/";
      } else if (b.startsWith(REFS_REMOTES_PREFIX)) {
        remotePrefix = REFS_REMOTES_PREFIX;
      }
      boolean isRemote = remotePrefix != null || remoteOnly;
      if (isRemote) {
        if (! remoteOnly) {
          b = b.substring(remotePrefix.length());
        }
        final int idx = b.indexOf("HEAD ->");
        if (idx > 0) {
          activeRemoteName = b.substring(idx + "HEAD ->".length() + (remotePrefix == null ? 0 : remotePrefix.length()));
          continue;
        }
      }
      final GitBranch branch = new GitBranch(b, current, isRemote);
      if (current) {
        currentBranch = branch;
      }
      if (branches != null && ((isRemote && remoteWanted) || (!isRemote && localWanted))) {
        branches.add(branch);
      }
    }
    if (activeRemoteName != null) {
      for (GitBranch branch : branches) {
        if (activeRemoteName.equals(branch.getName())) {
          branch.setActive(true);
          break;
        }
      }
    }
    return currentBranch;
  }

  /**
   * Set tracked branch
   *
   * @param project the context project
   * @param root    the git root
   * @param remote  the remote to track (null, for do not track anything, "." for local repository)
   * @param branch  the branch to track
   */
  public void setTrackedBranch(Project project, VirtualFile root, String remote, String branch) throws VcsException {
    if (remote == null || branch == null) {
      GitConfigUtil.unsetValue(project, root, trackedRemoteKey());
      GitConfigUtil.unsetValue(project, root, trackedBranchKey());
    }
    else {
      GitConfigUtil.setValue(project, root, trackedRemoteKey(), remote);
      GitConfigUtil.setValue(project, root, trackedBranchKey(), branch);
    }
  }

  /**
   * @return the key for the remote of the tracked branch
   */
  private String trackedBranchKey() {
    return "branch." + getName() + ".merge";
  }

  /**
   * @return the key for the tracked branch
   */
  private String trackedRemoteKey() {
    return "branch." + getName() + ".remote";
  }

  /**
   * Get tracked branch for the current branch
   *
   * @param project the project
   * @param root    the vcs root
   * @return the tracked branch
   * @throws VcsException if there is a problem with accessing configuration file
   */
  @Nullable
  public GitBranch tracked(Project project, VirtualFile root) throws VcsException {
    String remote = getTrackedRemoteName(project, root);
    if (remote == null) {
      return null;
    }
    String branch = getTrackedBranchName(project, root);
    if (branch == null) {
      return null;
    }
    if (branch.startsWith(REFS_HEADS_PREFIX)) {
      branch = branch.substring(REFS_HEADS_PREFIX.length());
    }
    boolean remoteFlag;
    if (!".".equals(remote)) {
      branch = remote + "/" + branch;
      remoteFlag = true;
    }
    else {
      remoteFlag = false;
    }
    return new GitBranch(branch, false, remoteFlag);
  }

  /**
   * Get a merge base between the current branch and specified branch.
   *
   * @param project the current project
   * @param root    the vcs root
   * @param branch  the branch
   * @return the common commit or null if the there is no common commit
   * @throws VcsException the exception
   */
  @Nullable
  public GitRevisionNumber getMergeBase(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitBranch branch)
    throws VcsException {
    return GitHistoryUtils.getMergeBase(project, root, this.getFullName(), branch.getFullName());
  }

  public void setActive(boolean active) {
    myActive = active;
  }
}
