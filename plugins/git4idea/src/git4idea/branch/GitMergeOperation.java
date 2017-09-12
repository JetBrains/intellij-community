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
package git4idea.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.*;
import git4idea.merge.GitMergeCommittingConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.repo.GitRepository;
import git4idea.reset.GitResetMode;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.GitUtil.HEAD;
import static git4idea.GitUtil.updateAndRefreshVfs;
import static git4idea.config.GitVcsSettings.UpdateChangesPolicy.STASH;

class GitMergeOperation extends GitBranchOperation {

  private static final Logger LOG = Logger.getInstance(GitMergeOperation.class);
  public static final String ROLLBACK_PROPOSAL = "You may rollback (reset to the commit before merging) not to let branches diverge.";

  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final String myBranchToMerge;
  private final GitBrancher.DeleteOnMergeOption myDeleteOnMerge;

  // true in value, if we've stashed local changes before merge and will need to unstash after resolving conflicts.
  @NotNull private final Map<GitRepository, Boolean> myConflictedRepositories = new HashMap<>();
  private GitPreservingProcess myPreservingProcess;

  GitMergeOperation(@NotNull Project project, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler,
                    @NotNull Collection<GitRepository> repositories,
                    @NotNull String branchToMerge, GitBrancher.DeleteOnMergeOption deleteOnMerge) {
    super(project, git, uiHandler, repositories);
    myBranchToMerge = branchToMerge;
    myDeleteOnMerge = deleteOnMerge;
    myChangeListManager = ChangeListManager.getInstance(myProject);
  }

  @Override
  protected void execute() {
    LOG.info("starting");
    saveAllDocuments();
    boolean fatalErrorHappened = false;
    int alreadyUpToDateRepositories = 0;
    AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
    try {
      while (hasMoreRepositories() && !fatalErrorHappened) {
        final GitRepository repository = next();
        LOG.info("next repository: " + repository);
        VirtualFile root = repository.getRoot();

        Collection<Change> changes = GitChangeUtils.getDiff(repository, HEAD, myBranchToMerge, false);

        GitLocalChangesWouldBeOverwrittenDetector localChangesDetector =
          new GitLocalChangesWouldBeOverwrittenDetector(root, GitLocalChangesWouldBeOverwrittenDetector.Operation.MERGE);
        GitSimpleEventDetector unmergedFiles = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED_PREVENTING_MERGE);
        GitUntrackedFilesOverwrittenByOperationDetector untrackedOverwrittenByMerge =
          new GitUntrackedFilesOverwrittenByOperationDetector(root);
        GitSimpleEventDetector mergeConflict = new GitSimpleEventDetector(GitSimpleEventDetector.Event.MERGE_CONFLICT);
        GitSimpleEventDetector alreadyUpToDateDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.ALREADY_UP_TO_DATE);

        GitCommandResult result = myGit.merge(repository, myBranchToMerge, Collections.emptyList(),
                                            localChangesDetector, unmergedFiles, untrackedOverwrittenByMerge, mergeConflict,
                                            alreadyUpToDateDetector);
        if (result.success()) {
          LOG.info("Merged successfully");
          updateAndRefreshVfs(repository, changes);
          markSuccessful(repository);
          if (alreadyUpToDateDetector.hasHappened()) {
            alreadyUpToDateRepositories += 1;
          }
        }
        else if (unmergedFiles.hasHappened()) {
          LOG.info("Unmerged files error!");
          fatalUnmergedFilesError();
          fatalErrorHappened = true;
        }
        else if (localChangesDetector.wasMessageDetected()) {
          LOG.info("Local changes would be overwritten by merge!");
          boolean smartMergeSucceeded = proposeSmartMergePerformAndNotify(repository, localChangesDetector);
          if (!smartMergeSucceeded) {
            fatalErrorHappened = true;
          }
        }
        else if (mergeConflict.hasHappened()) {
          LOG.info("Merge conflict");
          myConflictedRepositories.put(repository, Boolean.FALSE);
          updateAndRefreshVfs(repository);
          markSuccessful(repository);
        }
        else if (untrackedOverwrittenByMerge.wasMessageDetected()) {
          LOG.info("Untracked files would be overwritten by merge!");
          fatalUntrackedFilesError(repository.getRoot(), untrackedOverwrittenByMerge.getRelativeFilePaths());
          fatalErrorHappened = true;
        }
        else {
          LOG.info("Unknown error. " + result);
          fatalError(getCommonErrorTitle(), result.getErrorOutputAsJoinedString());
          fatalErrorHappened = true;
        }
      }

