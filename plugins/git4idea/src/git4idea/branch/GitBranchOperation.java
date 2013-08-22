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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitMessageWithFilesDetector;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.pluralize;

/**
 * Common class for Git operations with branches aware of multi-root configuration,
 * which means showing combined error information, proposing to rollback, etc.
 *
 * @author Kirill Likhodedov
 */
abstract class GitBranchOperation {

  protected static final Logger LOG = Logger.getInstance(GitBranchOperation.class);

  @NotNull protected final Project myProject;
  @NotNull protected final GitPlatformFacade myFacade;
  @NotNull protected final Git myGit;
  @NotNull protected final GitBranchUiHandler myUiHandler;
  @NotNull private final Collection<GitRepository> myRepositories;
  @NotNull protected final String myCurrentBranchOrRev;
  private final GitVcsSettings mySettings;

  @NotNull private final Collection<GitRepository> mySuccessfulRepositories;
  @NotNull private final Collection<GitRepository> myRemainingRepositories;

  protected GitBranchOperation(@NotNull Project project, @NotNull GitPlatformFacade facade, @NotNull Git git,
                               @NotNull GitBranchUiHandler uiHandler, @NotNull Collection<GitRepository> repositories) {
    myProject = project;
    myFacade = facade;
    myGit = git;
    myUiHandler = uiHandler;
    myRepositories = repositories;
    myCurrentBranchOrRev = GitBranchUtil.getCurrentBranchOrRev(repositories);
    mySuccessfulRepositories = new ArrayList<GitRepository>();
    myRemainingRepositories = new ArrayList<GitRepository>(myRepositories);
    mySettings = myFacade.getSettings(myProject);
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
   * @return true if the operation has already succeeded in at least one of repositories.
   */
  protected boolean wereSuccessful() {
    return !mySuccessfulRepositories.isEmpty();
  }
  
  @NotNull
  protected Collection<GitRepository> getSuccessfulRepositories() {
    return mySuccessfulRepositories;
  }
  
  @NotNull
  protected String successfulRepositoriesJoined() {
    return StringUtil.join(mySuccessfulRepositories, new Function<GitRepository, String>() {
      @Override
      public String fun(GitRepository repository) {
        return repository.getPresentableUrl();
      }
    }, "<br/>");
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
    List<GitRepository> repositories = new ArrayList<GitRepository>(myRemainingRepositories);
    repositories.remove(currentRepository);
    return repositories;
  }

  protected void notifySuccess(@NotNull String message) {
    myUiHandler.notifySuccess(message);
  }

  protected final void notifySuccess() {
    notifySuccess(getSuccessMessage());
  }

  protected final void saveAllDocuments() {
    myFacade.saveAllDocuments();
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
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(title + ": " + message);
    }
    else {
      myUiHandler.notifyError(title, message);
    }
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
      mySettings.setRecentBranchOfRepository(repository.getRoot().getPath(), myCurrentBranchOrRev);
    }
    else {
      mySettings.setRecentCommonBranch(myCurrentBranchOrRev);
    }
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

  /**
   * Asynchronously refreshes the VFS root directory of the given repository.
   */
  protected void refreshRoot(@NotNull GitRepository repository) {
    // marking all files dirty, because sometimes FileWatcher is unable to process such a large set of changes that can happen during
    // checkout on a large repository: IDEA-89944
    myFacade.hardRefresh(repository.getRoot());
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
  protected void fatalUntrackedFilesError(@NotNull Collection<VirtualFile> untrackedFiles) {
    if (wereSuccessful()) {
      showUntrackedFilesDialogWithRollback(untrackedFiles);
    }
    else {
      showUntrackedFilesNotification(untrackedFiles);
    }
  }

  private void showUntrackedFilesNotification(@NotNull Collection<VirtualFile> untrackedFiles) {
    myUiHandler.showUntrackedFilesNotification(getOperationName(), untrackedFiles);
  }

  private void showUntrackedFilesDialogWithRollback(@NotNull Collection<VirtualFile> untrackedFiles) {
    boolean ok = myUiHandler.showUntrackedFilesDialogWithRollback(getOperationName(), getRollbackProposal(), untrackedFiles);
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
    Map<GitRepository, List<Change>> changes = new HashMap<GitRepository, List<Change>>();
    for (GitRepository repository : repositories) {
      try {
        Collection<String> diff = GitUtil.getPathsDiffBetweenRefs(myGit, repository, currentBranch, otherBranch);
        List<Change> changesInRepo = convertPathsToChanges(repository, diff, false);
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
    List<Change> affectedChanges = convertPathsToChanges(currentRepository, localChangesOverwrittenBy.getRelativeFilePaths(), true);
    // get all other conflicting changes
    // get changes in all other repositories (except those which already have succeeded) to avoid multiple dialogs proposing smart checkout
    Map<GitRepository, List<Change>> conflictingChangesInRepositories =
      collectLocalChangesConflictingWithBranch(getRemainingRepositoriesExceptGiven(currentRepository), currentBranch, nextBranch);

    Set<GitRepository> otherProblematicRepositories = conflictingChangesInRepositories.keySet();
    List<GitRepository> allConflictingRepositories = new ArrayList<GitRepository>(otherProblematicRepositories);
    allConflictingRepositories.add(currentRepository);
    for (List<Change> changes : conflictingChangesInRepositories.values()) {
      affectedChanges.addAll(changes);
    }

    return Pair.create(allConflictingRepositories, affectedChanges);
  }

  /**
   * Given the list of paths converts them to the list of {@link com.intellij.openapi.vcs.changes.Change Changes} found in the {@link com.intellij.openapi.vcs.changes.ChangeListManager},
   * i.e. this works only for local changes.
   * Paths can be absolute or relative to the repository.
   * If a path is not in the local changes, it is ignored.
   */
  @NotNull
  private List<Change> convertPathsToChanges(@NotNull GitRepository repository,
                                             @NotNull Collection<String> affectedPaths, boolean relativePaths) {
    List<Change> affectedChanges = new ArrayList<Change>();
    for (String path : affectedPaths) {
      VirtualFile file;
      if (relativePaths) {
        file = repository.getRoot().findFileByRelativePath(FileUtil.toSystemIndependentName(path));
      }
      else {
        file = myFacade.getVirtualFileByPath(path);
      }

      if (file != null) {
        Change change = myFacade.getChangeListManager(myProject).getChange(file);
        if (change != null) {
          affectedChanges.add(change);
        }
      }
    }
    return affectedChanges;
  }

}
