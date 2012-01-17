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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.Git;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.commands.GitMessageWithFilesDetector;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.stash.GitChangesSaver;
import git4idea.ui.GitUIUtil;
import git4idea.update.GitComplexProcess;
import git4idea.util.UntrackedFilesNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents {@code git checkout} operation.
 * <p>NB: This class is designed to be executed only once.</p>
 * @author Kirill Likhodedov
 */
public class GitCheckoutOperation implements GitBranchOperation {

  private static final Logger LOG = Logger.getInstance(GitCheckoutOperation.class);

  private enum Problem {
    UNMERGED_FILES,
    LOCAL_CHANGES_OVERWRITTEN_BY_CHECKOUT, UNTRACKED_FILES_OVERWRITTEN_BY_CHECKOUT,
  }

  private final Project myProject;
  private final Collection<GitRepository> myRepositories;
  @NotNull private final GitMultiRootOperationExecutor myExecutor;
  @NotNull private final ProgressIndicator myIndicator;
  private final String myStartPointReference;
  private final String myNewBranch;
  private final String myPreviousBranch;

  /*
    These are populated, as needed, in execute() and used in resolve and other processing methods.
    to do: Probably a better architecture can be used...
  */
  private Problem myDetectedProblem;
  private List<Change> myLocalChangesOverwrittenByCheckout;
  private Collection<GitRepository> myProblematicRepositories;
  private Collection<VirtualFile> myUntrackedFilesWouldBeOverwrittenByCheckout;

  public GitCheckoutOperation(@NotNull Project project, @NotNull Collection<GitRepository> repositories, @NotNull String startPointReference, @Nullable String newBranch, @NotNull String previousBranch, @NotNull GitMultiRootOperationExecutor executor, @NotNull ProgressIndicator indicator) {
    myStartPointReference = startPointReference;
    myNewBranch = newBranch;
    myPreviousBranch = previousBranch;
    myProject = project;
    myRepositories = repositories;
    myExecutor = executor;
    myIndicator = indicator;
  }
  
  @NotNull
  @Override
  public GitBranchOperationResult execute(@NotNull final GitRepository repository) {
    final GitMessageWithFilesDetector localChangesOverwrittenByCheckoutDetector
      = new GitMessageWithFilesDetector(GitMessageWithFilesDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHECKOUT, repository.getRoot());
    GitSimpleEventDetector unmergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED);
    GitMessageWithFilesDetector untrackedOverwrittenByCheckout
      = new GitMessageWithFilesDetector(GitMessageWithFilesDetector.Event.UNTRACKED_FILES_OVERWRITTEN_BY, repository.getRoot());

