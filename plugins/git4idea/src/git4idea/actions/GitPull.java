// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.branch.GitBranchPair;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVersionSpecialty;
import git4idea.config.UpdateMethod;
import git4idea.i18n.GitBundle;
import git4idea.pull.GitPullDialog;
import git4idea.pull.GitPullOption;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitUpdateExecutionProcess;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static git4idea.GitNotificationIdsHolder.PULL_FAILED;
import static java.util.Collections.singletonList;

final class GitPull extends GitMergeAction {
  private static final Logger LOG = Logger.getInstance(GitPull.class);
  private static final @NonNls String INTERACTIVE = "interactive";

  @Override
  protected @NotNull String getActionName() {
    return GitBundle.message("pull.action.name");
  }

  @Override
  protected DialogState displayDialog(@NotNull Project project, @NotNull List<VirtualFile> gitRoots,
                                      @NotNull VirtualFile defaultRoot) {
    final GitPullDialog dialog = new GitPullDialog(project, gitRoots, defaultRoot);
    if (!dialog.showAndGet()) {
      return null;
    }

    GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
    GitRepository repository = repositoryManager.getRepositoryForRootQuick(dialog.gitRoot());
    assert repository != null : "Repository can't be null for root " + dialog.gitRoot();

    return new DialogState(dialog.gitRoot(),
                           GitBundle.message("pulling.title", dialog.getSelectedRemote().getName()),
                           getHandlerProvider(project, dialog),
                           dialog.getSelectedBranch(),
                           dialog.isCommitAfterMerge(),
                           ContainerUtil.map(dialog.getSelectedOptions(), option -> option.getOption()));
  }

  @Override
  protected String getNotificationErrorDisplayId() {
    return PULL_FAILED;
  }

  @Override
  protected void perform(@NotNull DialogState dialogState, @NotNull Project project) {
    if (!dialogState.selectedOptions.contains(GitPullOption.REBASE.getOption())) {
      super.perform(dialogState, project);
    }
    else {
      performRebase(project, dialogState);
    }
  }

  private static void performRebase(@NotNull Project project, DialogState dialogState) {
    VirtualFile selectedRoot = dialogState.selectedRoot;
    GitRemoteBranch selectedBranch = ((GitRemoteBranch)dialogState.selectedBranch);

    GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);

    GitRepository repository = repositoryManager.getRepositoryForRootQuick(selectedRoot);
    if (repository == null) {
      LOG.error("Unable to find git repository for root: " + selectedRoot.getPresentableUrl());
      return;
    }

    if (repository.getCurrentBranch() == null) {
      LOG.error("Unable to rebase operation since repository is not on a branch");
      return;
    }

    new GitUpdateExecutionProcess(project,
                                  singletonList(repository),
                                  Map.of(repository, new GitBranchPair(repository.getCurrentBranch(), selectedBranch)),
                                  UpdateMethod.REBASE,
                                  false)
      .execute();
  }

  @Override
  protected boolean shouldSetupRebaseEditor(@NotNull Project project, VirtualFile selectedRoot) {
    String value = null;
    try {
      value = GitConfigUtil.getValue(project, selectedRoot, "pull.rebase");
    }
    catch (VcsException e) {
      LOG.warn(e);
    }
    return INTERACTIVE.equals(value);
  }

  @NotNull Supplier<GitLineHandler> getHandlerProvider(Project project, GitPullDialog dialog) {
    GitRemote remote = dialog.getSelectedRemote();
    String remoteName = remote.getName();

    VirtualFile root = dialog.gitRoot();
    Set<GitPullOption> selectedOptions = dialog.getSelectedOptions();
    GitRemoteBranch selectedBranch = dialog.getSelectedBranch();

    return () -> {
      final List<String> urls = remote.getUrls();

      GitLineHandler h = new GitLineHandler(project, root, GitCommand.PULL);
      h.setUrls(urls);
      h.addParameters("--no-stat");

      for (GitPullOption option : selectedOptions) {
        h.addParameters(option.getOption());
      }

      h.addParameters("-v");
      if (GitVersionSpecialty.ABLE_TO_USE_PROGRESS_IN_REMOTE_COMMANDS.existsIn(project)) {
        h.addParameters("--progress");
      }

      h.addParameters(remoteName);

      h.addParameters(selectedBranch.getNameForRemoteOperations());

      return h;
    };
  }
}