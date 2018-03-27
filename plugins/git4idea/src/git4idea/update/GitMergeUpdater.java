/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.branch.GitBranchPair;
import git4idea.commands.*;
import git4idea.merge.GitConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.repo.GitRepository;
import git4idea.util.GitUIUtil;
import git4idea.util.GitUntrackedFilesHelper;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static java.util.Arrays.asList;

/**
 * Handles {@code git pull} via merge.
 */
public class GitMergeUpdater extends GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitMergeUpdater.class);

  @NotNull private final ChangeListManager myChangeListManager;

  public GitMergeUpdater(@NotNull Project project,
                         @NotNull Git git,
                         @NotNull GitRepository repository,
                         @NotNull GitBranchPair branchAndTracked,
                         @NotNull ProgressIndicator progressIndicator,
                         @NotNull UpdatedFiles updatedFiles) {
    super(project, git, repository, branchAndTracked, progressIndicator, updatedFiles);
    myChangeListManager = ChangeListManager.getInstance(myProject);
  }

  @Override
  @NotNull
  protected GitUpdateResult doUpdate() {
    LOG.info("doUpdate ");
    final GitMerger merger = new GitMerger(myProject);

    MergeLineListener mergeLineListener = new MergeLineListener();
    GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector = new GitUntrackedFilesOverwrittenByOperationDetector(myRoot);

    String originalText = myProgressIndicator.getText();
    myProgressIndicator.setText("Merging" + GitUtil.mention(myRepository) + "...");
    try {
      GitCommandResult result = myGit.merge(myRepository, assertNotNull(myBranchPair.getDest()).getName(),
                                            asList("--no-stat", "-v"), mergeLineListener, untrackedFilesDetector,
                                            GitStandardProgressAnalyzer.createListener(myProgressIndicator));
      myProgressIndicator.setText(originalText);
      return result.success()
             ? GitUpdateResult.SUCCESS
             : handleMergeFailure(mergeLineListener, untrackedFilesDetector, merger, result.getErrorOutputAsJoinedString());
    }
    catch (ProcessCanceledException pce) {
      cancel();
      return GitUpdateResult.CANCEL;
    }
  }

  @NotNull
  private GitUpdateResult handleMergeFailure(MergeLineListener mergeLineListener,
                                             GitMessageWithFilesDetector untrackedFilesWouldBeOverwrittenByMergeDetector,
                                             final GitMerger merger,
                                             String errorMessage) {
    final MergeError error = mergeLineListener.getMergeError();
    LOG.info("merge error: " + error);
    if (error == MergeError.CONFLICT) {
      LOG.info("Conflict detected");
      final boolean allMerged =
        new MyConflictResolver(myProject, myGit, merger, myRoot).merge();
      return allMerged ? GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS : GitUpdateResult.INCOMPLETE;
    }
    else if (error == MergeError.LOCAL_CHANGES) {
      LOG.info("Local changes would be overwritten by merge");
      final List<FilePath> paths = getFilesOverwrittenByMerge(mergeLineListener.getOutput());
      final Collection<Change> changes = getLocalChangesFilteredByFiles(paths);
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        ChangeListViewerDialog dialog = new ChangeListViewerDialog(myProject, changes, false) {
          @Override protected String getDescription() {
            return "Your local changes to the following files would be overwritten by merge.<br/>" +
                              "Please, commit your changes or stash them before you can merge.";
          }
        };
        dialog.show();
      });
      return GitUpdateResult.ERROR;
    }
    else if (untrackedFilesWouldBeOverwrittenByMergeDetector.wasMessageDetected()) {
      LOG.info("handleMergeFailure: untracked files would be overwritten by merge");
      GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(myProject, myRoot,
                                                                untrackedFilesWouldBeOverwrittenByMergeDetector.getRelativeFilePaths(),
                                                                "merge", null);
      return GitUpdateResult.ERROR;
    }
    else {
      LOG.info("Unknown error: " + errorMessage);
      GitUIUtil.notifyImportantError(myProject, "Error merging", errorMessage);
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
    String currentBranch = myBranchPair.getBranch().getName();
    String remoteBranch = myBranchPair.getDest().getName();
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
      GitUIUtil.notifyImportantError(myProject, "Couldn't reset merge", result.getErrorOutputAsHtmlString());
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
    for(LocalChangeList list : myChangeListManager.getChangeLists()) {
      for (Change change : list.getChanges()) {
        final ContentRevision afterRevision = change.getAfterRevision();
        final ContentRevision beforeRevision = change.getBeforeRevision();
        if ((afterRevision != null && paths.contains(afterRevision.getFile())) || (beforeRevision != null && paths.contains(beforeRevision.getFile()))) {
          changes.add(change);
        }
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

  private static class MergeLineListener extends GitLineHandlerAdapter {
    private MergeError myMergeError;
    private List<String> myOutput = new ArrayList<>();
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
    private final GitMerger myMerger;
    private final VirtualFile myRoot;

    public MyConflictResolver(Project project, @NotNull Git git, GitMerger merger, VirtualFile root) {
      super(project, git, Collections.singleton(root), makeParams());
      myMerger = merger;
      myRoot = root;
    }
    
    private static Params makeParams() {
      Params params = new Params();
      params.setErrorNotificationTitle("Can't complete update");
      params.setMergeDescription("Merge conflicts detected. Resolve them before continuing update.");
      return params;
    }

    @Override protected boolean proceedIfNothingToMerge() throws VcsException {
      myMerger.mergeCommit(myRoot);
      return true;
    }

    @Override protected boolean proceedAfterAllMerged() throws VcsException {
      myMerger.mergeCommit(myRoot);
      return true;
    }
  }
}
