// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsNotifier;
import git4idea.DialogManager;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.repo.GitRepository;
import git4idea.reset.GitResetMode;
import git4idea.stash.GitChangesSaver;
import git4idea.util.GitFreezingProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.CommonBundle.getCancelButtonText;
import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.dvcs.DvcsUtil.joinShortNames;
import static com.intellij.openapi.ui.Messages.canShowMacSheetPanel;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh;
import static com.intellij.openapi.vfs.VfsUtilCore.toVirtualFileArray;
import static git4idea.GitUtil.getRootsFromRepositories;
import static git4idea.rebase.GitRebaseUtils.mentionLocalChangesRemainingInStash;

class GitAbortRebaseProcess {
  private static final Logger LOG = Logger.getInstance(GitAbortRebaseProcess.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final VcsNotifier myNotifier;

  @Nullable private final GitRepository myRepositoryToAbort;
  @NotNull private final Map<GitRepository, String> myRepositoriesToRollback;
  @NotNull private final Map<GitRepository, String> myInitialCurrentBranches;
  @NotNull private final ProgressIndicator myIndicator;
  @Nullable private final GitChangesSaver mySaver;
  private final boolean myNotifySuccess;

  GitAbortRebaseProcess(@NotNull Project project,
                        @Nullable GitRepository repositoryToAbort,
                        @NotNull Map<GitRepository, String> repositoriesToRollback,
                        @NotNull Map<GitRepository, String> initialCurrentBranches,
                        @NotNull ProgressIndicator progressIndicator,
                        @Nullable GitChangesSaver changesSaver,
                        boolean notifySuccess) {
    myProject = project;
    myRepositoryToAbort = repositoryToAbort;
    myRepositoriesToRollback = repositoriesToRollback;
    myInitialCurrentBranches = initialCurrentBranches;
    myIndicator = progressIndicator;
    mySaver = changesSaver;
    myNotifySuccess = notifySuccess;

    myGit = Git.getInstance();
    myNotifier = VcsNotifier.getInstance(myProject);
  }

  void abortWithConfirmation() {
    LOG.info("Abort rebase. " + (myRepositoryToAbort == null ? "Nothing to abort" : getShortRepositoryName(myRepositoryToAbort)) +
              ". Roots to rollback: " + joinShortNames(myRepositoriesToRollback.keySet()));
    final Ref<AbortChoice> ref = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> ref.set(confirmAbort()));

    LOG.info("User choice: " + ref.get());
    if (ref.get() == AbortChoice.ROLLBACK_AND_ABORT) {
      doAbort(true);
    }
    else if (ref.get() == AbortChoice.ABORT) {
      doAbort(false);
    }
  }

  @NotNull
  private AbortChoice confirmAbort() {
    String title = "Abort Rebase";
    if (myRepositoryToAbort != null) {
      if (myRepositoriesToRollback.isEmpty()) {
        String message = "Abort rebase" + GitUtil.mention(myRepositoryToAbort) + "?";
        if (canShowMacSheetPanel()) {
          title = message;
          message = "";
        }
        int choice = DialogManager.showOkCancelDialog(myProject, message, title, "Abort", getCancelButtonText(), getQuestionIcon());
        if (choice == Messages.OK) {
          return AbortChoice.ABORT;
        }
      }
      else {
        String message = String.format("Abort rebase in %s only or also rollback rebase in %s?",
                                       getShortRepositoryName(myRepositoryToAbort),
                                       joinShortNames(myRepositoriesToRollback.keySet(), 5));
        if (canShowMacSheetPanel()) {
          title = message;
          message = "";
        }
        int choice = DialogManager.showYesNoCancelDialog(myProject, message, title, "Abort and Rollback", "Abort Only",
                                                         getCancelButtonText(), getQuestionIcon());
        if (choice == Messages.YES) {
          return AbortChoice.ROLLBACK_AND_ABORT;
        }
        else if (choice == Messages.NO) {
          return AbortChoice.ABORT;
        }
      }
    }
    else {
      if (myRepositoriesToRollback.isEmpty()) {
        LOG.error(new Throwable());
      }
      else {
        String description = "Do you want to rollback the successful rebase" + GitUtil.mention(myRepositoriesToRollback.keySet()) + "?";
        int choice = DialogManager.showOkCancelDialog(myProject, description, title, "Rollback", getCancelButtonText(), getQuestionIcon());
        if (choice == Messages.YES) {
          return AbortChoice.ROLLBACK_AND_ABORT;
        }
      }
    }
    return AbortChoice.CANCEL;
  }

  enum AbortChoice {
    ABORT,
    ROLLBACK_AND_ABORT,
    CANCEL
  }

  private void doAbort(final boolean rollback) {
    new GitFreezingProcess(myProject, "rebase", () -> {
      List<GitRepository> repositoriesToRefresh = new ArrayList<>();
      try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, "Rebase")) {
        if (myRepositoryToAbort != null) {
          myIndicator.setText2("git rebase --abort" + GitUtil.mention(myRepositoryToAbort));
          GitCommandResult result = myGit.rebaseAbort(myRepositoryToAbort);
          repositoriesToRefresh.add(myRepositoryToAbort);
          if (!result.success()) {
            myNotifier.notifyError("Rebase Abort Failed",
                                   result.getErrorOutputAsHtmlString() + mentionLocalChangesRemainingInStash(mySaver));
            return;
          }
        }

        if (rollback) {
          for (GitRepository repo : myRepositoriesToRollback.keySet()) {
            myIndicator.setText2("git reset --keep" + GitUtil.mention(repo));
            GitCommandResult res = myGit.reset(repo, GitResetMode.KEEP, myRepositoriesToRollback.get(repo));
            repositoriesToRefresh.add(repo);

            if (res.success()) {
              String initialBranchPosition = myInitialCurrentBranches.get(repo);
              if (initialBranchPosition != null && !initialBranchPosition.equals(repo.getCurrentBranchName())) {
                myIndicator.setText2("git checkout " + initialBranchPosition + GitUtil.mention(repo));
                res = myGit.checkout(repo, initialBranchPosition, null, true, false);
              }
            }

            if (!res.success()) {
              String description = myRepositoryToAbort != null ?
                                   "Rebase abort was successful" + GitUtil.mention(myRepositoryToAbort) + ", but rollback failed" :
                                   "Rollback failed";
              description += GitUtil.mention(repo) + ":" + res.getErrorOutputAsHtmlString() +
                             mentionLocalChangesRemainingInStash(mySaver);
              myNotifier.notifyImportantWarning("Rebase Rollback Failed", description);
              return;
            }
          }
        }

        if (mySaver != null) {
          mySaver.load();
        }
        if (myNotifySuccess) {
          myNotifier.notifySuccess("Rebase abort succeeded");
        }
      }
      finally {
        refresh(repositoriesToRefresh);
      }
    }).execute();
  }

  private static void refresh(@NotNull List<? extends GitRepository> toRefresh) {
    for (GitRepository repository : toRefresh) {
      repository.update();
    }
    markDirtyAndRefresh(false, true, false, toVirtualFileArray(getRootsFromRepositories(toRefresh)));
  }
}
