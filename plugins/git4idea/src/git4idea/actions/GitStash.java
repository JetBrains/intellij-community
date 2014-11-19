/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitPlatformFacade;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitStashDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Git stash action
 */
public class GitStash extends GitRepositoryAction {

  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.isFreezedWithNotification("Can not stash changes now")) return;
    GitStashDialog d = new GitStashDialog(project, gitRoots, defaultRoot);
    if (!d.showAndGet()) {
      return;
    }
    VirtualFile root = d.getGitRoot();
    affectedRoots.add(root);
    final GitLineHandler h = d.handler();
    AccessToken token = DvcsUtil.workingTreeChangeStarted(project);
    try {
      GitHandlerUtil.doSynchronously(h, GitBundle.getString("stashing.title"), h.printableCommandLine());
    }
    finally {
      DvcsUtil.workingTreeChangeFinished(project, token);
    }
    ServiceManager.getService(project, GitPlatformFacade.class).hardRefresh(root);
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("stash.action.name");
  }
}
