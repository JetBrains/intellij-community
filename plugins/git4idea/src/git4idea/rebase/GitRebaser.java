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
package git4idea.rebase;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.merge.GitConflictResolver;
import git4idea.update.GitUpdateResult;
import git4idea.util.GitUIUtil;
import git4idea.util.GitUntrackedFilesHelper;
import git4idea.util.LocalChangesWouldBeOverwrittenHelper;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.CHECKOUT;

public class GitRebaser {

  private static final Logger LOG = Logger.getInstance(GitRebaser.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private GitVcs myVcs;
  @NotNull private ProgressIndicator myProgressIndicator;

  @NotNull private List<GitRebaseUtils.CommitInfo> mySkippedCommits;

  public GitRebaser(@NotNull Project project, @NotNull Git git, @NotNull ProgressIndicator progressIndicator) {
    myProject = project;
    myGit = git;
    myProgressIndicator = progressIndicator;
    myVcs = GitVcs.getInstance(project);
    mySkippedCommits = new ArrayList<>();
  }

  public GitUpdateResult rebase(@NotNull VirtualFile root,
                                @NotNull List<String> parameters,
                                @Nullable final Runnable onCancel,
                                @Nullable GitLineHandlerListener lineListener) {
    final GitLineHandler rebaseHandler = createHandler(root);
    rebaseHandler.setStdoutSuppressed(false);
    rebaseHandler.addParameters(parameters);
    if (lineListener != null) {
      rebaseHandler.addLineListener(lineListener);
    }

    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    rebaseHandler.addLineListener(rebaseConflictDetector);
    GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector = new GitUntrackedFilesOverwrittenByOperationDetector(root);
    GitLocalChangesWouldBeOverwrittenDetector localChangesDetector = new GitLocalChangesWouldBeOverwrittenDetector(root, CHECKOUT);
    rebaseHandler.addLineListener(untrackedFilesDetector);
    rebaseHandler.addLineListener(localChangesDetector);
    rebaseHandler.addLineListener(GitStandardProgressAnalyzer.createListener(myProgressIndicator));

    AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
    try {
      String oldText = myProgressIndicator.getText();
      myProgressIndicator.setText("Rebasing...");
      GitCommandResult result = myGit.runCommand(rebaseHandler);
      myProgressIndicator.setText(oldText);
      return result.success() ?
             GitUpdateResult.SUCCESS :
             handleRebaseFailure(rebaseHandler, root, result, rebaseConflictDetector, untrackedFilesDetector, localChangesDetector);
    }
    catch (ProcessCanceledException pce) {
      if (onCancel != null) {
        onCancel.run();
      }
      return GitUpdateResult.CANCEL;
    }
    finally {
      token.finish();
    }
  }

  protected GitLineHandler createHandler(VirtualFile root) {
    return new GitLineHandler(myProject, root, GitCommand.REBASE);
  }

  public void abortRebase(@NotNull VirtualFile root) {
    LOG.info("abortRebase " + root);
    final GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE);
    rh.setStdoutSuppressed(false);
    rh.addParameters("--abort");
    GitTask task = new GitTask(myProject, rh, "Aborting rebase");
    task.setProgressIndicator(myProgressIndicator);
    task.executeAsync(new GitTaskResultNotificationHandler(myProject, "Rebase aborted", "Abort rebase cancelled", "Error aborting rebase"));
  }

  public boolean continueRebase(@NotNull VirtualFile root) {
    return continueRebase(root, "--continue");
  }

  /**
   * Runs 'git rebase --continue' on several roots consequently.
   * @return true if rebase successfully finished.
   */
  public boolean continueRebase(@NotNull Collection<VirtualFile> rebasingRoots) {
    AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
    try {
      boolean success = true;
      for (VirtualFile root : rebasingRoots) {
        success &= continueRebase(root);
      }
      return success;
    }
    finally {
      token.finish();
    }
  }