      if (fatalErrorHappened) {
        notifyAboutRemainingConflicts();
      }
      else {
        boolean allConflictsResolved = resolveConflicts();
        if (allConflictsResolved) {
          if (alreadyUpToDateRepositories < getRepositories().size()) {
            notifySuccess();
          }
          else {
            notifySuccess("Already up-to-date");
          }
        }
      }

      restoreLocalChanges();
    }
    finally {
      token.finish();
    }
  }

  private void notifyAboutRemainingConflicts() {
    if (!myConflictedRepositories.isEmpty()) {
      new MyMergeConflictResolver().notifyUnresolvedRemain();
    }
  }

  @Override
  protected void notifySuccess(@NotNull String message) {
    switch (myDeleteOnMerge) {
      case DELETE:
        super.notifySuccess(message);
        // bg process needs to be started from the EDT
        ApplicationManager.getApplication().invokeLater(() -> {
          GitBrancher brancher = GitBrancher.getInstance(myProject);
          brancher.deleteBranch(myBranchToMerge, new ArrayList<>(getRepositories()));
        });
        break;
      case PROPOSE:
        String description = message + "<br/><a href='delete'>Delete " + myBranchToMerge + "</a>";
        VcsNotifier.getInstance(myProject).notifySuccess("", description, new DeleteMergedLocalBranchNotificationListener());
        break;
      case NOTHING:
        super.notifySuccess(message);
        break;
    }
  }

  private boolean resolveConflicts() {
    if (!myConflictedRepositories.isEmpty()) {
      return new MyMergeConflictResolver().merge();
    }
    return true;
  }

  private boolean proposeSmartMergePerformAndNotify(@NotNull GitRepository repository,
                                          @NotNull GitMessageWithFilesDetector localChangesOverwrittenByMerge) {
    Pair<List<GitRepository>, List<Change>> conflictingRepositoriesAndAffectedChanges =
      getConflictingRepositoriesAndAffectedChanges(repository, localChangesOverwrittenByMerge, myCurrentHeads.get(repository),
                                                   myBranchToMerge);
    List<GitRepository> allConflictingRepositories = conflictingRepositoriesAndAffectedChanges.getFirst();
    List<Change> affectedChanges = conflictingRepositoriesAndAffectedChanges.getSecond();

    Collection<String> absolutePaths = GitUtil.toAbsolute(repository.getRoot(), localChangesOverwrittenByMerge.getRelativeFilePaths());
    GitSmartOperationDialog.Choice decision = myUiHandler.showSmartOperationDialog(myProject, affectedChanges, absolutePaths,
                                                                                   "merge", null);
    if (decision == GitSmartOperationDialog.Choice.SMART) {
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
    myPreservingProcess = new GitPreservingProcess(myProject, myGit, GitUtil.getRootsFromRepositories(repositories), "merge",
                                                   myBranchToMerge, STASH, getIndicator(),
                                                   () -> success.set(doMerge(repositories)));
    myPreservingProcess.execute(myConflictedRepositories::isEmpty);
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
      GitCommandResult result = myGit.merge(repository, myBranchToMerge, Collections.emptyList(), mergeConflict);
      if (!result.success()) {
        if (mergeConflict.hasHappened()) {
          myConflictedRepositories.put(repository, Boolean.TRUE);
          updateAndRefreshVfs(repository);
          markSuccessful(repository);
        }
        else {
          fatalError(getCommonErrorTitle(), result.getErrorOutputAsJoinedString());
          return false;
        }
      }
      else {
        updateAndRefreshVfs(repository);
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
    LOG.info("starting rollback...");
    Collection<GitRepository> repositoriesForSmartRollback = new ArrayList<>();
    Collection<GitRepository> repositoriesForSimpleRollback = new ArrayList<>();
    Collection<GitRepository> repositoriesForMergeRollback = new ArrayList<>();
    for (GitRepository repository : getSuccessfulRepositories()) {
      if (myConflictedRepositories.containsKey(repository)) {
        repositoriesForMergeRollback.add(repository);
      }
      else if (thereAreLocalChangesIn(repository)) {
        repositoriesForSmartRollback.add(repository);
      }
      else {
        repositoriesForSimpleRollback.add(repository);
      }
    }

    LOG.info("for smart rollback: " + DvcsUtil.getShortNames(repositoriesForSmartRollback) +
             "; for simple rollback: " + DvcsUtil.getShortNames(repositoriesForSimpleRollback) +
             "; for merge rollback: " + DvcsUtil.getShortNames(repositoriesForMergeRollback));

    GitCompoundResult result = smartRollback(repositoriesForSmartRollback);
    for (GitRepository repository : repositoriesForSimpleRollback) {
      result.append(repository, rollback(repository));
    }
    for (GitRepository repository : repositoriesForMergeRollback) {
      result.append(repository, rollbackMerge(repository));
    }
    myConflictedRepositories.clear();

    if (!result.totalSuccess()) {
      VcsNotifier.getInstance(myProject).notifyError("Error during rollback", result.getErrorOutputWithReposIndication());
    }
    LOG.info("rollback finished.");
  }

  @NotNull
  private GitCompoundResult smartRollback(@NotNull Collection<GitRepository> repositories) {
    LOG.info("Starting smart rollback...");
    final GitCompoundResult result = new GitCompoundResult(myProject);
    Collection<VirtualFile> roots = GitUtil.getRootsFromRepositories(repositories);
    GitPreservingProcess preservingProcess =
      new GitPreservingProcess(myProject, myGit, roots, "merge", myBranchToMerge, STASH, getIndicator(), () -> {
        for (GitRepository repository : repositories) result.append(repository, rollback(repository));
      });
    preservingProcess.execute();
    LOG.info("Smart rollback completed.");
    return result;
  }

  @NotNull
  private GitCommandResult rollback(@NotNull GitRepository repository) {
    return myGit.reset(repository, GitResetMode.HARD, getInitialRevision(repository));
  }

  @NotNull
  private GitCommandResult rollbackMerge(@NotNull GitRepository repository) {
    GitCommandResult result = myGit.resetMerge(repository, null);
    updateAndRefreshVfs(repository);
    return result;
  }

  private boolean thereAreLocalChangesIn(@NotNull GitRepository repository) {
    return !myChangeListManager.getChangesIn(repository.getRoot()).isEmpty();
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    return String.format("Merged <b><code>%s</code></b> to <b><code>%s</code></b>",
                         myBranchToMerge, stringifyBranchesByRepos(myCurrentHeads));
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

  private class MyMergeConflictResolver extends GitMergeCommittingConflictResolver {
    public MyMergeConflictResolver() {
      super(GitMergeOperation.this.myProject, myGit, new GitMerger(GitMergeOperation.this.myProject),
            GitUtil.getRootsFromRepositories(GitMergeOperation.this.myConflictedRepositories.keySet()), new Params(), true);
    }

    @Override
    protected void notifyUnresolvedRemain() {
      notifyWarning("Merged branch " + myBranchToMerge + " with conflicts",
                    "Unresolved conflicts remain in the project.");
    }
  }

  private class DeleteMergedLocalBranchNotificationListener extends NotificationListener.Adapter {
    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getDescription().equalsIgnoreCase("delete")) {
        notification.expire();
        GitBrancher.getInstance(myProject).deleteBranch(myBranchToMerge, new ArrayList<>(getRepositories()));
      }
    }
  }
}
