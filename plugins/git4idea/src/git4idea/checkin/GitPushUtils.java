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
package git4idea.checkin;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerAdapter;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities that support pushing to remote repository
 */
public class GitPushUtils {
  /**
   * A private constructor for utility class
   */
  private GitPushUtils() {
  }

  /**
   * Prepare push command
   *
   * @param project a project
   * @param vcsRoot a vcsRoot
   * @return a prepared push handler
   * @throws VcsException if the git error happens
   */
  @Nullable
  public static GitLineHandler preparePush(Project project, VirtualFile vcsRoot) throws VcsException {
    GitBranch current = GitBranch.current(project, vcsRoot);
    if (current == null) {
      return null;
    }
    String remote = current.getTrackedRemoteName(project, vcsRoot);
    if (remote == null) {
      return null;
    }
    String tracked = current.getTrackedBranchName(project, vcsRoot);
    if (tracked == null) {
      return null;
    }
    final GitLineHandler rc = new GitLineHandler(project, vcsRoot, GitCommand.PUSH);
    rc.addParameters("-v", remote, current.getFullName() + ":" + tracked);
    trackPushRejectedAsError(rc, "Rejected push (" + vcsRoot.getPresentableUrl() + "): ");
    return rc;
  }

  /**
   * Install listener that tracks rejected push branch operations as errors
   *
   * @param handler the handler to use
   * @param prefix  the prefix for errors
   */
  public static void trackPushRejectedAsError(final GitLineHandler handler, final String prefix) {
    handler.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(final String line, final Key outputType) {
        if (outputType == ProcessOutputTypes.STDERR && line.startsWith(" ! [")) {
          //noinspection ThrowableInstanceNeverThrown
          handler.addError(new VcsException(prefix + line));
        }
      }
    });
  }
}
