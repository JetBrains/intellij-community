/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.config;

import com.intellij.execution.util.ExecutableValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;

/**
 * Project service that is used to check whether currently set git executable is valid (just calls 'git version' and parses the output),
 * and to display notification to the user proposing to fix the project set up.
 * @author Kirill Likhodedov
 */
public class GitExecutableValidator extends ExecutableValidator {

  private GitVcs myVcs;
  private static final String UNIX_EXECUTABLE = "git";
  private static final String SYSTEM_DEFAULT_EXECUTABLE = SystemInfo.isWindows ? "git.exe": UNIX_EXECUTABLE;

  public GitExecutableValidator(Project project) {
    super(project, GitVcs.NOTIFICATION_GROUP_ID, GitVcs.getInstance(project).getConfigurable());
    myVcs = GitVcs.getInstance(project);
    setMessagesAndTitles(GitBundle.message("git.executable.notification.title"),
                         GitBundle.message("git.executable.notification.description"),
                         GitBundle.message("git.executable.dialog.title"),
                         GitBundle.message("git.executable.dialog.description"),
                         GitBundle.message("git.executable.dialog.error"),
                         GitBundle.message("git.executable.filechooser.title"),
                         GitBundle.message("git.executable.filechooser.description"));
  }

  @Override
  protected String getCurrentExecutable() {
    return myVcs.getAppSettings().getPathToGit();
  }

  @Override
  public boolean isExecutableValid(String executable) {
    if (!executable.endsWith(SYSTEM_DEFAULT_EXECUTABLE) && !executable.endsWith(UNIX_EXECUTABLE)) {
      return false;
    }
    return super.isExecutableValid(executable);
  }

  @Override
  protected void saveCurrentExecutable(String executable) {
    myVcs.getAppSettings().setPathToGit(executable);
  }

  /**
   * Checks if git executable is valid. If not (which is a common case for low-level vcs exceptions), shows the
   * notification. Otherwise throws the exception.
   * This is to be used in catch-clauses
   * @param e exception which was thrown.
   * @throws VcsException if git executable is valid.
   */
   public void showNotificationOrThrow(VcsException e) throws VcsException {
    if (checkExecutableAndNotifyIfNeeded()) {
      throw e;
    }
  }
}
