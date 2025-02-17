// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.BranchChangeListener;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitMessageWithFilesDetector;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.chooseNotNull;
import static git4idea.GitNotificationIdsHolder.BRANCH_OPERATION_SUCCESS;
import static git4idea.GitUtil.getRepositoryManager;

/**
 * Common class for Git operations with branches aware of multi-root configuration,
 * which means showing combined error information, proposing to rollback, etc.
 */
@ApiStatus.Internal
public abstract class GitBranchOperation {
  protected static final Logger LOG = Logger.getInstance(GitBranchOperation.class);

  protected final @NotNull Project myProject;
  protected final @NotNull Git myGit;
  protected final @NotNull GitBranchUiHandler myUiHandler;
  private final @NotNull Collection<GitRepository> myRepositories;
  protected final @NotNull Map<GitRepository, String> myCurrentHeads;
  protected final @NotNull Map<GitRepository, String> myInitialRevisions;
  private final @NotNull GitVcsSettings mySettings;

  private final @NotNull Collection<GitRepository> mySuccessfulRepositories;
  private final @NotNull Collection<GitRepository> mySkippedRepositories;
  private final @NotNull Collection<GitRepository> myRemainingRepositories;

  protected GitBranchOperation(@NotNull Project project, @NotNull Git git,
                               @NotNull GitBranchUiHandler uiHandler, @NotNull Collection<? extends GitRepository> repositories) {
    myProject = project;
    myGit = git;
    myUiHandler = uiHandler;

    myRepositories = getRepositoryManager(project).sortByDependency(repositories);
    myCurrentHeads =
      ContainerUtil.newMapFromKeys(repositories.iterator(), repo -> chooseNotNull(repo.getCurrentBranchName(), repo.getCurrentRevision()));
    myInitialRevisions = ContainerUtil.newMapFromKeys(repositories.iterator(), GitRepository::getCurrentRevision);
    mySuccessfulRepositories = new ArrayList<>();
    mySkippedRepositories = new ArrayList<>();
    myRemainingRepositories = new ArrayList<>(myRepositories);
    mySettings = GitVcsSettings.getInstance(myProject);
  }

  protected abstract void execute();

  protected abstract void rollback();

  protected abstract @NotNull @NlsContexts.NotificationContent String getSuccessMessage();

  protected abstract @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getRollbackProposal();

  /**
   * Returns a short downcased name of the operation.
   * It is used by some dialogs or notifications which are common to several operations.
   * Some operations (like checkout new branch) can be not mentioned in these dialogs, so their operation names would be not used.
   */
  protected abstract @NotNull @Nls String getOperationName();

  /**
   * @return next repository that wasn't handled (e.g. checked out) yet.
   */
  protected final @NotNull GitRepository next() {
    return myRemainingRepositories.iterator().next();
  }

  /**
   * @return true if there are more repositories on which the operation wasn't executed yet.
   */
  protected final boolean hasMoreRepositories() {
    return !myRemainingRepositories.isEmpty();
  }

  /**
   * Marks repositories as successful, i.e. they won't be handled again.
   */
  protected final void markSuccessful(GitRepository... repositories) {
    for (GitRepository repository : repositories) {
      mySuccessfulRepositories.add(repository);
      myRemainingRepositories.remove(repository);
    }
  }

  /**
   * Marks repositories as successful, i.e. they won't be handled again.
   */
  protected final void markSkip(GitRepository... repositories) {
    for (GitRepository repository : repositories) {
      mySkippedRepositories.add(repository);
      myRemainingRepositories.remove(repository);
    }
  }

  /**
   * @return true if the operation has already succeeded in at least one of repositories.
   */
  protected final boolean wereSuccessful() {
    return !mySuccessfulRepositories.isEmpty();
  }

  protected final boolean wereSkipped() {
    return !mySkippedRepositories.isEmpty();
  }

  protected final @NotNull Collection<GitRepository> getSuccessfulRepositories() {
    return mySuccessfulRepositories;
  }

  protected final @NotNull Collection<GitRepository> getSkippedRepositories() {
    return mySkippedRepositories;
  }

  protected final @NotNull @NlsSafe String successfulRepositoriesJoined() {
    return GitUtil.joinToHtml(mySuccessfulRepositories);
  }

  protected final @NotNull Collection<GitRepository> getRepositories() {
    return myRepositories;
  }

  protected final @NotNull List<GitRepository> getRemainingRepositoriesExceptGiven(final @NotNull GitRepository currentRepository) {
    List<GitRepository> repositories = new ArrayList<>(myRemainingRepositories);
    repositories.remove(currentRepository);
    return repositories;
  }

