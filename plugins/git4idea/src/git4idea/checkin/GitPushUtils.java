/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
import git4idea.commands.GitHandler;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerAdapter;

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
   */
  public static GitLineHandler preparePush(Project project, VirtualFile vcsRoot) {
    final GitLineHandler rc = new GitLineHandler(project, vcsRoot, GitHandler.PUSH);
    rc.addParameters("-v");
    rc.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(final String line, final Key outputType) {
        if (outputType == ProcessOutputTypes.STDERR && line.startsWith(" ! [")) {
          //noinspection ThrowableInstanceNeverThrown
          rc.addError(new VcsException("Rejected push: " + line));
        }
      }
    });
    return rc;
  }
}