    GitCommandResult result = Git.checkout(repository, myStartPointReference, myNewBranch,
                                           localChangesOverwrittenByCheckoutDetector, unmergedDetector, untrackedOverwrittenByCheckout);
    if (result.success()) {
      refreshRoot(repository);
      return GitBranchOperationResult.success();
    }
    else if (unmergedDetector.hasHappened()) {
      myDetectedProblem = Problem.UNMERGED_FILES;
      return GitBranchOperationResult.resolvable();
    }
    else if (localChangesOverwrittenByCheckoutDetector.wasMessageDetected()) {
      myDetectedProblem = Problem.LOCAL_CHANGES_OVERWRITTEN_BY_CHECKOUT;
      // get changes overwritten by checkout from the error message captured from Git
      List<Change> affectedChanges = getChangesAffectedByCheckout(repository, localChangesOverwrittenByCheckoutDetector.getRelativeFilePaths(), true);
      // get changes in all other repositories (except those which already have succeeded) to avoid multiple dialogs proposing smart checkout
      List<GitRepository> remainingRepositories = ContainerUtil.filter(myRepositories, new Condition<GitRepository>() {
        @Override
        public boolean value(GitRepository repo) {
          return !repo.equals(repository) && !myExecutor.getSuccessfulRepositories().contains(repo);
        }
      });
      Map<GitRepository, List<Change>> changesByRepository = collectChangesConflictingWithCheckout(remainingRepositories);
      myProblematicRepositories = new ArrayList<GitRepository>(changesByRepository.keySet());
      myProblematicRepositories.add(repository);
      for (List<Change> changes : changesByRepository.values()) {
        affectedChanges.addAll(changes);
      }
      myLocalChangesOverwrittenByCheckout = affectedChanges;
      return GitBranchOperationResult.resolvable();
    }
    else if (untrackedOverwrittenByCheckout.wasMessageDetected()) {
      LOG.info("doCheckout: untracked files would be overwritten by checkout");
      myUntrackedFilesWouldBeOverwrittenByCheckout = untrackedOverwrittenByCheckout.getFiles();
      myDetectedProblem = Problem.UNTRACKED_FILES_OVERWRITTEN_BY_CHECKOUT;
      return GitBranchOperationResult.error(getCommonErrorTitle(), result.getErrorOutputAsJoinedString());
    }
    else {
      return GitBranchOperationResult.error(getCommonErrorTitle(), result.getErrorOutputAsJoinedString());
    }
  }

  @Override
  public void rollback(@NotNull Collection<GitRepository> repositories) {
    GitCompoundResult compoundResult = new GitCompoundResult(myProject);
    for (GitRepository repository : repositories) {
      GitCommandResult result = Git.checkout(repository, myPreviousBranch, null);
      compoundResult.append(repository, result);
      refreshRoot(repository);
    }
    if (!compoundResult.totalSuccess()) {
      GitUIUtil.notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, myProject, "Error during rolling checkout back",
                       compoundResult.getErrorOutputWithReposIndication(), NotificationType.ERROR, null);
    }
  }

  @Override
  public boolean rollbackable() {
    return true;
  }

  @NotNull
  @Override
  public GitBranchOperationResult tryResolve() {
    switch (myDetectedProblem) {
      case UNMERGED_FILES:
        return GitBranchUtil.proposeToResolveUnmergedFiles(myProject, myRepositories, getCommonErrorTitle(),
                                                           "Couldn't checkout branch due to unmerged files.");
      case LOCAL_CHANGES_OVERWRITTEN_BY_CHECKOUT:
        if (GitWouldBeOverwrittenByCheckoutDialog.showAndGetAnswer(myProject, myLocalChangesOverwrittenByCheckout)) {
          return smartCheckout(myProblematicRepositories, myStartPointReference, myNewBranch, myIndicator);
        }
        return GitBranchOperationResult.error(getCommonErrorTitle(), "Local changes would be overwritten by checkout.<br/>Commit or stash the changes before checking out.");

      case UNTRACKED_FILES_OVERWRITTEN_BY_CHECKOUT:
      default:
          throw new AssertionError("Impossible case " + myDetectedProblem);

    }
  }

  @NotNull
  private String getCommonErrorTitle() {
    return "Couldn't checkout " + myStartPointReference;
  }

  @NotNull
  private Map<GitRepository, List<Change>> collectChangesConflictingWithCheckout(@NotNull Collection<GitRepository> repositories) {
    Map<GitRepository, List<Change>> changes = new HashMap<GitRepository, List<Change>>();
    for (GitRepository repository : repositories) {
      try {
        Collection<String> diff = GitUtil.getPathsDiffBetweenRefs(myPreviousBranch, myStartPointReference, myProject, repository.getRoot());
        List<Change> changesInRepo = getChangesAffectedByCheckout(repository, diff, false);
        if (!changesInRepo.isEmpty()) {
          changes.put(repository, changesInRepo);
        }
      }
      catch (VcsException e) {
        // ignoring the exception: this is not fatal if we won't collect such a diff from other repositories. 
        // At worst, use will get double dialog proposing the smart checkout.
        LOG.warn(String.format("Couldn't collect diff between %s and %s in %s", myPreviousBranch, myStartPointReference, repository.getRoot()));
      }
    }
    return changes;
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    if (myNewBranch == null) {
      return String.format("Checked out <b><code>%s</code></b>", myStartPointReference);
    }
    return String.format("Checked out new branch <b><code>%s</code></b> from <b><code>%s</code></b>", myNewBranch, myStartPointReference);
  }

  @Override
  public boolean showFatalError() {
    if (myDetectedProblem.equals(Problem.UNTRACKED_FILES_OVERWRITTEN_BY_CHECKOUT)) {
      UntrackedFilesNotifier.notifyUntrackedFilesOverwrittenBy(myProject, myUntrackedFilesWouldBeOverwrittenByCheckout, "checkout");
      return true;
    }
    return false;
  }

  // stash - checkout - unstash
  private GitBranchOperationResult smartCheckout(@NotNull final Collection<GitRepository> repositories, @NotNull final String reference, @Nullable final String newBranch, @NotNull ProgressIndicator indicator) {
    final GitChangesSaver saver = configureSaver(reference, indicator);

    final AtomicReference<GitBranchOperationResult> result = new AtomicReference<GitBranchOperationResult>();
    GitComplexProcess.Operation checkoutOperation = new GitComplexProcess.Operation() {
      @Override public void run(ContinuationContext context) {
        GitBranchOperationResult saveResult = save(repositories, saver);
        if (saveResult.isSuccess()) {
          try {
            result.set(justCheckout(repositories, reference, newBranch));
          } finally {
            saver.restoreLocalChanges(context);
          }
        }
      }
    };
    GitComplexProcess.execute(myProject, "checkout", checkoutOperation);
    return result.get();
  }

  /**
   * Configures the saver, actually notifications and texts in the GitConflictResolver used inside.
   */
  private GitChangesSaver configureSaver(final String reference, ProgressIndicator indicator) {
    GitChangesSaver saver = GitChangesSaver.getSaver(myProject, indicator, String.format("Checkout %s at %s",
                                                                                         reference,
                                                                                         DateFormatUtil.formatDateTime(Clock.getTime())));
    MergeDialogCustomizer mergeDialogCustomizer = new MergeDialogCustomizer() {
      @Override
      public String getMultipleFileMergeDescription(Collection<VirtualFile> files) {
        return String.format(
          "<html>Uncommitted changes that were saved before checkout have conflicts with files from <code>%s</code></html>",
          reference);
      }

      @Override
      public String getLeftPanelTitle(VirtualFile file) {
        return "Uncommitted changes";
      }

      @Override
      public String getRightPanelTitle(VirtualFile file, VcsRevisionNumber lastRevisionNumber) {
        return String.format("<html>Changes from <b><code>%s</code></b></html>", reference);
      }
    };

    GitConflictResolver.Params params = new GitConflictResolver.Params().
      setReverse(true).
      setMergeDialogCustomizer(mergeDialogCustomizer).
      setErrorNotificationTitle("Local changes were not restored");

    saver.setConflictResolverParams(params);
    return saver;
  }

  /**
   * Saves local changes. In case of error shows a notification and returns false.
   */
  private static GitBranchOperationResult save(@NotNull Collection<GitRepository> repositories, @NotNull GitChangesSaver saver) {
    try {
      saver.saveLocalChanges(GitUtil.getRoots(repositories));
      return GitBranchOperationResult.success();
    } catch (VcsException e) {
      LOG.info("Couldn't save local changes", e);
      return GitBranchOperationResult.error("Git checkout failed",
               String.format("Tried to save uncommitted changes in %s before checkout, but failed with an error.<br/>%s",
                             saver.getSaverName(), StringUtil.join(e.getMessages())));
    }
  }

  /**
   * Checks out or shows an error message.
   */
  private GitBranchOperationResult justCheckout(@NotNull Collection<GitRepository> repositories, @NotNull String reference, @Nullable String newBranch) {
    GitCompoundResult compoundResult = new GitCompoundResult(myProject);
    for (GitRepository repository : repositories) {
      compoundResult.append(repository, Git.checkout(repository, reference, newBranch));
    }
    if (compoundResult.totalSuccess()) {
      return GitBranchOperationResult.success();
    }
    return GitBranchOperationResult.error("Couldn't checkout " + reference, compoundResult.getErrorOutputWithReposIndication());
  }

  /**
   * Forms the list of the changes, that would be overwritten by checkout.
   *
   * @param repository
   * @param affectedPaths paths returned by Git.
   * @param relativePaths Are the paths specified relative or absolute.
   * @return List of Changes is these paths.
   */
  private List<Change> getChangesAffectedByCheckout(@NotNull GitRepository repository, @NotNull Collection<String> affectedPaths, boolean relativePaths) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    List<Change> affectedChanges = new ArrayList<Change>();
    for (String path : affectedPaths) {
      VirtualFile file;
      if (relativePaths) {
        file = repository.getRoot().findFileByRelativePath(FileUtil.toSystemIndependentName(path));
      }
      else {
        file = VcsUtil.getVirtualFile(path);
      }

      if (file != null) {
        Change change = changeListManager.getChange(file);
        if (change != null) {
          affectedChanges.add(change);
        }
      }
    }
    return affectedChanges;
  }

  private static void refreshRoot(GitRepository repository) {
    repository.getRoot().refresh(true, true);
  }

}
