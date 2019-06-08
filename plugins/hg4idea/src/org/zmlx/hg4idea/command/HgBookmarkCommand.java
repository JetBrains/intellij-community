// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.emptyList;
import static java.util.Collections.singletonList;
import static org.zmlx.hg4idea.util.HgUtil.getRepositoryManager;

public class HgBookmarkCommand {

  public static void createBookmarkAsynchronously(@NotNull List<? extends HgRepository> repositories, @NotNull String name, boolean isActive) {
    final Project project = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(repositories)).getProject();
    if (StringUtil.isEmptyOrSpaces(name)) {
      VcsNotifier.getInstance(project).notifyError("Hg Error", "Bookmark name is empty");
      return;
    }
    new Task.Backgroundable(project, HgVcsMessages.message("hg4idea.progress.bookmark", name)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (HgRepository repository : repositories) {
          executeBookmarkCommandSynchronously(project, repository.getRoot(), name, isActive ? emptyList() : singletonList("--inactive"));
        }
      }
    }.queue();
  }

  public static void deleteBookmarkSynchronously(@NotNull Project project, @NotNull VirtualFile repo, @NotNull String name) {
    executeBookmarkCommandSynchronously(project, repo, name, singletonList("-d"));
  }

  private static void executeBookmarkCommandSynchronously(@NotNull Project project,
                                                          @NotNull VirtualFile repositoryRoot,
                                                          @NotNull String name,
                                                          @NotNull List<String> args) {
    ArrayList<String> arguments = new ArrayList<>(args);
    arguments.add(name);
    HgCommandResult result = new HgCommandExecutor(project).executeInCurrentThread(repositoryRoot, "bookmark", arguments);
    getRepositoryManager(project).updateRepository(repositoryRoot);
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(project)
        .notifyError(result, "Hg Error",
                     String.format("Hg bookmark command failed for repository %s with name %s ", repositoryRoot.getName(), name));
    }
  }
}
