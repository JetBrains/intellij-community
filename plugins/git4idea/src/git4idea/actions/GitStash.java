// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitStashUsageCollector;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitStashDialog;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

import static git4idea.GitNotificationIdsHolder.STASH_FAILED;

public class GitStash extends GitRepositoryAction {

  @Override
  protected void perform(@NotNull Project project, @NotNull List<VirtualFile> gitRoots, @NotNull VirtualFile defaultRoot) {
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(GitBundle.message("stash.error.can.not.stash.changes.now"))) {
      return;
    }
    GitStashDialog d = new GitStashDialog(project, gitRoots, defaultRoot);
    if (!d.showAndGet()) {
      return;
    }
    d.logUsage();

    runStashInBackground(project, Collections.singleton(d.getGitRoot()), root -> d.handler());
  }

  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.message("stash.action.name");
  }

  public static void runStashInBackground(@NotNull Project project, @NotNull Collection<VirtualFile> roots,
                                          @NotNull Function<VirtualFile, GitLineHandler> createHandler) {
    new Task.Backgroundable(project, GitBundle.message("stashing.progress.title"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try (AccessToken ignored = DvcsUtil.workingTreeChangeStarted(project, GitBundle.message("stash.action.name"))) {
          Collection<VirtualFile> successfulRoots = new ArrayList<>();
          Map<VirtualFile, @Nls String> failedRoots = new LinkedHashMap<>();
          for (VirtualFile root : roots) {
            StructuredIdeActivity activity = GitStashUsageCollector.logStashPush(project);
            GitCommandResult result = Git.getInstance().runCommand(createHandler.apply(root));
            activity.finished();

            if (result.success()) {
              successfulRoots.add(root);
            }
            else {
              failedRoots.put(root, result.getErrorOutputAsHtmlString());
            }
          }

          if (!successfulRoots.isEmpty()) {
            GitUtil.refreshVfsInRoots(successfulRoots);
          }
          if (!failedRoots.isEmpty()) {
            String rootsList = StringUtil.join(failedRoots.keySet(), VirtualFile::getPresentableName, ",");
            String errorTitle = GitBundle.message("stash.error", StringUtil.shortenTextWithEllipsis(rootsList, 100, 0));
            String errorMessage = new HtmlBuilder()
              .appendWithSeparators(HtmlChunk.br(), ContainerUtil.map(failedRoots.values(), s -> HtmlChunk.raw(s)))
              .toString();
            VcsNotifier.getInstance(project).notifyError(STASH_FAILED, errorTitle, errorMessage, true);
          }
        }
      }
    }.queue();
  }
}
