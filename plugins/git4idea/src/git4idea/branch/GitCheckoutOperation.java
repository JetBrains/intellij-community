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
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.DialogManager;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.stash.GitChangesSaver;
import git4idea.update.GitComplexProcess;
import git4idea.util.GitUIUtil;
import git4idea.util.GitUtil;
import git4idea.util.UntrackedFilesNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.commands.GitMessageWithFilesDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHECKOUT;
import static git4idea.commands.GitMessageWithFilesDetector.Event.UNTRACKED_FILES_OVERWRITTEN_BY;

/**
 * Represents {@code git checkout} operation.
 * Fails to checkout if there are unmerged files.
 * Fails to checkout if there are untracked files that would be overwritten by checkout. Shows the list of files.
 * If there are local changes that would be overwritten by checkout, proposes to perform a "smart checkout" which means stashing local
 * changes, checking out, and then unstashing the changes back (possibly with showing the conflict resolving dialog). 
 *
 *  @author Kirill Likhodedov
 */
public class GitCheckoutOperation extends GitBranchOperation {

  private static final Logger LOG = Logger.getInstance(GitCheckoutOperation.class);

  @NotNull private final String myStartPointReference;
  @Nullable private final String myNewBranch;
  @NotNull private final String myPreviousBranch;

  public GitCheckoutOperation(@NotNull Project project, @NotNull Collection<GitRepository> repositories,
                              @NotNull String startPointReference, @Nullable String newBranch, @NotNull String previousBranch, 
                              @NotNull ProgressIndicator indicator) {
    super(project, repositories, indicator);
    myStartPointReference = startPointReference;
    myNewBranch = newBranch;
    myPreviousBranch = previousBranch;
  }
  
  @Override
  protected void execute() {
    boolean fatalErrorHappened = false;
    while (hasMoreRepositories() && !fatalErrorHappened) {
      final GitRepository repository = next();

      VirtualFile root = repository.getRoot();
      GitMessageWithFilesDetector localChangesOverwrittenByCheckout = new GitMessageWithFilesDetector(LOCAL_CHANGES_OVERWRITTEN_BY_CHECKOUT, root);
      GitSimpleEventDetector unmergedFiles = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED);
      GitMessageWithFilesDetector untrackedOverwrittenByCheckout = new GitMessageWithFilesDetector(UNTRACKED_FILES_OVERWRITTEN_BY, root);

      GitCommandResult result = Git.checkout(repository, myStartPointReference, myNewBranch,
                                             localChangesOverwrittenByCheckout, unmergedFiles, untrackedOverwrittenByCheckout);
      if (result.success()) {
        refresh(repository);
        markSuccessful(repository);
      }
      else if (unmergedFiles.hasHappened()) {
        fatalUnmergedFilesError();
        fatalErrorHappened = true;
      }
      else if (localChangesOverwrittenByCheckout.wasMessageDetected()) {
        boolean smartCheckoutSucceeded = smartCheckoutOrNotify(repository, localChangesOverwrittenByCheckout);
        if (!smartCheckoutSucceeded) {
          fatalErrorHappened = true;
        }
      }
      else if (untrackedOverwrittenByCheckout.wasMessageDetected()) {
        fatalUntrackedFilesError(untrackedOverwrittenByCheckout.getFiles());
        fatalErrorHappened = true;
      }
      else {
        fatalError(getCommonErrorTitle(), result.getErrorOutputAsJoinedString());
        fatalErrorHappened = true;
      }
    }

