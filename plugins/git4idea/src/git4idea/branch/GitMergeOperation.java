/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.NotificationManager;
import git4idea.commands.*;
import git4idea.merge.GitConflictResolver;
import git4idea.merge.GitMergeCommittingConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.repo.GitRepository;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.commands.GitMessageWithFilesDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_MERGE;
import static git4idea.commands.GitMessageWithFilesDetector.Event.UNTRACKED_FILES_OVERWRITTEN_BY;

/**
 * @author Kirill Likhodedov
 */
class GitMergeOperation extends GitBranchOperation {

  private static final Logger LOG = Logger.getInstance(GitMergeOperation.class);
  public static final String ROLLBACK_PROPOSAL = "You may rollback (reset to the commit before merging) not to let branches diverge.";

  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final String myBranchToMerge;
  @NotNull private final String myCurrentBranch;
  @NotNull private final GitRepository myCurrentRepository;
  @NotNull private final Map<GitRepository, String> myCurrentRevisionsBeforeMerge;

  // true in value if unstash will be needed after resolving conflicts.
  @NotNull private final Map<GitRepository, Boolean> myConflictedRepositories = new HashMap<GitRepository, Boolean>();
  private GitPreservingProcess myPreservingProcess;

  protected GitMergeOperation(@NotNull Project project, @NotNull Collection<GitRepository> repositories,
                              @NotNull String branchToMerge, @NotNull String currentBranch, @NotNull GitRepository currentRepository,
                              @NotNull Map<GitRepository, String> currentRevisionsBeforeMerge,
                              @NotNull ProgressIndicator indicator) {
    super(project, repositories, indicator);
    myBranchToMerge = branchToMerge;
    myCurrentBranch = currentBranch;
    myCurrentRepository = currentRepository;
    myCurrentRevisionsBeforeMerge = currentRevisionsBeforeMerge;
    myChangeListManager = ChangeListManager.getInstance(myProject);
  }

  @Override
  protected void execute() {
    boolean fatalErrorHappened = false;
    int alreadyUpToDateRepositories = 0;
    while (hasMoreRepositories() && !fatalErrorHappened) {
      final GitRepository repository = next();

      VirtualFile root = repository.getRoot();
      GitMessageWithFilesDetector localChangesOverwrittenByMerge = new GitMessageWithFilesDetector(LOCAL_CHANGES_OVERWRITTEN_BY_MERGE, root);
      GitSimpleEventDetector unmergedFiles = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED_PREVENTING_MERGE);
      GitMessageWithFilesDetector untrackedOverwrittenByMerge = new GitMessageWithFilesDetector(UNTRACKED_FILES_OVERWRITTEN_BY, root);
      GitSimpleEventDetector mergeConflict = new GitSimpleEventDetector(GitSimpleEventDetector.Event.MERGE_CONFLICT);
      GitSimpleEventDetector alreadyUpToDateDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.ALREADY_UP_TO_DATE);

