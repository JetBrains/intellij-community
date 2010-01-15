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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitSimpleHandler;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseActionDialog;
import git4idea.rebase.GitRebaseUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Rebase abort action
 */
public class GitRebaseAbort extends GitRepositoryAction {

  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull Project project,
                         @NotNull List<VirtualFile> gitRoots,
                         @NotNull VirtualFile defaultRoot,
                         Set<VirtualFile> affectedRoots,
                         List<VcsException> exceptions) throws VcsException {
    // remote all roots where there are no rebase in progress
    for (Iterator<VirtualFile> i = gitRoots.iterator(); i.hasNext();) {
      if (!GitRebaseUtils.isRebaseInTheProgress(i.next())) {
        i.remove();
      }
    }
    if (gitRoots.size() == 0) {
      Messages.showErrorDialog(project, GitBundle.getString("rebase.action.no.root"), GitBundle.getString("rebase.action.error"));
      return;
    }
    final VirtualFile root;
    if (gitRoots.size() == 1) {
      root = gitRoots.get(0);
    }
    else {
      if (!gitRoots.contains(defaultRoot)) {
        defaultRoot = gitRoots.get(0);
      }
      GitRebaseActionDialog d = new GitRebaseActionDialog(project, getActionName(), gitRoots, defaultRoot);
      root = d.selectRoot();
      if (root == null) {
        return;
      }
    }
    affectedRoots.add(root);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.REBASE);
    h.addParameters("--abort");
    GitHandlerUtil.doSynchronously(h, getActionName(), h.printableCommandLine());
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("rebase.abort.action.name");
  }
}
