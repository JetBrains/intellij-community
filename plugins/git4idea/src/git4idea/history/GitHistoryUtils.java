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
package git4idea.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * History utilities for Git
 */
public class GitHistoryUtils {
  /**
   * A private constructor
   */
  private GitHistoryUtils() {
  }

  /**
   * Get current revision for the file under git
   *
   * @param project  a project
   * @param filePath a file path
   * @return a revision number or null if the file is unversioned or new
   * @throws VcsException if there is problem with running git
   */
  @Nullable
  public static VcsRevisionNumber getCurrentRevision(final Project project, final FilePath filePath) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, GitUtil.getGitRoot(project, filePath), "log");
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("-n1", "--pretty=format:%H%n%ct%n");
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = h.run();
    if (result.length() == 0) {
      return null;
    }
    String[] lines = result.split("\n");
    String revstr = lines[0];
    Date commitDate = GitUtil.parseTimestamp(lines[1]);
    return new GitRevisionNumber(revstr, commitDate);
  }
}
