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
 * Copyright 2008 MQSoftware
 * Authors: Mark Scott
 */

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBundle;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Git un-stash action
 */
public class GitUnstash extends BasicAction {
  protected void perform(@NotNull Project project, GitVcs vcs, @NotNull List<VcsException> exceptions, @NotNull VirtualFile[] affectedFiles)
      throws VcsException {
    saveAll();

    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(GitVcs.getInstance(project), affectedFiles)) return;

    final Map<VirtualFile, List<VirtualFile>> roots = GitUtil.sortFilesByVcsRoot(project, affectedFiles);

    boolean stashesFound = false;
    for (VirtualFile root : roots.keySet()) {
      GitCommand command = new GitCommand(project, vcs.getSettings(), root);
      String[] stashList = command.stashList();
      if (stashList == null || stashList.length == 0) continue;
      stashesFound = true;
      int stashIndex = Messages
          .showChooseDialog(GitBundle.getString("unstash.message"), GitBundle.getString("unstash.title"), stashList, stashList[0],
                            Messages.getQuestionIcon());
      if (stashIndex < 0) continue;
      GitCommandRunnable cmdr = new GitCommandRunnable(project, vcs.getSettings(), root);
      cmdr.setCommand(GitCommand.STASH_CMD);
      String stashName = stashList[stashIndex].split(":")[0];
      cmdr.setArgs(new String[]{"apply", stashName});

      ProgressManager manager = ProgressManager.getInstance();
      //TODO: make this async so the git command output can be seen in the version control window as it happens...
      manager.runProcessWithProgressSynchronously(cmdr, GitBundle.getString("unstashing.title"), false, project);

      @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"}) VcsException ex = cmdr.getException();
      if (ex != null) {
        GitUtil.showOperationError(project, ex, "git stash apply");
        break;
      }
    }
    if(!stashesFound) {
      Messages.showInfoMessage(project, GitBundle.getString("unstash.notfound.message"), GitBundle.getString("unstash.notfound.title"));
    }
  }

  @NotNull
  protected String getActionName(@NotNull AbstractVcs abstractvcs) {
    return GitBundle.getString("unstash.action.name");
  }

  /**
   * {@inheritDoc}
   */
  protected boolean isEnabled(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile... vFiles) {
    return true;
  }
}