    if (!fatalErrorHappened) {
      notifySuccess();
    }
  }

  private boolean smartCheckoutOrNotify(@NotNull GitRepository repository, 
                                        @NotNull GitMessageWithFilesDetector localChangesOverwrittenByCheckout) {
    // get changes overwritten by checkout from the error message captured from Git
    List<Change> affectedChanges = getChangesAffectedByCheckout(repository, localChangesOverwrittenByCheckout.getRelativeFilePaths(), true);
    // get all other conflicting changes
    Map<GitRepository, List<Change>> conflictingChangesInRepositories = collectLocalChangesOnAllOtherRepositories(repository);
    Set<GitRepository> otherProblematicRepositories = conflictingChangesInRepositories.keySet();
    Collection<GitRepository> allConflictingRepositories = new ArrayList<GitRepository>(otherProblematicRepositories);
    allConflictingRepositories.add(repository);
    for (List<Change> changes : conflictingChangesInRepositories.values()) {
      affectedChanges.addAll(changes);
    }

    if (GitWouldBeOverwrittenByCheckoutDialog.showAndGetAnswer(myProject, affectedChanges)) {
      boolean smartCheckedOutSuccessfully = smartCheckout(allConflictingRepositories, myStartPointReference, myNewBranch, getIndicator());
      if (smartCheckedOutSuccessfully) {
        GitRepository[] otherRepositories = ArrayUtil.toObjectArray(otherProblematicRepositories, GitRepository.class);

        markSuccessful(repository);
        markSuccessful(otherRepositories);
        refresh(repository);
        refresh(otherRepositories);
        return true;
      }
      else {
        // notification is handled in smartCheckout()
        return false;
      }
    }
    else {
      fatalLocalChangesError();
      return false;
    }
  }

  private void fatalLocalChangesError() {
    String title = "Couldn't checkout " + myStartPointReference;
    String message = "Local changes would be overwritten by checkout.<br/>Stash or commit them before checking out a branch.<br/>";
    if (wereSuccessful()) {
      showFatalErrorDialogWithRollback(title, message);
    }
    else {
      showFatalNotification(title, message);
    }
  }

  @NotNull
  private Map<GitRepository, List<Change>> collectLocalChangesOnAllOtherRepositories(@NotNull final GitRepository currentRepository) {
    // get changes in all other repositories (except those which already have succeeded) to avoid multiple dialogs proposing smart checkout
    List<GitRepository> remainingRepositories = ContainerUtil.filter(getRepositories(), new Condition<GitRepository>() {
      @Override
      public boolean value(GitRepository repo) {
        return !repo.equals(currentRepository) && !getSuccessfulRepositories().contains(repo);
      }
    });
    return collectChangesConflictingWithCheckout(remainingRepositories);
  }

  private void fatalUntrackedFilesError(@NotNull Collection<VirtualFile> untrackedFiles) {
    if (wereSuccessful()) {
      showUntrackedFilesDialogWithRollback(untrackedFiles);
    }
    else {
      showUntrackedFilesNotification(untrackedFiles);
    }
  }

  private void showUntrackedFilesNotification(@NotNull Collection<VirtualFile> untrackedFiles) {
    UntrackedFilesNotifier.notifyUntrackedFilesOverwrittenBy(myProject, untrackedFiles, "checkout");
  }

  private void showUntrackedFilesDialogWithRollback(@NotNull Collection<VirtualFile> untrackedFiles) {
    String title = "Couldn't checkout";
    String description = UntrackedFilesNotifier.createUntrackedFilesOverwrittenDescription("checkout", true);

    final SelectFilesDialog dialog = new UntrackedFilesDialog(myProject, new ArrayList<VirtualFile>(untrackedFiles),
                                                              StringUtil.stripHtml(description, true));
    dialog.setTitle(title);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        DialogManager.getInstance(myProject).showDialog(dialog);
      }
    });

    if (dialog.isOK()) {
      rollback();
    }
  }

  private class UntrackedFilesDialog extends SelectFilesDialog {

    public UntrackedFilesDialog(@NotNull Project project, @NotNull List<VirtualFile> originalFiles, @NotNull String prompt) {
      super(project, originalFiles, prompt, null, false, false);
      setOKButtonText("Rollback");
      setCancelButtonText("Don't rollback");
    }

    @Override
    protected JComponent createSouthPanel() {
      JComponent buttons = super.createSouthPanel();
      JPanel panel = new JPanel(new VerticalFlowLayout());
      panel.add(new JBLabel("<html>" + getRollbackProposal() + "</html>"));
      panel.add(buttons);
      return panel;
    }
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    return "However checkout has succeeded for the following repositories:<br/>" +
           successfulRepositoriesJoined() +
           "<br/>You may rollback (checkout back to " + myPreviousBranch + ") not to let branches diverge.";
  }

  @Override
  protected void rollback() {
    GitCompoundResult compoundResult = new GitCompoundResult(myProject);
    for (GitRepository repository : getSuccessfulRepositories()) {
      GitCommandResult result = Git.checkout(repository, myPreviousBranch, null);
      compoundResult.append(repository, result);
      refresh(repository);
    }
    if (!compoundResult.totalSuccess()) {
      GitUIUtil.notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, myProject, "Error during rolling checkout back",
                       compoundResult.getErrorOutputWithReposIndication(), NotificationType.ERROR, null);
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

  // stash - checkout - unstash
  private boolean smartCheckout(@NotNull final Collection<GitRepository> repositories, @NotNull final String reference, @Nullable final String newBranch, @NotNull ProgressIndicator indicator) {
    final GitChangesSaver saver = configureSaver(reference, indicator);

    final AtomicBoolean result = new AtomicBoolean();
    GitComplexProcess.Operation checkoutOperation = new GitComplexProcess.Operation() {
      @Override public void run(ContinuationContext context) {
        boolean savedSuccessfully = save(repositories, saver);
        if (savedSuccessfully) {
          try {
            result.set(checkoutOrNotify(repositories, reference, newBranch));
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
  private boolean save(@NotNull Collection<GitRepository> repositories, @NotNull GitChangesSaver saver) {
    try {
      saver.saveLocalChanges(GitUtil.getRoots(repositories));
      return true;
    } catch (VcsException e) {
      LOG.info("Couldn't save local changes", e);
      notifyError("Couldn't save uncommitted changes.",
                  String.format("Tried to save uncommitted changes in %s before checkout, but failed with an error.<br/>%s",
                                saver.getSaverName(), StringUtil.join(e.getMessages())));
      return false;
    }
  }

  /**
   * Checks out or shows an error message.
   */
  private boolean checkoutOrNotify(@NotNull Collection<GitRepository> repositories,
                                                    @NotNull String reference,
                                                    @Nullable String newBranch) {
    GitCompoundResult compoundResult = new GitCompoundResult(myProject);
    for (GitRepository repository : repositories) {
      compoundResult.append(repository, Git.checkout(repository, reference, newBranch));
    }
    if (compoundResult.totalSuccess()) {
      return true;
    }
    notifyError("Couldn't checkout " + reference, compoundResult.getErrorOutputWithReposIndication());
    return false;
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

  private static void refresh(GitRepository... repositories) {
    for (GitRepository repository : repositories) {
      refreshRoot(repository);
      repository.update(GitRepository.TrackedTopic.ALL_CURRENT);
    }
  }
  
  private static void refreshRoot(GitRepository repository) {
    repository.getRoot().refresh(true, true);
  }
  
}
