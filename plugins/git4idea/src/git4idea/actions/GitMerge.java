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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import com.intellij.util.containers.ContainerUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeDialog;
import git4idea.merge.MergeOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class GitMerge extends GitMergeAction {

  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("merge.action.name");
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
                           dialog.getSelectedBranches(),
                           dialog.shouldCommitAfterMerge(),
                           ContainerUtil.map(dialog.getSelectedOptions(), option -> option.getOption()));
  }

  @NotNull
  protected Supplier<GitLineHandler> getHandlerProvider(Project project, GitMergeDialog dialog) {
    VirtualFile root = dialog.getSelectedRoot();
    Set<MergeOption> selectedOptions = dialog.getSelectedOptions();
    String commitMsg = dialog.getCommitMessage().trim();
    List<String> selectedBranches = dialog.getSelectedBranches();

    return () -> {
      GitLineHandler h = new GitLineHandler(project, root, GitCommand.MERGE);

      for (MergeOption option : selectedOptions) {
        if (option == MergeOption.COMMIT_MESSAGE) {
          if (commitMsg.length() > 0) {
            h.addParameters(option.getOption(), commitMsg);
          }
        }
        else {
          h.addParameters(option.getOption());
        }
      }

      for (String branch : selectedBranches) {
        h.addParameters(branch);
      }

      return h;
    };
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project != null && !GitUtil.getRepositoriesInState(project, Repository.State.MERGING).isEmpty()) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }
}
