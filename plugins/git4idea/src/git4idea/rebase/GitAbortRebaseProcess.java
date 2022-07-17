// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.vcs.log.Hash;
import git4idea.DialogManager;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.reset.GitResetMode;
import git4idea.stash.GitChangesSaver;
import git4idea.util.GitFreezingProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.CommonBundle.getCancelButtonText;
import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.dvcs.DvcsUtil.joinShortNames;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static git4idea.GitNotificationIdsHolder.*;
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
    String title = GitBundle.message("rebase.abort.dialog.title");
    if (myRepositoryToAbort != null) {
      if (myRepositoriesToRollback.isEmpty()) {
        String message = GitBundle.message("rebase.abort.dialog.message", getShortRepositoryName(myRepositoryToAbort));
        int choice = DialogManager.showOkCancelDialog(
          myProject,
          message,
          title,
          GitBundle.message("rebase.abort.dialog.ok.button.text"),
          getCancelButtonText(),
          getQuestionIcon()
        );
        if (choice == Messages.OK) {
          return AbortChoice.ABORT;
        }
      }
      else {
        String message = GitBundle.message(
          "rebase.abort.and.rollback.dialog.message",
          getShortRepositoryName(myRepositoryToAbort),
          joinShortNames(myRepositoriesToRollback.keySet(), 5)
        );
        int choice = DialogManager.showYesNoCancelDialog(
          myProject,
          message,
          title,
          GitBundle.message("rebase.abort.and.rollback.dialog.yes.button.text"),
          GitBundle.message("rebase.abort.and.rollback.dialog.no.button.text"),
          getCancelButtonText(),
          getQuestionIcon()
        );
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
        String description = GitBundle.message(
          "rebase.abort.rollback.successful.rebase.dialog.message",
          joinShortNames(myRepositoriesToRollback.keySet(), -1)
        );
        int choice = DialogManager.showOkCancelDialog(
          myProject,
          description,
          title,
          GitBundle.message("rebase.abort.rollback.successful.rebase.dialog.ok.button.text"),
          getCancelButtonText(),
          getQuestionIcon()
        );
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
    boolean[] success = new boolean[1];

    new GitFreezingProcess(myProject, GitBundle.message("activity.name.rebase"), () -> {
      try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, GitBundle.message("activity.name.rebase"))) {
        if (myRepositoryToAbort != null) {
          myIndicator.setText2(GitBundle.message(
            "rebase.abort.progress.indicator.command.in.repo.title",
            "git rebase --abort",
            getShortRepositoryName(myRepositoryToAbort)
          ));
          Hash startHash = GitUtil.getHead(myRepositoryToAbort);
          GitCommandResult result = myGit.rebaseAbort(myRepositoryToAbort);
          if (result.success()) {
            GitUtil.updateAndRefreshChangedVfs(myRepositoryToAbort, startHash);
          }
          else {
            myNotifier.notifyError(
              REBASE_ABORT_FAILED,
              GitBundle.message("rebase.abort.notification.failed.title"),
              result.getErrorOutputAsHtmlString() + mentionLocalChangesRemainingInStash(mySaver),
              true);
            return;
          }
        }

        if (rollback) {
          for (GitRepository repo : myRepositoriesToRollback.keySet()) {
            myIndicator.setText2(GitBundle.message(
              "rebase.abort.progress.indicator.command.in.repo.title",
              "git reset --keep",
              getShortRepositoryName(repo)
            ));
            Hash startHash = GitUtil.getHead(repo);
            GitCommandResult res = myGit.reset(repo, GitResetMode.KEEP, myRepositoriesToRollback.get(repo));

            if (res.success()) {
              String initialBranchPosition = myInitialCurrentBranches.get(repo);
              if (initialBranchPosition != null && !initialBranchPosition.equals(repo.getCurrentBranchName())) {
                myIndicator.setText2(GitBundle.message(
                  "rebase.abort.progress.indicator.command.in.repo.title",
                  "git checkout " + initialBranchPosition,
                  getShortRepositoryName(repo)
                ));
                res = myGit.checkout(repo, initialBranchPosition, null, true, false);
              }
            }

            if (!res.success()) {
              String description;
              if (myRepositoryToAbort != null) {
                description = GitBundle.message(
                  "rebase.abort.notification.warning.rollback.failed.with.repo.message",
                  getShortRepositoryName(myRepositoryToAbort),
                  repo,
                  res.getErrorOutputAsHtmlString(),
                  mentionLocalChangesRemainingInStash(mySaver)
                );
              }
              else {
                description = GitBundle.message(
                  "rebase.abort.notification.warning.rollback.failed.message",
                  getShortRepositoryName(repo),
                  res.getErrorOutputAsHtmlString(),
                  mentionLocalChangesRemainingInStash(mySaver)
                );
              }
              myNotifier.notifyImportantWarning(
                REBASE_ROLLBACK_FAILED,
                GitBundle.message("rebase.abort.notification.warning.rollback.failed.title"),
                description
              );
              return;
            }

            GitUtil.updateAndRefreshChangedVfs(repo, startHash);
          }
        }

        success[0] = true;
      }
    }).execute();

    if (success[0]) {
      ChangeListManagerEx.getInstanceEx(myProject).waitForUpdate();

      if (mySaver != null) {
        mySaver.load();
      }
      if (myNotifySuccess) {
        myNotifier.notifySuccess(REBASE_ABORT_SUCCESS, "", GitBundle.message("rebase.abort.notification.successful.message"));
      }
    }
  }
}
