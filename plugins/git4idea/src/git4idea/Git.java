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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;

/**
 * Low level layer of Git commands.
 *
 * @author Kirill Likhodedov
 */
public class Git {

  /**
   * Calls 'git init' on the specified directory.
   */
  public static void init(Project project, VirtualFile root) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.INIT);
    h.setNoSSH(true);
    GitHandlerUtil.runInCurrentThread(h, null);
    if (!h.errors().isEmpty()) {
      throw h.errors().get(0);
    }
  }

}
