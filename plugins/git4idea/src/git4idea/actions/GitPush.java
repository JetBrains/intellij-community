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
package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.checkin.GitOldPushDialog;
import git4idea.commands.GitHandlerUtil;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Git "push" action
 */
public class GitPush extends GitRepositoryAction {

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("push.action.name");
  }

  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {
    GitOldPushDialog d = new GitOldPushDialog(project, gitRoots, defaultRoot);
    d.show();
    if (!d.isOK()) {
      return;
    }
    GitHandlerUtil.doSynchronously(d.handler(), GitBundle.getString("pushing.all.changes"), "git push");
    GitRepositoryManager.getInstance(project).updateRepository(d.getGitRoot(), GitRepository.TrackedTopic.ALL);
  }
}