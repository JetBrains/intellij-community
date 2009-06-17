package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitConfigUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;

/**
 * This data class represents a Git branch
 */
public class GitBranch extends GitReference {
  /**
   * If true, the branch is remote
   */
  private final boolean myRemote;
  /**
   * If true, the branch is active
   */
  private final boolean myActive;
  /**
   * The name that specifies that git is on specific commit rather then on some branch ({@value})
   */
  @NonNls public static final String NO_BRANCH_NAME = "(no branch)";
  /**
   * Prefix for local branches ({@value})
   */
  @NonNls public static final String REFS_HEADS_PREFIX = "refs/heads/";
  /**
   * Prefix for remote branches ({@value})
   */
  @NonNls public static final String REFS_REMOTES_PREFIX = "refs/remotes/";

  /**
   * The constructor for the branch
   *
   * @param name   the name of the branch
   * @param active if true, the branch is active
   * @param remote if true, the branch is remote
   */
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

  /**
   * Get current branch
   *
   * @param project a project
   * @param root    a directory inside the repository
   * @return the current branch or null if there is no current branch or if specific commit has been checked out
   * @throws VcsException if there is a problem running git
   */
  @Nullable
  public static GitBranch current(Project project, VirtualFile root) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitHandler.BRANCH);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("--no-color");
    for (StringTokenizer lines = new StringTokenizer(h.run(), "\n"); lines.hasMoreTokens();) {
      String line = lines.nextToken();
      if (line != null && line.startsWith("*")) {
        //noinspection HardCodedStringLiteral
        if (line.endsWith(NO_BRANCH_NAME)) {
          return null;
        }
        else {
          return new GitBranch(line.substring(2), true, false);
        }
      }
    }
    return null;
  }

  /**
   * List branches for the git root
   *
   * @param project  the context project
   * @param root     the git root
   * @param remote   if true remote branches are listed
   * @param local    if true local branches are listed
   * @param branches the collection used to store branches
   * @throws VcsException if there is a problem with running git
   */
  public static void listAsStrings(final Project project,
                                   final VirtualFile root,
                                   final boolean remote,
                                   final boolean local,
                                   final Collection<String> branches) throws VcsException {
    if (!local && !remote) {
      // no need to run handler
      return;
    }
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitHandler.BRANCH);
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.addParameters("--no-color");
    if (remote && local) {
      handler.addParameters("-a");
    }
    else if (remote) {
      handler.addParameters("-r");
    }
    for (String line : handler.run().split("\n")) {
      if (line.length() == 0 || line.endsWith(NO_BRANCH_NAME)) {
        continue;
      }
      branches.add(line.substring(2));
    }
  }

  /**
   * {@inheritDoc}
   */
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
   * List branches for the git root
   *
   * @param project  the context project
   * @param root     the git root
   * @param remote   if true remote branches are listed
   * @param local    if true local branches are listed
   * @param branches the collection used to store branches
   * @throws VcsException if there is a problem with running git
   */
  public static void list(final Project project,
                          final VirtualFile root,
                          final boolean local,
                          final boolean remote,
                          final Collection<GitBranch> branches) throws VcsException {
    ArrayList<String> temp = new ArrayList<String>();
    if (local) {
      listAsStrings(project, root, false, true, temp);
      for (String b : temp) {
        branches.add(new GitBranch(b, false, false));
      }
      temp.clear();
    }
    if (remote) {
      listAsStrings(project, root, true, false, temp);
      for (String b : temp) {
        branches.add(new GitBranch(b, false, true));
      }
    }
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
    branch = branch.substring(REFS_HEADS_PREFIX.length());
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
}
