/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.containers.ContainerUtil;
import git4idea.DialogManager;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.repo.GitRepository;
import git4idea.reset.GitResetMode;
import git4idea.stash.GitChangesSaver;
import git4idea.util.GitFreezingProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.intellij.CommonBundle.getCancelButtonText;
import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
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

  GitAbortRebaseProcess(@NotNull Project project,
                        @Nullable GitRepository repositoryToAbort,
                        @NotNull Map<GitRepository, String> repositoriesToRollback,
                        @NotNull Map<GitRepository, String> initialCurrentBranches,
                        @NotNull ProgressIndicator progressIndicator,
                        @Nullable GitChangesSaver changesSaver) {
    myProject = project;
    myRepositoryToAbort = repositoryToAbort;
    myRepositoriesToRollback = repositoriesToRollback;
    myInitialCurrentBranches = initialCurrentBranches;
    myIndicator = progressIndicator;
    mySaver = changesSaver;

    myGit = ServiceManager.getService(Git.class);
    myNotifier = VcsNotifier.getInstance(myProject);
  }

  void abortWithConfirmation() {
    LOG.info("Abort rebase. " + (myRepositoryToAbort == null ? "Nothing to abort" : getShortRepositoryName(myRepositoryToAbort)) +
              ". Roots to rollback: " + DvcsUtil.joinShortNames(myRepositoriesToRollback.keySet()));
    final Ref<AbortChoice> ref = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        ref.set(confirmAbort());
      }
    }, ModalityState.defaultModalityState());

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
        String message = "Are you sure you want to abort rebase" + GitUtil.mention(myRepositoryToAbort) + "?";
        int choice = DialogManager.showOkCancelDialog(myProject, message, title, "Abort", getCancelButtonText(), getQuestionIcon());
        if (choice == Messages.OK) {
          return AbortChoice.ABORT;
        }
      }
      else {
        String message = "Do you want just to abort rebase" + GitUtil.mention(myRepositoryToAbort) + ",\n" +
                         "or also rollback the successful rebase" + GitUtil.mention(myRepositoriesToRollback.keySet()) + "?";
        int choice = DialogManager.showYesNoCancelDialog(myProject, message, title, "Abort & Rollback", "Abort",
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
    new GitFreezingProcess(myProject, ServiceManager.getService(GitPlatformFacade.class), "rebase", new Runnable() {
      public void run() {
        AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
        List<GitRepository> repositoriesToRefresh = ContainerUtil.newArrayList();
        try {
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
          myNotifier.notifySuccess("Rebase abort succeeded");
        }
        finally {
          refresh(repositoriesToRefresh);
          DvcsUtil.workingTreeChangeFinished(myProject, token);
        }
      }
    }).execute();
  }

  private static void refresh(@NotNull List<GitRepository> toRefresh) {
    for (GitRepository repository : toRefresh) {
      repository.update();
    }
    markDirtyAndRefresh(false, true, false, toVirtualFileArray(getRootsFromRepositories(toRefresh)));
  }
}
