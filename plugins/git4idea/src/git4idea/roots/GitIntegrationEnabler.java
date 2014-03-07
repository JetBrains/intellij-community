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
package git4idea.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.roots.VcsIntegrationEnabler;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import org.jetbrains.annotations.NotNull;

public class GitIntegrationEnabler extends VcsIntegrationEnabler<GitVcs> {

  private final @NotNull Git myGit;

  private static final Logger LOG = Logger.getInstance(GitIntegrationEnabler.class);

  public GitIntegrationEnabler(@NotNull GitVcs vcs, @NotNull Git git) {
    super(vcs);
    myGit = git;
  }

  protected boolean initOrNotifyError(@NotNull final VirtualFile projectDir) {
    VcsNotifier vcsNotifier = VcsNotifier.getInstance(myProject);
    GitCommandResult result = myGit.init(myProject, projectDir);
    if (result.success()) {
      refreshGitDir(projectDir);
      vcsNotifier.notifySuccess("Created Git repository in " + projectDir.getPresentableUrl());
      return true;
    }
    else {
      if (myVcs.getExecutableValidator().checkExecutableAndNotifyIfNeeded()) {
        vcsNotifier.notifyError("Couldn't git init " + projectDir.getPresentableUrl(), result.getErrorOutputAsHtmlString());
        LOG.info(result.getErrorOutputAsHtmlString());
      }
      return false;
    }
  }

  private static void refreshGitDir(final VirtualFile projectDir) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            LocalFileSystem.getInstance().refreshAndFindFileByPath(projectDir.getPath() + "/" + GitUtil.DOT_GIT);
          }
        });
      }
    });
  }
}