  protected void notifySuccess(@NotNull @NlsContexts.NotificationContent String message) {
    VcsNotifier.getInstance(myProject).notifySuccess(BRANCH_OPERATION_SUCCESS, "", message);
  }

  protected void notifySuccess() {
    notifySuccess(getSuccessMessage());
  }

  protected static void saveAllDocuments() {
    ApplicationManager.getApplication().invokeAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());
  }

  /**
   * Show fatal error as a notification or as a dialog with rollback proposal.
   */
  protected final void fatalError(@NotNull @NlsContexts.NotificationTitle String title,
                                  @NotNull @NlsContexts.NotificationContent String message) {
    if (wereSuccessful()) {
      showFatalErrorDialogWithRollback(title, message);
    }
    else {
      notifyError(title, message);
    }
  }

  protected final void fatalError(@NotNull @NlsContexts.NotificationTitle String title, @NotNull GitCommandResult result) {
    fatalError(title, result.getErrorOutputAsHtmlString());
  }

  protected final void showFatalErrorDialogWithRollback(@NotNull @NlsContexts.DialogTitle String title,
                                                        @NotNull @NlsContexts.DialogMessage String message) {
    boolean rollback = myUiHandler.notifyErrorWithRollbackProposal(title, message, getRollbackProposal());
    if (rollback) {
      rollback();
    }
  }

  protected final void notifyError(@NotNull @NlsContexts.NotificationTitle String title,
                                   @NotNull @NlsContexts.NotificationContent String message) {
    myUiHandler.notifyError(title, message);
  }

  protected final @NotNull ProgressIndicator getIndicator() {
    return myUiHandler.getProgressIndicator();
  }

  /**
   * Display the error saying that the operation can't be performed because there are unmerged files in a repository.
   * Such error prevents checking out and creating new branch.
   */
  protected final void fatalUnmergedFilesError() {
    if (wereSuccessful()) {
      showUnmergedFilesDialogWithRollback();
    }
    else {
      showUnmergedFilesNotification();
    }
  }

  /**
   * Updates the recently visited branch in the settings.
   * This is to be performed after successful checkout operation.
   */
  protected final void updateRecentBranch() {
    if (getRepositories().size() == 1) {
      GitRepository repository = myRepositories.iterator().next();
      String currentHead = myCurrentHeads.get(repository);
      if (currentHead != null) {
        mySettings.setRecentBranchOfRepository(repository.getRoot().getPath(), currentHead);
      }
      else {
        LOG.error("Current head is not known for " + repository.getRoot().getPath());
      }
    }
    else {
      String recentCommonBranch = getRecentCommonBranch();
      if (recentCommonBranch != null) {
        mySettings.setRecentCommonBranch(recentCommonBranch);
      }
    }
  }

  protected final void notifyBranchWillChange() {
    String currentBranch = ContainerUtil.getFirstItem(myCurrentHeads.values());
    if (currentBranch != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myProject.isDisposed()) return;
        myProject.getMessageBus().syncPublisher(BranchChangeListener.VCS_BRANCH_CHANGED).branchWillChange(currentBranch);
      });
    }
  }

  protected final void notifyBranchHasChanged(@Nullable String branchName) {
    if (branchName != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myProject.isDisposed()) return;
        myProject.getMessageBus().syncPublisher(BranchChangeListener.VCS_BRANCH_CHANGED).branchHasChanged(branchName);
      });
    }
  }

  /**
   * Returns the hash of the revision which was current before the start of this GitBranchOperation.
   */
  protected final @NotNull String getInitialRevision(@NotNull GitRepository repository) {
    return myInitialRevisions.get(repository);
  }

  private @Nullable String getRecentCommonBranch() {
    String recentCommonBranch = null;
    for (String branch : myCurrentHeads.values()) {
      if (recentCommonBranch == null) {
        recentCommonBranch = branch;
      }
      else if (!recentCommonBranch.equals(branch)) {
        return null;
      }
    }
    return recentCommonBranch;
  }

  private void showUnmergedFilesDialogWithRollback() {
    boolean ok = myUiHandler.showUnmergedFilesMessageWithRollback(getOperationName(), getRollbackProposal());
    if (ok) {
      rollback();
    }
  }

  private void showUnmergedFilesNotification() {
    myUiHandler.showUnmergedFilesNotification(getOperationName(), getRepositories());
  }

  protected final void fatalLocalChangesError(@NotNull String reference) {
    String title = GitBundle.message("branch.operation.could.not.0.operation.name.1.reference", getOperationName(), reference);
    if (wereSuccessful()) {
      showFatalErrorDialogWithRollback(title, "");
    }
  }

  /**
   * Shows the error "The following untracked working tree files would be overwritten by checkout/merge".
   * If there were no repositories that succeeded the operation, shows a notification with a link to the list of these untracked files.
   * If some repositories succeeded, shows a dialog with the list of these files and a proposal to rollback the operation of those
   * repositories.
   */
  protected final void fatalUntrackedFilesError(@NotNull VirtualFile root, @NotNull Collection<String> relativePaths) {
    String operationName = getOperationName();
    if (wereSuccessful()) {
      boolean ok = myUiHandler.showUntrackedFilesDialogWithRollback(operationName, getRollbackProposal(), root, relativePaths);
      if (ok) {
        rollback();
      }
    }
    else {
      myUiHandler.showUntrackedFilesNotification(operationName, root, relativePaths);
    }
  }

  /**
   * For each of the given repositories looks to the diff between current branch and the given branch and converts it to the list of
   * local changes.
   */
  private @NotNull Map<GitRepository, List<Change>> collectLocalChangesConflictingWithBranch(@NotNull Collection<? extends GitRepository> repositories,
                                                                                             @NotNull String otherBranch) {
    Map<GitRepository, List<Change>> changes = new HashMap<>();
    for (GitRepository repository : repositories) {
      Collection<Change> diffWithWorkingTree = GitChangeUtils.getDiffWithWorkingTree(repository, otherBranch, false);
      if (diffWithWorkingTree != null) {
        List<String> diff = ChangesUtil.iteratePaths(diffWithWorkingTree).map(FilePath::getPath).toList();
        List<Change> changesInRepo = GitUtil.findLocalChangesForPaths(myProject, repository.getRoot(), diff, false);
        if (!changesInRepo.isEmpty()) {
          changes.put(repository, changesInRepo);
        }
      }
    }
    return changes;
  }

  /**
   * When checkout or merge operation on a repository fails with the error "local changes would be overwritten by...",
   * affected local files are captured by the {@link GitMessageWithFilesDetector detector}.
   * Then all remaining (non successful repositories) are searched if they are about to fail with the same problem.
   * All collected local changes which prevent the operation, together with these repositories, are returned.
   * @param currentRepository          The first repository which failed the operation.
   * @param localChangesOverwrittenBy  The detector of local changes would be overwritten by merge/checkout.
   * @param currentBranch              Current branch.
   * @param nextBranch                 Branch to compare with (the branch to be checked out, or the branch to be merged).
   * @return Repositories that have failed or would fail with the "local changes" error, together with these local changes.
   */
  protected final @NotNull Pair<List<GitRepository>, List<Change>> getConflictingRepositoriesAndAffectedChanges(
    @NotNull GitRepository currentRepository, @NotNull GitMessageWithFilesDetector localChangesOverwrittenBy,
    String currentBranch, String nextBranch) {

    // get changes overwritten by checkout from the error message captured from Git
    List<Change> affectedChanges = GitUtil.findLocalChangesForPaths(myProject, currentRepository.getRoot(),
                                                                    localChangesOverwrittenBy.getRelativeFilePaths(), true
    );
    // get all other conflicting changes
    // get changes in all other repositories (except those which already have succeeded) to avoid multiple dialogs proposing smart checkout
    Map<GitRepository, List<Change>> conflictingChangesInRepositories =
      collectLocalChangesConflictingWithBranch(getRemainingRepositoriesExceptGiven(currentRepository), nextBranch);

    Set<GitRepository> otherProblematicRepositories = conflictingChangesInRepositories.keySet();
    List<GitRepository> allConflictingRepositories = new ArrayList<>(otherProblematicRepositories);
    allConflictingRepositories.add(currentRepository);
    for (List<Change> changes : conflictingChangesInRepositories.values()) {
      affectedChanges.addAll(changes);
    }

    return Pair.create(allConflictingRepositories, affectedChanges);
  }

  protected static @NotNull String stringifyBranchesByRepos(@NotNull Map<GitRepository, String> heads) {
    MultiMap<String, VirtualFile> grouped = groupByBranches(heads);
    if (grouped.size() == 1) {
      return grouped.keySet().iterator().next();
    }
    return StringUtil.join(grouped.entrySet(), entry -> {
      String roots = StringUtil.join(entry.getValue(), file -> file.getName(), ", ");
      return GitBundle.message("branch.operation.in", entry.getKey(), roots);
    }, UIUtil.BR);
  }

  private static @NotNull MultiMap<String, VirtualFile> groupByBranches(@NotNull Map<GitRepository, String> heads) {
    MultiMap<String, VirtualFile> result = MultiMap.createLinked();
    List<GitRepository> sortedRepos = DvcsUtil.sortRepositories(heads.keySet());
    for (GitRepository repo : sortedRepos) {
      result.putValue(heads.get(repo), repo.getRoot());
    }
    return result;
  }
}
