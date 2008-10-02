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
 * Authors: Erlend Simonsen & Mark Scott
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
import git4idea.GitBundle;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Git "tag" action
 */
public class GitTag extends BasicAction {
  @Override
  public void perform(@NotNull Project project, GitVcs vcs, @NotNull List<VcsException> exceptions, @NotNull VirtualFile[] affectedFiles)
      throws VcsException {
    saveAll();

    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, affectedFiles)) {
      Messages.showErrorDialog(project, GitBundle.getString("tag.nonvcs.error.message"), GitBundle.getString("tag.nonvcs.error.title"));
      return;
    }

    final String tagName =
        Messages.showInputDialog(project, GitBundle.getString("tag.message"), GitBundle.getString("tag.title"), Messages.getQuestionIcon());
    if (tagName == null || tagName.length() == 0) return;

    final Map<VirtualFile, List<VirtualFile>> roots = GitUtil.sortFilesByVcsRoot(project, affectedFiles);

    for (VirtualFile root : roots.keySet()) {
      GitCommandRunnable cmdr = new GitCommandRunnable(project, vcs.getSettings(), root);
      cmdr.setCommand(GitCommand.TAG_CMD);
      cmdr.setArgs(new String[]{tagName});

      ProgressManager manager = ProgressManager.getInstance();
      //TODO: make this async so the git command output can be seen in the version control window as it happens...
      manager.runProcessWithProgressSynchronously(cmdr, GitBundle.getString("tagging.title"), false, project);

      @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"}) VcsException ex = cmdr.getException();
      if (ex != null) {
        GitUtil.showOperationError(project, ex, "git tag");
        break;
      }
    }
  }

  @Override
  @NotNull
  protected String getActionName(@NotNull AbstractVcs abstractvcs) {
    return GitBundle.getString("tag.action.name");
  }

  @Override
  protected boolean isEnabled(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile... vFiles) {
    return true;
  }
}
