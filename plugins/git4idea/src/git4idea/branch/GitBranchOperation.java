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

import com.google.common.collect.Maps;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitMessageWithFilesDetector;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static git4idea.GitUtil.getRepositoryManager;

/**
 * Common class for Git operations with branches aware of multi-root configuration,
 * which means showing combined error information, proposing to rollback, etc.
 */
abstract class GitBranchOperation {

  protected static final Logger LOG = Logger.getInstance(GitBranchOperation.class);

  @NotNull protected final Project myProject;
  @NotNull protected final Git myGit;
  @NotNull protected final GitBranchUiHandler myUiHandler;
  @NotNull private final Collection<GitRepository> myRepositories;
  @NotNull protected final Map<GitRepository, String> myCurrentHeads;
  @NotNull protected final Map<GitRepository, String> myInitialRevisions;
  @NotNull private final GitVcsSettings mySettings;

  @NotNull private final Collection<GitRepository> mySuccessfulRepositories;
  @NotNull private final Collection<GitRepository> mySkippedRepositories;
  @NotNull private final Collection<GitRepository> myRemainingRepositories;

  protected GitBranchOperation(@NotNull Project project, @NotNull Git git,
                               @NotNull GitBranchUiHandler uiHandler, @NotNull Collection<GitRepository> repositories) {
    myProject = project;
    myGit = git;
    myUiHandler = uiHandler;

    myRepositories = getRepositoryManager(project).sortByDependency(repositories);
    myCurrentHeads = Maps.toMap(repositories, repo -> chooseNotNull(repo.getCurrentBranchName(), repo.getCurrentRevision()));
    myInitialRevisions = Maps.toMap(repositories, GitRepository::getCurrentRevision);
    mySuccessfulRepositories = new ArrayList<>();
    mySkippedRepositories = new ArrayList<>();
    myRemainingRepositories = new ArrayList<>(myRepositories);
    mySettings = GitVcsSettings.getInstance(myProject);
  }

  protected abstract void execute();

  protected abstract void rollback();

  @NotNull
  public abstract String getSuccessMessage();

  @NotNull
  protected abstract String getRollbackProposal();

  /**
   * Returns a short downcased name of the operation.
   * It is used by some dialogs or notifications which are common to several operations.
   * Some operations (like checkout new branch) can be not mentioned in these dialogs, so their operation names would be not used.
   */
  @NotNull
  protected abstract String getOperationName();

  /**
   * @return next repository that wasn't handled (e.g. checked out) yet.
   */
  @NotNull
  protected GitRepository next() {
    return myRemainingRepositories.iterator().next();
  }

  /**
   * @return true if there are more repositories on which the operation wasn't executed yet.
   */
  protected boolean hasMoreRepositories() {
    return !myRemainingRepositories.isEmpty();
  }

  /**
   * Marks repositories as successful, i.e. they won't be handled again.
   */
  protected void markSuccessful(GitRepository... repositories) {
    for (GitRepository repository : repositories) {
      mySuccessfulRepositories.add(repository);
      myRemainingRepositories.remove(repository);
    }
  }

  /**
   * Marks repositories as successful, i.e. they won't be handled again.
   */
  protected void markSkip(GitRepository... repositories) {
    for (GitRepository repository : repositories) {
      mySkippedRepositories.add(repository);
      myRemainingRepositories.remove(repository);
    }
  }

  /**
   * @return true if the operation has already succeeded in at least one of repositories.
   */
  protected boolean wereSuccessful() {
    return !mySuccessfulRepositories.isEmpty();
  }

  protected boolean wereSkipped() {
    return !mySkippedRepositories.isEmpty();
  }
  
  @NotNull
  protected Collection<GitRepository> getSuccessfulRepositories() {
    return mySuccessfulRepositories;
  }

  @NotNull
  protected Collection<GitRepository> getSkippedRepositories() {
    return mySkippedRepositories;
  }

