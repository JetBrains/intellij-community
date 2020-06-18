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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVersionSpecialty;
import git4idea.i18n.GitBundle;
import git4idea.pull.GitPullDialog;
import git4idea.pull.PullOption;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static git4idea.commands.GitImpl.REBASE_CONFIG_PARAMS;

public class GitPull extends GitMergeAction {
  private static final Logger LOG = Logger.getInstance(GitPull.class);

  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("pull.action.name");
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
                           GitBundle.message("pulling.title", dialog.getRemote().getName()),
                           getHandlerProvider(project, dialog),
                           dialog.getSelectedBranches(),
                           dialog.isCommitAfterMerge(),
                           ContainerUtil.map(dialog.getSelectedOptions(), option -> option.getOption()));
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
    return "interactive".equals(value);
  }

  @NotNull
  protected Supplier<GitLineHandler> getHandlerProvider(Project project, GitPullDialog dialog) {
    GitRemote remote = dialog.getRemote();
    String remoteName = remote.getName();

    VirtualFile root = dialog.gitRoot();
    Set<PullOption> selectedOptions = dialog.getSelectedOptions();
    List<String> selectedBranches = dialog.getSelectedBranches();

    return () -> {
      final List<String> urls = remote.getUrls();

      GitLineHandler h = new GitLineHandler(project, root, GitCommand.PULL, REBASE_CONFIG_PARAMS);
      h.setUrls(urls);
      h.addParameters("--no-stat");

      for (PullOption option : selectedOptions) {
        h.addParameters(option.getOption());
      }

      h.addParameters("-v");
      if (GitVersionSpecialty.ABLE_TO_USE_PROGRESS_IN_REMOTE_COMMANDS.existsIn(project)) {
        h.addParameters("--progress");
      }

      h.addParameters(remoteName);
      for (String branch : selectedBranches) {
        h.addParameters(branch);
      }
      return h;
    };
  }
}