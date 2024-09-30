// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog;
import com.intellij.openapi.vcs.changes.ui.LoadingCommittedChangeListPanel;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.branch.GitBranchPair;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.repo.GitRepository;
import git4idea.util.GitUntrackedFilesHelper;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static git4idea.GitNotificationIdsHolder.MERGE_ERROR;
import static git4idea.GitNotificationIdsHolder.MERGE_RESET_ERROR;
import static java.util.Arrays.asList;

/**
 * Handles {@code git pull} via merge.
 */
public class GitMergeUpdater extends GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitMergeUpdater.class);

  private final @NotNull ChangeListManager myChangeListManager;
  private final @NotNull GitBranchPair myBranchPair;

  public GitMergeUpdater(@NotNull Project project,
                         @NotNull Git git,
                         @NotNull GitRepository repository,
                         @NotNull GitBranchPair branchPair,
                         @NotNull ProgressIndicator progressIndicator,
                         @NotNull UpdatedFiles updatedFiles) {
    super(project, git, repository, progressIndicator, updatedFiles);
    myBranchPair = branchPair;
    myChangeListManager = ChangeListManager.getInstance(myProject);
  }

  @Override
  protected @NotNull GitUpdateResult doUpdate() {
    LOG.info("doUpdate ");

    MergeLineListener mergeLineListener = new MergeLineListener();
    GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector = new GitUntrackedFilesOverwrittenByOperationDetector(myRoot);

    String originalText = myProgressIndicator.getText();
    myProgressIndicator.setText(GitBundle.message("progress.text.merging.repository", GitUtil.mention(myRepository)));
    try {
      GitCommandResult result = myGit.merge(myRepository, myBranchPair.getTarget().getName(),
                                            asList("--no-stat", "-v"), mergeLineListener, untrackedFilesDetector,
                                            GitStandardProgressAnalyzer.createListener(myProgressIndicator));
      myProgressIndicator.setText(originalText);
      return result.success()
             ? GitUpdateResult.SUCCESS
             : handleMergeFailure(mergeLineListener, untrackedFilesDetector, result);
    }
    catch (ProcessCanceledException pce) {
      cancel();
      return GitUpdateResult.CANCEL;
    }
  }

  private @NotNull GitUpdateResult handleMergeFailure(MergeLineListener mergeLineListener,
                                                      GitMessageWithFilesDetector untrackedFilesWouldBeOverwrittenByMergeDetector,
                                                      GitCommandResult commandResult) {
    final MergeError error = mergeLineListener.getMergeError();
    LOG.info("merge error: " + error);
    if (error == MergeError.CONFLICT) {
      LOG.info("Conflict detected");
      final boolean allMerged =
        new MyConflictResolver(myProject, myRoot).merge();
      return allMerged ? GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS : GitUpdateResult.INCOMPLETE;
    }
    else if (error == MergeError.LOCAL_CHANGES) {
      LOG.info("Local changes would be overwritten by merge");
      final List<FilePath> paths = getFilesOverwrittenByMerge(mergeLineListener.getOutput());
      final Collection<Change> changes = getLocalChangesFilteredByFiles(paths);
      UIUtil.invokeAndWaitIfNeeded(() -> {
        LoadingCommittedChangeListPanel panel = new LoadingCommittedChangeListPanel(myProject);
        panel.setChanges(changes, null);
        panel.setDescription(GitBundle.message("warning.your.local.changes.would.be.overwritten.by.merge"));

        ChangeListViewerDialog.showDialog(myProject, null, panel);
      });
      return GitUpdateResult.ERROR;
    }
    else if (untrackedFilesWouldBeOverwrittenByMergeDetector.isDetected()) {
      LOG.info("handleMergeFailure: untracked files would be overwritten by merge");
      GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(myProject, myRoot,
                                                                untrackedFilesWouldBeOverwrittenByMergeDetector.getRelativeFilePaths(),
                                                                GitBundle.message("merge.operation.name"), null);
      return GitUpdateResult.ERROR;
    }
    else {
      LOG.info("Unknown error: " + commandResult.getErrorOutputAsJoinedString());
      VcsNotifier.getInstance(myProject)
        .notifyError(MERGE_ERROR, GitBundle.message("notification.title.error.merging"), commandResult.getErrorOutputAsHtmlString());
      return GitUpdateResult.ERROR;
    }
  }

  @Override
  public boolean isSaveNeeded() {
    try {
      if (GitUtil.hasLocalChanges(true, myProject, myRoot)) {
        return true;
      }
    }
    catch (VcsException e) {
      LOG.info("isSaveNeeded failed to check staging area", e);
      return true;
    }

    // git log --name-status master..origin/master
    String currentBranch = myBranchPair.getSource().getName();
    String remoteBranch = myBranchPair.getTarget().getName();
    try {
      GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(myRoot);
      if (repository == null) {
        LOG.error("Repository is null for root " + myRoot);
        return true; // fail safe
      }
      final Collection<String> remotelyChanged = GitUtil.getPathsDiffBetweenRefs(Git.getInstance(), repository,
                                                                                 currentBranch, remoteBranch);
      final List<File> locallyChanged = myChangeListManager.getAffectedPaths();
      for (final File localPath : locallyChanged) {
        if (ContainerUtil.exists(remotelyChanged, remotelyChangedPath -> FileUtil.pathsEqual(localPath.getPath(), remotelyChangedPath))) {
          // found a file which was changed locally and remotely => need to save
          return true;
        }
      }
      return false;
    } catch (VcsException e) {
      LOG.info("failed to get remotely changed files for " + currentBranch + ".." + remoteBranch, e);
      return true; // fail safe
    }
  }

  private void cancel() {
    GitLineHandler h = new GitLineHandler(myProject, myRoot, GitCommand.RESET);
    h.addParameters("--merge");
    GitCommandResult result = Git.getInstance().runCommand(h);
    if (!result.success()) {
      LOG.info("cancel git reset --merge: " + result.getErrorOutputAsJoinedString());
      VcsNotifier.getInstance(myProject)
        .notifyError(MERGE_RESET_ERROR, GitBundle.message("notification.title.couldn.t.reset.merge"),
                                     result.getErrorOutputAsHtmlString());
    }
  }

  // parses the output of merge conflict returning files which would be overwritten by merge. These files will be stashed.
  private List<FilePath> getFilesOverwrittenByMerge(@NotNull List<String> mergeOutput) {
    final List<FilePath> paths = new ArrayList<>();
    for  (String line : mergeOutput) {
      if (StringUtil.isEmptyOrSpaces(line)) {
        continue;
      }
      if (line.contains("Please, commit your changes or stash them before you can merge")) {
        break;
      }
      line = line.trim();

      final String path;
      try {
        path = myRoot.getPath() + "/" + GitUtil.unescapePath(line);
        final File file = new File(path);
        if (file.exists()) {
          paths.add(VcsUtil.getFilePath(file, false));
        }
      } catch (VcsException e) { // just continue
      }
    }
    return paths;
  }

  private Collection<Change> getLocalChangesFilteredByFiles(List<FilePath> paths) {
    final Collection<Change> changes = new HashSet<>();
    for (Change change : myChangeListManager.getAllChanges()) {
      final ContentRevision afterRevision = change.getAfterRevision();
      final ContentRevision beforeRevision = change.getBeforeRevision();
      if ((afterRevision != null && paths.contains(afterRevision.getFile())) ||
          (beforeRevision != null && paths.contains(beforeRevision.getFile()))) {
        changes.add(change);
      }
    }
    return changes;
  }

  @Override
  public String toString() {
    return "Merge updater";
  }

  private enum MergeError {
    CONFLICT,
    LOCAL_CHANGES,
    OTHER
  }

  private static class MergeLineListener implements GitLineHandlerListener {
    private MergeError myMergeError;
    private final List<String> myOutput = new ArrayList<>();
    private boolean myLocalChangesError = false;

    @Override
    public void onLineAvailable(String line, Key outputType) {
      if (myLocalChangesError) {
        myOutput.add(line);
      } else if (line.contains("Automatic merge failed; fix conflicts and then commit the result")) {
        myMergeError = MergeError.CONFLICT;
      } else if (line.contains("Your local changes to the following files would be overwritten by merge")) {
        myMergeError = MergeError.LOCAL_CHANGES;
        myLocalChangesError = true;
      }
    }

    public MergeError getMergeError() {
      return myMergeError;
    }

    public List<String> getOutput() {
      return myOutput;
    }
  }

  private static class MyConflictResolver extends GitConflictResolver {
    private final VirtualFile myRoot;

    MyConflictResolver(Project project, VirtualFile root) {
      super(project, Collections.singleton(root), makeParams(project));
      myRoot = root;
    }
    
    private static Params makeParams(Project project) {
      Params params = new Params(project);
      params.setErrorNotificationTitle(GitBundle.message("merge.update.project.generic.error.title"));
      params.setMergeDescription(GitBundle.message("merge.update.project.conflict.merge.description.label"));
      return params;
    }

    @Override protected boolean proceedIfNothingToMerge() throws VcsException {
      new GitMerger(myProject).mergeCommit(myRoot);
      return true;
    }

    @Override protected boolean proceedAfterAllMerged() throws VcsException {
      new GitMerger(myProject).mergeCommit(myRoot);
      return true;
    }
  }
}