  @NotNull
  protected String successfulRepositoriesJoined() {
    return GitUtil.joinToHtml(mySuccessfulRepositories);
  }
  
  @NotNull
  protected Collection<GitRepository> getRepositories() {
    return myRepositories;
  }

  @NotNull
  protected Collection<GitRepository> getRemainingRepositories() {
    return myRemainingRepositories;
  }

  @NotNull
  protected List<GitRepository> getRemainingRepositoriesExceptGiven(@NotNull final GitRepository currentRepository) {
    List<GitRepository> repositories = new ArrayList<>(myRemainingRepositories);
    repositories.remove(currentRepository);
    return repositories;
  }

  protected void notifySuccess(@NotNull String message) {
    VcsNotifier.getInstance(myProject).notifySuccess(message);
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
  protected void fatalError(@NotNull String title, @NotNull String message) {
    if (wereSuccessful())  {
      showFatalErrorDialogWithRollback(title, message);
    }
    else {
      showFatalNotification(title, message);
    }
  }

  protected void showFatalErrorDialogWithRollback(@NotNull final String title, @NotNull final String message) {
    boolean rollback = myUiHandler.notifyErrorWithRollbackProposal(title, message, getRollbackProposal());
    if (rollback) {
      rollback();
    }
  }

  protected void showFatalNotification(@NotNull String title, @NotNull String message) {
    notifyError(title, message);
  }

  protected void notifyError(@NotNull String title, @NotNull String message) {
    VcsNotifier.getInstance(myProject).notifyError(title, message);
  }

  @NotNull
  protected ProgressIndicator getIndicator() {
    return myUiHandler.getProgressIndicator();
  }

  /**
   * Display the error saying that the operation can't be performed because there are unmerged files in a repository.
   * Such error prevents checking out and creating new branch.
   */
  protected void fatalUnmergedFilesError() {
    if (wereSuccessful()) {
      showUnmergedFilesDialogWithRollback();
    }
    else {
      showUnmergedFilesNotification();
    }
  }

  @NotNull
  protected String repositories() {
    return pluralize("repository", getSuccessfulRepositories().size());
  }

  /**
   * Updates the recently visited branch in the settings.
   * This is to be performed after successful checkout operation.
   */
  protected void updateRecentBranch() {
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

  /**
   * Returns the hash of the revision which was current before the start of this GitBranchOperation.
   */
  @NotNull
  protected String getInitialRevision(@NotNull GitRepository repository) {
    return myInitialRevisions.get(repository);
  }

  @Nullable
  private String getRecentCommonBranch() {
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

  protected void fatalLocalChangesError(@NotNull String reference) {
    String title = String.format("Couldn't %s %s", getOperationName(), reference);
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
  protected void fatalUntrackedFilesError(@NotNull VirtualFile root, @NotNull Collection<String> relativePaths) {
    if (wereSuccessful()) {
      showUntrackedFilesDialogWithRollback(root, relativePaths);
    }
    else {
      showUntrackedFilesNotification(root, relativePaths);
    }
  }

  private void showUntrackedFilesNotification(@NotNull VirtualFile root, @NotNull Collection<String> relativePaths) {
    myUiHandler.showUntrackedFilesNotification(getOperationName(), root, relativePaths);
  }

  private void showUntrackedFilesDialogWithRollback(@NotNull VirtualFile root, @NotNull Collection<String> relativePaths) {
    boolean ok = myUiHandler.showUntrackedFilesDialogWithRollback(getOperationName(), getRollbackProposal(), root, relativePaths);
    if (ok) {
      rollback();
    }
  }

  /**
   * TODO this is non-optimal and even incorrect, since such diff shows the difference between committed changes
   * For each of the given repositories looks to the diff between current branch and the given branch and converts it to the list of
   * local changes.
   */
  @NotNull
  Map<GitRepository, List<Change>> collectLocalChangesConflictingWithBranch(@NotNull Collection<GitRepository> repositories,
                                                                            @NotNull String currentBranch, @NotNull String otherBranch) {
    Map<GitRepository, List<Change>> changes = new HashMap<>();
    for (GitRepository repository : repositories) {
      try {
        Collection<String> diff = GitUtil.getPathsDiffBetweenRefs(myGit, repository, currentBranch, otherBranch);
        List<Change> changesInRepo = GitUtil.findLocalChangesForPaths(myProject, repository.getRoot(), diff, false);
        if (!changesInRepo.isEmpty()) {
          changes.put(repository, changesInRepo);
        }
      }
      catch (VcsException e) {
        // ignoring the exception: this is not fatal if we won't collect such a diff from other repositories.
        // At worst, use will get double dialog proposing the smart checkout.
        LOG.warn(String.format("Couldn't collect diff between %s and %s in %s", currentBranch, otherBranch, repository.getRoot()), e);
      }
    }
    return changes;
  }

  /**
   * When checkout or merge operation on a repository fails with the error "local changes would be overwritten by...",
   * affected local files are captured by the {@link git4idea.commands.GitMessageWithFilesDetector detector}.
   * Then all remaining (non successful repositories) are searched if they are about to fail with the same problem.
   * All collected local changes which prevent the operation, together with these repositories, are returned.
   * @param currentRepository          The first repository which failed the operation.
   * @param localChangesOverwrittenBy  The detector of local changes would be overwritten by merge/checkout.
   * @param currentBranch              Current branch.
   * @param nextBranch                 Branch to compare with (the branch to be checked out, or the branch to be merged).
   * @return Repositories that have failed or would fail with the "local changes" error, together with these local changes.
   */
  @NotNull
  protected Pair<List<GitRepository>, List<Change>> getConflictingRepositoriesAndAffectedChanges(
    @NotNull GitRepository currentRepository, @NotNull GitMessageWithFilesDetector localChangesOverwrittenBy,
    String currentBranch, String nextBranch) {

    // get changes overwritten by checkout from the error message captured from Git
    List<Change> affectedChanges = GitUtil.findLocalChangesForPaths(myProject, currentRepository.getRoot(),
                                                                    localChangesOverwrittenBy.getRelativeFilePaths(), true
    );
    // get all other conflicting changes
    // get changes in all other repositories (except those which already have succeeded) to avoid multiple dialogs proposing smart checkout
    Map<GitRepository, List<Change>> conflictingChangesInRepositories =
      collectLocalChangesConflictingWithBranch(getRemainingRepositoriesExceptGiven(currentRepository), currentBranch, nextBranch);

    Set<GitRepository> otherProblematicRepositories = conflictingChangesInRepositories.keySet();
    List<GitRepository> allConflictingRepositories = new ArrayList<>(otherProblematicRepositories);
    allConflictingRepositories.add(currentRepository);
    for (List<Change> changes : conflictingChangesInRepositories.values()) {
      affectedChanges.addAll(changes);
    }

    return Pair.create(allConflictingRepositories, affectedChanges);
  }

  @NotNull
  protected static String stringifyBranchesByRepos(@NotNull Map<GitRepository, String> heads) {
    MultiMap<String, VirtualFile> grouped = groupByBranches(heads);
    if (grouped.size() == 1) {
      return grouped.keySet().iterator().next();
    }
    return StringUtil.join(grouped.entrySet(), entry -> {
      String roots = StringUtil.join(entry.getValue(), file -> file.getName(), ", ");
      return entry.getKey() + " (in " + roots + ")";
    }, "<br/>");
  }

  @NotNull
  private static MultiMap<String, VirtualFile> groupByBranches(@NotNull Map<GitRepository, String> heads) {
    MultiMap<String, VirtualFile> result = MultiMap.createLinked();
    List<GitRepository> sortedRepos = DvcsUtil.sortRepositories(heads.keySet());
    for (GitRepository repo : sortedRepos) {
      result.putValue(heads.get(repo), repo.getRoot());
    }
    return result;
  }

}