      GitCommandResult result = Git.merge(repository, myBranchToMerge,
                                          localChangesOverwrittenByMerge, unmergedFiles, untrackedOverwrittenByMerge, mergeConflict,
                                          alreadyUpToDateDetector);
      if (result.success()) {
        refresh(repository);
        markSuccessful(repository);
        if (alreadyUpToDateDetector.hasHappened()) {
          alreadyUpToDateRepositories += 1;
        }
      }
      else if (unmergedFiles.hasHappened()) {
        fatalUnmergedFilesError();
        fatalErrorHappened = true;
      }
      else if (localChangesOverwrittenByMerge.wasMessageDetected()) {
        boolean smartMergeSucceeded = proposeSmartMergePerformAndNotify(repository, localChangesOverwrittenByMerge);
        if (!smartMergeSucceeded) {
          fatalErrorHappened = true;
        }
      }
      else if (mergeConflict.hasHappened()) {
        myConflictedRepositories.put(repository, Boolean.FALSE);
        refresh(repository);
        markSuccessful(repository);
      }
      else if (untrackedOverwrittenByMerge.wasMessageDetected()) {
        fatalUntrackedFilesError(untrackedOverwrittenByMerge.getFiles());
        fatalErrorHappened = true;
      }
      else {
        fatalError(getCommonErrorTitle(), result.getErrorOutputAsJoinedString());
        fatalErrorHappened = true;
      }
    }

    boolean allConflictsResolved = resolveConflicts();

    if (!fatalErrorHappened && allConflictsResolved) {
      if (alreadyUpToDateRepositories < getRepositories().size()) {
        notifySuccess();
      }
      else {
        notifySuccess("Already up-to-date");
      }
    }

    restoreLocalChanges();
  }

  @Override
  protected void notifySuccess(@NotNull String message) {
    String description = message + "<br/><a href='delete'>Delete " + myBranchToMerge + "</a>";
    NotificationManager.getInstance(myProject).notify(GitVcs.NOTIFICATION_GROUP_ID, "", description, NotificationType.INFORMATION,
      new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equalsIgnoreCase("delete")) {
            new GitBranchOperationsProcessor(myProject, new ArrayList<GitRepository>(getRepositories()), myCurrentRepository).
              deleteBranch(myBranchToMerge);
          }
        }
    });
  }

  private boolean resolveConflicts() {
    if (!myConflictedRepositories.isEmpty()) {
      GitMerger merger = new GitMerger(myProject);
      GitConflictResolver.Params params = new GitConflictResolver.Params();
      GitMergeCommittingConflictResolver conflictResolver =
        new GitMergeCommittingConflictResolver(myProject, merger, GitUtil.getRoots(myConflictedRepositories.keySet()), params, true) {
          @Override
          protected void notifyUnresolvedRemain() {
            NotificationManager.getInstance(myProject).notify(
              GitVcs.IMPORTANT_ERROR_NOTIFICATION, "Merged branch " + myBranchToMerge + " with conflicts",
              "Unresolved conflicts remain in the project. <a href='resolve'>Resolve now.</a>", NotificationType.WARNING,
              getResolveLinkListener());
          }
        };
      return conflictResolver.merge();
    }
    return true;
  }

  private boolean proposeSmartMergePerformAndNotify(@NotNull GitRepository repository,
                                          @NotNull GitMessageWithFilesDetector localChangesOverwrittenByMerge) {
    Pair<List<GitRepository>, List<Change>> conflictingRepositoriesAndAffectedChanges =
      getConflictingRepositoriesAndAffectedChanges(repository, localChangesOverwrittenByMerge, myCurrentBranch, myBranchToMerge);
    List<GitRepository> allConflictingRepositories = conflictingRepositoriesAndAffectedChanges.getFirst();
    List<Change> affectedChanges = conflictingRepositoriesAndAffectedChanges.getSecond();

    int smartCheckoutDecision = GitSmartOperationDialog.showAndGetAnswer(myProject, affectedChanges, "merge", false);
    if (smartCheckoutDecision == GitSmartOperationDialog.SMART_EXIT_CODE) {
      return doSmartMerge(allConflictingRepositories);
    }
    else {
      fatalLocalChangesError(myBranchToMerge);
      return false;
    }
  }

  private void restoreLocalChanges() {
    if (myPreservingProcess != null) {
      myPreservingProcess.load();
    }
  }

  private boolean doSmartMerge(@NotNull final Collection<GitRepository> repositories) {
    final AtomicBoolean success = new AtomicBoolean();
    myPreservingProcess = new GitPreservingProcess(myProject, repositories, "merge", myBranchToMerge, getIndicator(),
      new Runnable() {
        @Override
        public void run() {
          success.set(doMerge(repositories));
        }
      });
    myPreservingProcess.execute(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return myConflictedRepositories.isEmpty();
      }
    });
    return success.get();
  }

  /**
   * Performs merge in the given repositories.
   * Handle only merge conflict situation: all other cases should have been handled before and are treated as errors.
   * Conflict is treated as a success: the repository with conflict is remembered and will be handled later along with all other conflicts.
   * If an error happens in one repository, the method doesn't go further in others, and shows a notification.
   *
   * @return true if merge has succeeded without errors (but possibly with conflicts) in all repositories;
   *         false if it failed at least in one of them.
   */
  private boolean doMerge(@NotNull Collection<GitRepository> repositories) {
    for (GitRepository repository : repositories) {
      GitSimpleEventDetector mergeConflict = new GitSimpleEventDetector(GitSimpleEventDetector.Event.MERGE_CONFLICT);
      GitCommandResult result = Git.merge(repository, myBranchToMerge, mergeConflict);
      if (!result.success()) {
        if (mergeConflict.hasHappened()) {
          myConflictedRepositories.put(repository, Boolean.TRUE);
          refresh(repository);
          markSuccessful(repository);
        }
        else {
          fatalError(getCommonErrorTitle(), result.getErrorOutputAsJoinedString());
          return false;
        }
      }
      else {
        refresh(repository);
        markSuccessful(repository);
      }
    }
    return true;
  }

  @NotNull
  private String getCommonErrorTitle() {
    return "Couldn't merge " + myBranchToMerge;
  }

  @Override
  protected void rollback() {
    Collection<GitRepository> repositoriesForSmartRollback = new ArrayList<GitRepository>();
    Collection<GitRepository> repositoriesForSimpleRollback = new ArrayList<GitRepository>();
    for (GitRepository repository : getSuccessfulRepositories()) {
      if (thereAreLocalChangesIn(repository)) {
        repositoriesForSmartRollback.add(repository);
      }
      else {
        repositoriesForSimpleRollback.add(repository);
      }
    }

    GitCompoundResult result = smartRollback(repositoriesForSmartRollback);
    for (GitRepository repository : repositoriesForSimpleRollback) {
      result.append(repository, rollback(repository));
    }

    if (!result.totalSuccess()) {
      NotificationManager.getInstance(myProject).notifyError("Error during rollback", result.getErrorOutputWithReposIndication());
    }
  }

  @NotNull
  private GitCompoundResult smartRollback(@NotNull final Collection<GitRepository> repositories) {
    final GitCompoundResult result = new GitCompoundResult(myProject);
    myPreservingProcess = new GitPreservingProcess(myProject, repositories, "merge", myBranchToMerge, getIndicator(),
      new Runnable() {
        @Override public void run() {
          for (GitRepository repository : repositories) {
            result.append(repository, rollback(repository));
          }
        }
      });
    myPreservingProcess.execute();
    return result;
  }

  @NotNull
  private GitCommandResult rollback(@NotNull GitRepository repository) {
    return Git.hardReset(repository, myCurrentRevisionsBeforeMerge.get(repository));
  }

  private boolean thereAreLocalChangesIn(@NotNull GitRepository repository) {
    return !myChangeListManager.getChangesIn(repository.getRoot()).isEmpty();
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    return String.format("Merged <b><code>%s</code></b> to <b><code>%s</code></b>", myBranchToMerge, myCurrentBranch);
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    return "However merge has succeeded for the following " + repositories() + ":<br/>" +
           successfulRepositoriesJoined() +
           "<br/>" + ROLLBACK_PROPOSAL;
  }

  @NotNull
  @Override
  protected String getOperationName() {
    return "merge";
  }

  private static void refresh(GitRepository... repositories) {
    for (GitRepository repository : repositories) {
      refreshRoot(repository);
      repository.update(GitRepository.TrackedTopic.ALL_CURRENT);
    }
  }

}
