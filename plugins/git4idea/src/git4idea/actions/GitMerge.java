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

import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRevisionNumber;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeDialog;
import git4idea.merge.GitMergeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Git "merge" action
 */
public class GitMerge extends GitRepositoryAction {

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("merge.action.name");
  }

  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {
    GitMergeDialog dialog = new GitMergeDialog(project, gitRoots, defaultRoot);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    Label beforeLabel = LocalHistory.getInstance().putSystemLabel(project, "Before update");
    GitLineHandler h = dialog.handler();
    final VirtualFile root = dialog.getSelectedRoot();
    affectedRoots.add(root);
    GitRevisionNumber currentRev = GitRevisionNumber.resolve(project, root, "HEAD");
    try {
      GitHandlerUtil.doSynchronously(h, GitBundle.message("merging.title", dialog.getSelectedRoot().getPath()), h.printableCommandLine());
    }
    finally {
      exceptions.addAll(h.errors());
    }
    if (exceptions.size() != 0) {
      return;
    }
    GitMergeUtil.showUpdates(this, project, exceptions, root, currentRev, beforeLabel, getActionName(), ActionInfo.INTEGRATE);
  }
}
