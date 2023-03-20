// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import git4idea.GitUtil;
import git4idea.commands.*;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeCommittingConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.repo.GitRepository;
import git4idea.reset.GitResetMode;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.GitNotificationIdsHolder.DELETE_BRANCH_ON_MERGE;
import static git4idea.GitNotificationIdsHolder.MERGE_ROLLBACK_ERROR;
import static git4idea.GitUtil.getHead;
import static git4idea.GitUtil.updateAndRefreshChangedVfs;
import static git4idea.util.GitUIUtil.bold;
import static git4idea.util.GitUIUtil.code;

class GitMergeOperation extends GitBranchOperation {

  private static final Logger LOG = Logger.getInstance(GitMergeOperation.class);
  @NotNull private static final String DELETE_HREF_ATTRIBUTE = "delete";

  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final @NlsSafe String myBranchToMerge;
  private final GitBrancher.DeleteOnMergeOption myDeleteOnMerge;

  // true in value, if we've stashed local changes before merge and will need to unstash after resolving conflicts.
  @NotNull private final Map<GitRepository, Boolean> myConflictedRepositories = new HashMap<>();
  private GitPreservingProcess myPreservingProcess;

  GitMergeOperation(@NotNull Project project, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler,
                    @NotNull Collection<? extends GitRepository> repositories,
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
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, getOperationName())) {
      while (hasMoreRepositories() && !fatalErrorHappened) {
        final GitRepository repository = next();
        LOG.info("next repository: " + repository);
        VirtualFile root = repository.getRoot();

        Hash startHash = getHead(repository);

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
          updateAndRefreshChangedVfs(repository, startHash);
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
          updateAndRefreshChangedVfs(repository, startHash);
          markSuccessful(repository);
        }
        else if (untrackedOverwrittenByMerge.wasMessageDetected()) {
          LOG.info("Untracked files would be overwritten by merge!");
          fatalUntrackedFilesError(repository.getRoot(), untrackedOverwrittenByMerge.getRelativeFilePaths());
          fatalErrorHappened = true;
        }
        else {
          LOG.info("Unknown error. " + result);
          fatalError(getCommonErrorTitle(), result);
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
            notifySuccess(GitBundle.message("merge.operation.already.up.to.date"));
          }
        }
      }

      restoreLocalChanges();
    }
  }

  private void notifyAboutRemainingConflicts() {
    if (!myConflictedRepositories.isEmpty()) {
      new MyMergeConflictResolver().notifyUnresolvedRemain();
    }
  }

  @Override
  protected void notifySuccess(@NotNull @Nls String message) {
    switch (myDeleteOnMerge) {
      case DELETE -> {
        super.notifySuccess(message);
        new GitBranchWorker(myProject, myGit, myUiHandler).deleteBranch(myBranchToMerge, new ArrayList<>(getRepositories()));
      }
      case PROPOSE -> {
        String deleteBranch = GitBundle.message("merge.operation.delete.branch", myBranchToMerge);
        String description = new HtmlBuilder().appendRaw(message).br().appendLink(DELETE_HREF_ATTRIBUTE, deleteBranch).toString();
        VcsNotifier.getInstance(myProject).notifySuccess(DELETE_BRANCH_ON_MERGE,
                                                         "",
                                                         description,
                                                         new DeleteMergedLocalBranchNotificationListener());
      }
      case NOTHING -> super.notifySuccess(message);
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
                                                                                   GitBundle.message("merge.operation.name"), null);
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

  private boolean doSmartMerge(@NotNull final Collection<? extends GitRepository> repositories) {
    final AtomicBoolean success = new AtomicBoolean();
    GitSaveChangesPolicy saveMethod = GitVcsSettings.getInstance(myProject).getSaveChangesPolicy();
    myPreservingProcess = new GitPreservingProcess(myProject, myGit, GitUtil.getRootsFromRepositories(repositories),
                                                   GitBundle.message("merge.operation.name"),
                                                   myBranchToMerge, saveMethod, getIndicator(),
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
  private boolean doMerge(@NotNull Collection<? extends GitRepository> repositories) {
    for (GitRepository repository : repositories) {
      Hash startHash = getHead(repository);
      GitSimpleEventDetector mergeConflict = new GitSimpleEventDetector(GitSimpleEventDetector.Event.MERGE_CONFLICT);
      GitCommandResult result = myGit.merge(repository, myBranchToMerge, Collections.emptyList(), mergeConflict);
      if (!result.success()) {
        if (mergeConflict.hasHappened()) {
          myConflictedRepositories.put(repository, Boolean.TRUE);
          updateAndRefreshChangedVfs(repository, startHash);
          markSuccessful(repository);
        }
        else {
          fatalError(getCommonErrorTitle(), result);
          return false;
        }
      }
      else {
        updateAndRefreshChangedVfs(repository, startHash);
        markSuccessful(repository);
      }
    }
    return true;
  }

  @NotNull
  @NlsContexts.NotificationTitle
  private String getCommonErrorTitle() {
    return GitBundle.message("merge.operation.could.not.merge.branch", myBranchToMerge);
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
      VcsNotifier.getInstance(myProject).notifyError(MERGE_ROLLBACK_ERROR,
                                                     GitBundle.message("merge.operation.error.during.rollback"),
                                                     result.getErrorOutputWithReposIndication(),
                                                     true);
    }
    LOG.info("rollback finished.");
  }

  @NotNull
  private GitCompoundResult smartRollback(@NotNull Collection<? extends GitRepository> repositories) {
    LOG.info("Starting smart rollback...");
    final GitCompoundResult result = new GitCompoundResult(myProject);
    Collection<VirtualFile> roots = GitUtil.getRootsFromRepositories(repositories);
    GitSaveChangesPolicy saveMethod = GitVcsSettings.getInstance(myProject).getSaveChangesPolicy();
    GitPreservingProcess preservingProcess =
      new GitPreservingProcess(myProject, myGit, roots, GitBundle.message("merge.operation.name"), myBranchToMerge, saveMethod,
                               getIndicator(), () -> {
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
    Hash startHash = getHead(repository);
    GitCommandResult result = myGit.resetMerge(repository, null);
    updateAndRefreshChangedVfs(repository, startHash);
    return result;
  }

  private boolean thereAreLocalChangesIn(@NotNull GitRepository repository) {
    return !myChangeListManager.getChangesIn(repository.getRoot()).isEmpty();
  }

  @NotNull
  @Override
  protected String getSuccessMessage() {
    return GitBundle
      .message("merge.operation.merged.to", bold(code(myBranchToMerge)), bold(code(stringifyBranchesByRepos(myCurrentHeads))));
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    return new HtmlBuilder()
      .append(
        GitBundle.message("merge.operation.however.merge.has.succeeded.for.the.following.repositories", getSuccessfulRepositories().size()))
      .br()
      .appendRaw(successfulRepositoriesJoined())
      .br()
      .append(GitBundle.message("merge.operation.you.may.rollback.not.to.let.branches.diverge")).toString();  
  }

  @NotNull
  @Nls
  @Override
  protected String getOperationName() {
    return GitBundle.message("merge.operation.name");
  }

  private class MyMergeConflictResolver extends GitMergeCommittingConflictResolver {
    MyMergeConflictResolver() {
      super(GitMergeOperation.this.myProject, myGit, new GitMerger(GitMergeOperation.this.myProject),
            GitUtil.getRootsFromRepositories(GitMergeOperation.this.myConflictedRepositories.keySet()), new Params(GitMergeOperation.this.myProject), true);
    }

    @Override
    protected void notifyUnresolvedRemain() {
      notifyWarning(GitBundle.message("merge.operation.branch.merged.with.conflicts", myBranchToMerge), "");
    }
  }

  private class DeleteMergedLocalBranchNotificationListener extends NotificationListener.Adapter {
    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getDescription().equalsIgnoreCase(DELETE_HREF_ATTRIBUTE)) {
        notification.expire();
        GitBrancher.getInstance(myProject).deleteBranch(myBranchToMerge, new ArrayList<>(getRepositories()));
      }
    }
  }
}
