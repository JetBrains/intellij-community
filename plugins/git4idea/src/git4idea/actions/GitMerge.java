// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

final class GitMerge extends GitMergeAction {
  @Override
  protected @NotNull String getActionName() {
    return GitBundle.message("merge.action.name");
  }

  @Override
  protected @Nullable DialogState displayDialog(@NotNull Project project, @NotNull List<VirtualFile> gitRoots, @NotNull VirtualFile defaultRoot) {
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

  @NotNull Supplier<GitLineHandler> getHandlerProvider(Project project, GitMergeDialog dialog) {
    VirtualFile root = dialog.getSelectedRoot();
    Set<GitMergeOption> selectedOptions = dialog.getSelectedOptions();
    String commitMsg = dialog.getCommitMessage().trim();
    GitBranch selectedBranch = dialog.getSelectedBranch();

    return () -> {
      GitLineHandler h = new GitLineHandler(project, root, GitCommand.MERGE);

      for (GitMergeOption option : selectedOptions) {
        if (option == GitMergeOption.COMMIT_MESSAGE) {
          if (!commitMsg.isEmpty()) {
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
    if (project != null && !GitUtil.getRepositoriesInStates(project, Repository.State.MERGING).isEmpty()) {
      presentation.setEnabledAndVisible(false);
    }
    else if (project != null && GitUtil.getRepositoriesInStates(project, Repository.State.NORMAL, Repository.State.DETACHED).isEmpty()) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
    }
    else {
      presentation.setEnabledAndVisible(true);
    }
  }
}
