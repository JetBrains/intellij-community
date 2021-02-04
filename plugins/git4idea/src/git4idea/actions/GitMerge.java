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

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeDialog;
import git4idea.merge.GitMergeOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static git4idea.GitNotificationIdsHolder.MERGE_FAILED;

public class GitMerge extends GitMergeAction {

  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.message("merge.action.name");
  }

  @Nullable
  @Override
  protected DialogState displayDialog(@NotNull Project project, @NotNull List<VirtualFile> gitRoots, @NotNull VirtualFile defaultRoot) {
    final GitMergeDialog dialog = new GitMergeDialog(project, defaultRoot, gitRoots);
    if (!dialog.showAndGet()) {
      return null;
    }
    return new DialogState(dialog.getSelectedRoot(),
                           GitBundle.message("merging.title", dialog.getSelectedRoot().getPath()),
                           getHandlerProvider(project, dialog),
                           dialog.getSelectedBranch(),
                           dialog.shouldCommitAfterMerge(),
                           ContainerUtil.map(dialog.getSelectedOptions(), option -> option.getOption()));
  }

  @Override
  protected String getNotificationErrorDisplayId() {
    return MERGE_FAILED;
  }

  @NotNull
  protected Supplier<GitLineHandler> getHandlerProvider(Project project, GitMergeDialog dialog) {
    VirtualFile root = dialog.getSelectedRoot();
    Set<GitMergeOption> selectedOptions = dialog.getSelectedOptions();
    String commitMsg = dialog.getCommitMessage().trim();
    GitBranch selectedBranch = dialog.getSelectedBranch();

    return () -> {
      GitLineHandler h = new GitLineHandler(project, root, GitCommand.MERGE);

      for (GitMergeOption option : selectedOptions) {
        if (option == GitMergeOption.COMMIT_MESSAGE) {
          if (commitMsg.length() > 0) {
            h.addParameters(option.getOption(), commitMsg);
          }
        }
        else {
          h.addParameters(option.getOption());
        }
      }

      h.addParameters(selectedBranch.getName());

      return h;
    };
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project != null && !GitUtil.getRepositoriesInState(project, Repository.State.MERGING).isEmpty()) {
      presentation.setEnabledAndVisible(false);
    }
    else if (project != null && GitUtil.getRepositoriesInState(project, Repository.State.NORMAL).isEmpty()) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
    }
    else {
      presentation.setEnabledAndVisible(true);
    }
  }
}