  // start operation may be "--continue" or "--skip" depending on the situation.
  private boolean continueRebase(final @NotNull VirtualFile root, @NotNull String startOperation) {
    LOG.info("continueRebase " + root + " " + startOperation);
    final GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE);
    rh.setStdoutSuppressed(false);
    rh.addParameters(startOperation);
    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    rh.addLineListener(rebaseConflictDetector);

    makeContinueRebaseInteractiveEditor(root, rh);

    final GitTask rebaseTask = new GitTask(myProject, rh, "git rebase " + startOperation);
    rebaseTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    rebaseTask.setProgressIndicator(myProgressIndicator);
    return executeRebaseTaskInBackground(root, rh, rebaseConflictDetector, rebaseTask);
  }

  protected void makeContinueRebaseInteractiveEditor(VirtualFile root, GitLineHandler rh) {
    GitRebaseEditorService rebaseEditorService = GitRebaseEditorService.getInstance();
    // TODO If interactive rebase with commit rewording was invoked, this should take the reworded message
    GitRebaser.TrivialEditor editor = new GitRebaser.TrivialEditor(rebaseEditorService, myProject, root);
    UUID rebaseEditorNo = editor.getHandlerNo();
    rebaseEditorService.configureHandler(rh, rebaseEditorNo);
  }

  /**
   * @return Roots which have unfinished rebase process. May be empty.
   */
  public @NotNull Collection<VirtualFile> getRebasingRoots() {
    final Collection<VirtualFile> rebasingRoots = new HashSet<>();
    for (VirtualFile root : ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(myVcs)) {
      if (GitRebaseUtils.isRebaseInTheProgress(myProject, root)) {
        rebasingRoots.add(root);
      }
    }
    return rebasingRoots;
  }

  /**
   * Reorders commits so that the given commits go before others, just after the given parentCommit.
   * For example, if A->B->C->D are unpushed commits and B and D are supplied to this method, then after rebase the commits will
   * look like that: B->D->A->C.
   * NB: If there are merges in the unpushed commits being reordered, a conflict would happen. The calling code should probably
   * prohibit reordering merge commits.
   */
  public boolean reoderCommitsIfNeeded(@NotNull final VirtualFile root, @NotNull String parentCommit, @NotNull List<String> olderCommits) throws VcsException {
    List<String> allCommits = new ArrayList<>(); //TODO
    if (olderCommits.isEmpty() || olderCommits.size() == allCommits.size()) {
      LOG.info("Nothing to reorder. olderCommits: " + olderCommits + " allCommits: " + allCommits);
      return true;
    }

    final GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.REBASE);
    h.setStdoutSuppressed(false);
    UUID rebaseEditorNo = null;
    GitRebaseEditorService rebaseEditorService = GitRebaseEditorService.getInstance();
    try {
      h.addParameters("-i", "-m", "-v");
      h.addParameters(parentCommit);

      final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
      h.addLineListener(rebaseConflictDetector);

      final PushRebaseEditor pushRebaseEditor = new PushRebaseEditor(rebaseEditorService, root, olderCommits, false);
      rebaseEditorNo = pushRebaseEditor.getHandlerNo();
      rebaseEditorService.configureHandler(h, rebaseEditorNo);

      final GitTask rebaseTask = new GitTask(myProject, h, "Reordering commits");
      rebaseTask.setProgressIndicator(myProgressIndicator);
      return executeRebaseTaskInBackground(root, h, rebaseConflictDetector, rebaseTask);
    } finally { // TODO should be unregistered in the task.success
      // unregistering rebase service
      if (rebaseEditorNo != null) {
        rebaseEditorService.unregisterHandler(rebaseEditorNo);
      }
    }
  }

  private boolean executeRebaseTaskInBackground(VirtualFile root, GitLineHandler h, GitRebaseProblemDetector rebaseConflictDetector, GitTask rebaseTask) {
    final AtomicBoolean result = new AtomicBoolean();
    final AtomicBoolean failure = new AtomicBoolean();
    rebaseTask.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        result.set(true);
      }

      @Override protected void onCancel() {
        result.set(false);
      }

      @Override protected void onFailure() {
        failure.set(true);
      }
    });
    if (failure.get()) {
      result.set(handleRebaseFailure(root, h, rebaseConflictDetector));
    }
    return result.get();
  }

  /**
   * @return true if the failure situation was resolved successfully, false if we failed to resolve the problem.
   */
  private boolean handleRebaseFailure(final VirtualFile root, final GitLineHandler h, GitRebaseProblemDetector rebaseConflictDetector) {
    if (rebaseConflictDetector.isMergeConflict()) {
      LOG.info("handleRebaseFailure merge conflict");
      return new GitConflictResolver(myProject, myGit, Collections.singleton(root), makeParamsForRebaseConflict()) {
        @Override protected boolean proceedIfNothingToMerge() {
          return continueRebase(root, "--continue");
        }

        @Override protected boolean proceedAfterAllMerged() {
          return continueRebase(root, "--continue");
        }
      }.merge();
    }
    else if (rebaseConflictDetector.isNoChangeError()) {
      LOG.info("handleRebaseFailure no changes error detected");
      try {
        if (GitUtil.hasLocalChanges(true, myProject, root)) {
          LOG.error("The rebase detector incorrectly detected 'no changes' situation. Attempting to continue rebase.");
          return continueRebase(root);
        }
        else if (GitUtil.hasLocalChanges(false, myProject, root)) {
          LOG.warn("No changes from patch were not added to the index. Adding all changes from tracked files.");
          stageEverything(root);
          return continueRebase(root);
        }
        else {
          GitRebaseUtils.CommitInfo commit = GitRebaseUtils.getCurrentRebaseCommit(myProject, root);
          LOG.info("no changes confirmed. Skipping commit " + commit);
          mySkippedCommits.add(commit);
          return continueRebase(root, "--skip");
        }
      }
      catch (VcsException e) {
        LOG.info("Failed to work around 'no changes' error.", e);
        String message = "Couldn't proceed with rebase. " + e.getMessage();
        GitUIUtil.notifyImportantError(myProject, "Error rebasing", message);
        return false;
      }
    }
    else {
      LOG.info("handleRebaseFailure error " + h.errors());
      GitUIUtil.notifyImportantError(myProject, "Error rebasing", GitUIUtil.stringifyErrors(h.errors()));
      return false;
    }
  }

  private void stageEverything(@NotNull VirtualFile root) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(myProject, root, GitCommand.ADD);
    handler.setSilent(false);
    handler.addParameters("--update");
    handler.run();
  }

  private static GitConflictResolver.Params makeParamsForRebaseConflict() {
    return new GitConflictResolver.Params().
      setReverse(true).
      setErrorNotificationTitle("Can't continue rebase").
      setMergeDescription("Merge conflicts detected. Resolve them before continuing rebase.").
      setErrorNotificationAdditionalDescription("Then you may <b>continue rebase</b>. <br/> " +
                                                "You also may <b>abort rebase</b> to restore the original branch and stop rebasing.");
  }

  public static class TrivialEditor extends GitInteractiveRebaseEditorHandler{
    public TrivialEditor(@NotNull GitRebaseEditorService service,
                         @NotNull Project project,
                         @NotNull VirtualFile root) {
      super(service, project, root);
    }

    @Override
    public int editCommits(@NotNull String path) {
      return 0;
    }
  }

  @NotNull
  public GitUpdateResult handleRebaseFailure(@NotNull GitLineHandler handler,
                                             @NotNull VirtualFile root,
                                             @NotNull GitCommandResult result,
                                             @NotNull GitRebaseProblemDetector rebaseConflictDetector,
                                             @NotNull GitMessageWithFilesDetector untrackedWouldBeOverwrittenDetector,
                                             @NotNull GitLocalChangesWouldBeOverwrittenDetector localChangesDetector) {
    if (rebaseConflictDetector.isMergeConflict()) {
      LOG.info("handleRebaseFailure merge conflict");
      final boolean allMerged = new GitRebaser.ConflictResolver(myProject, myGit, root, this).merge();
      return allMerged ? GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS : GitUpdateResult.INCOMPLETE;
    }
    else if (untrackedWouldBeOverwrittenDetector.wasMessageDetected()) {
      LOG.info("handleRebaseFailure: untracked files would be overwritten by checkout");
      GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(myProject, root,
                                                                untrackedWouldBeOverwrittenDetector.getRelativeFilePaths(), "rebase", null);
      return GitUpdateResult.ERROR;
    }
    else if (localChangesDetector.wasMessageDetected()) {
      LocalChangesWouldBeOverwrittenHelper.showErrorNotification(myProject, root, "rebase", localChangesDetector.getRelativeFilePaths());
      return GitUpdateResult.ERROR;
    }
    else {
      LOG.info("handleRebaseFailure error " + handler.errors());
      VcsNotifier.getInstance(myProject).notifyError("Rebase error", result.getErrorOutputAsHtmlString());
      return GitUpdateResult.ERROR;
    }
  }

  public static class ConflictResolver extends GitConflictResolver {
    @NotNull private final GitRebaser myRebaser;
    @NotNull private final VirtualFile myRoot;

    public ConflictResolver(@NotNull Project project, @NotNull Git git, @NotNull VirtualFile root, @NotNull GitRebaser rebaser) {
      super(project, git, Collections.singleton(root), makeParams());
      myRebaser = rebaser;
      myRoot = root;
    }

    private static Params makeParams() {
      Params params = new Params();
      params.setReverse(true);
      params.setMergeDescription("Merge conflicts detected. Resolve them before continuing rebase.");
      params.setErrorNotificationTitle("Can't continue rebase");
      params.setErrorNotificationAdditionalDescription("Then you may <b>continue rebase</b>. <br/> You also may <b>abort rebase</b> to restore the original branch and stop rebasing.");
      return params;
    }

    @Override protected boolean proceedIfNothingToMerge() throws VcsException {
      return myRebaser.continueRebase(myRoot);
    }

    @Override protected boolean proceedAfterAllMerged() throws VcsException {
      return myRebaser.continueRebase(myRoot);
    }
  }

  /**
   * The rebase editor that just overrides the list of commits
   */
  class PushRebaseEditor extends GitInteractiveRebaseEditorHandler {
    private final Logger LOG = Logger.getInstance(PushRebaseEditor.class);
    private final List<String> myCommits; // The reordered commits
    private final boolean myHasMerges; // true means that the root has merges

    /**
     * The constructor from fields that is expected to be
     * accessed only from {@link git4idea.rebase.GitRebaseEditorService}.
     *
     * @param rebaseEditorService
     * @param root      the git repository root
     * @param commits   the reordered commits
     * @param hasMerges if true, the vcs root has merges
     */
    public PushRebaseEditor(GitRebaseEditorService rebaseEditorService,
                            final VirtualFile root,
                            List<String> commits,
                            boolean hasMerges) {
      super(rebaseEditorService, myProject, root);
      myCommits = commits;
      myHasMerges = hasMerges;
    }

    public int editCommits(@NotNull String path) {
      if (!myRebaseEditorShown) {
        myRebaseEditorShown = true;
        if (myHasMerges) {
          return 0;
        }
        try {
          TreeMap<String, String> pickLines = new TreeMap<>();
          StringScanner s = new StringScanner(new String(FileUtil.loadFileText(new File(path), CharsetToolkit.UTF8)));
          while (s.hasMoreData()) {
            if (!s.tryConsume("pick ")) {
              s.line();
              continue;
            }
            String commit = s.spaceToken();
            pickLines.put(commit, "pick " + commit + " " + s.line());
          }
          PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(path), CharsetToolkit.UTF8));
          try {
            for (String commit : myCommits) {
              String key = pickLines.headMap(commit + "\u0000").lastKey();
              if (key == null || !commit.startsWith(key)) {
                continue; // commit from merged branch
              }
              w.print(pickLines.get(key) + "\n");
            }
          }
          finally {
            w.close();
          }
          return 0;
        }
        catch (Exception ex) {
          LOG.error("Editor failed: ", ex);
          return 1;
        }
      }
      else {
        return super.editCommits(path);
      }
    }
  }


}
