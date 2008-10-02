package git4idea.actions;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 *
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitBundle;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Git "pull" action
 */
public class GitPull extends BasicAction {
  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
  @Override
  protected void perform(@NotNull Project project, GitVcs vcs, @NotNull List<VcsException> exceptions, @NotNull VirtualFile[] affectedFiles)
      throws VcsException {
    saveAll();

    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs);
    for (VirtualFile root : roots) {
      GitCommand command = new GitCommand(project, vcs.getSettings(), root);

      String initialValue = null;
      List<GitBranch> rbranches = command.branchList(true);
      if (rbranches != null && rbranches.size() > 0) {
        initialValue = command.remoteRepoURL(rbranches.get(0));
      }
      String repoURL = Messages.showInputDialog(project, GitBundle.getString("pull.url.message"), GitBundle.getString("pull.url.title"),
                                                Messages.getQuestionIcon(), initialValue, null);

      GitCommandRunnable cmdr = new GitCommandRunnable(project, vcs.getSettings(), root);
      cmdr.setCommand(GitCommand.FETCH_CMD);
      cmdr.setArgs(new String[]{repoURL});

      ProgressManager manager = ProgressManager.getInstance();
      manager.runProcessWithProgressSynchronously(cmdr, GitBundle.message("fetching.title", repoURL), false, project);

      VcsException ex = cmdr.getException();
      if (ex != null) {
        GitUtil.showOperationError(project, ex, "git fetch");
        return;
      }

      cmdr.setArgs(new String[]{"--tags", repoURL});
      manager.runProcessWithProgressSynchronously(cmdr, GitBundle.message("fetching.tags.title", repoURL), false, project);
      ex = cmdr.getException();
      if (ex != null) {
        GitUtil.showOperationError(project, ex, "git fetch --tags");
        return;
      }

      List<GitBranch> branches = command.branchList();
      String[] branchesList = new String[branches.size()];
      GitBranch selectedBranch = null;
      int i = 0;
      for (GitBranch b : branches) {
        if (!b.isActive() && selectedBranch == null) selectedBranch = b;
        branchesList[i++] = b.getName();
      }

      if (selectedBranch == null) selectedBranch = branches.get(0);

      int branchNum = Messages
          .showChooseDialog(GitBundle.message("merge.branch.message", command.currentBranch()), GitBundle.getString("merge.branch.title"),
                            branchesList, selectedBranch.getName(), Messages.getQuestionIcon());
      if (branchNum < 0) {
        return;
      }

      selectedBranch = branches.get(branchNum);
      cmdr.setCommand(GitCommand.MERGE_CMD);
      cmdr.setArgs(new String[]{selectedBranch.getName()});
      //TODO: make this async so the git command output can be seen in the version control window as it happens...
      manager.runProcessWithProgressSynchronously(cmdr, GitBundle.message("merging.branch", selectedBranch.getName()), false, project);
      ex = cmdr.getException();
      if (ex != null) {
        GitUtil.showOperationError(project, ex, "git merge");
      }
    }
  }

  @Override
  @NotNull
  protected String getActionName(@NotNull AbstractVcs abstractvcs) {
    return GitBundle.getString("pull.action.name");
  }

  @Override
  protected boolean isEnabled(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile... vFiles) {
    return true;
  }
}
