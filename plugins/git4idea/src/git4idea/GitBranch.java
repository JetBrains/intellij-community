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
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.StringTokenizer;

/**
 * This data class represents a Git branch
 */
public class GitBranch {
  private final Project project;
  private final String name;
  private final boolean remote;
  private final boolean active;
  /**
   * The name that specifies that git is on specific commit rather then on some branch
   */
  @NonNls public static final String NO_BRANCH_NAME = "(no " + GitHandler.BRANCH + ")";

  public GitBranch(@NotNull Project project, @NotNull String name, boolean active, boolean remote) {
    this.project = project;
    this.name = name;
    this.remote = remote;
    this.active = active;
  }

  @NotNull
  public Project getProject() {
    return project;
  }

  @NotNull
  public String getName() {
    return name;
  }

  public boolean isRemote() {
    return remote;
  }

  public boolean isActive() {
    return active;
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
    for (StringTokenizer lines = new StringTokenizer(h.run(), "\n"); lines.hasMoreTokens();) {
      String line = lines.nextToken();
      if (line != null && line.startsWith("*")) {
        //noinspection HardCodedStringLiteral
        if (line.endsWith(NO_BRANCH_NAME)) {
          return null;
        }
        else {
          return new GitBranch(project, line.substring(2), true, false);
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
  public static void list(final Project project,
                          final VirtualFile root,
                          final boolean remote,
                          final boolean local,
                          final Collection<String> branches) throws VcsException {
    if (!local && !remote) {
      // no need to run hanler
      return;
    }
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitHandler.BRANCH);
    handler.setNoSSH(true);
    handler.setSilent(true);
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
}
