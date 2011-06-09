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
package git4idea.rebase;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.merge.GitMergeConflictResolver;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Kirill Likhodedov
 */
public class GitRebaser {

  private final Project myProject;
  private GitVcs myVcs;
  private List<GitRebaseUtils.CommitInfo> mySkippedCommits;
  private static final Logger LOG = Logger.getInstance(GitRebaser.class);
  private final @Nullable ProgressIndicator myProgressIndicator;

  public GitRebaser(Project project, ProgressIndicator progressIndicator) {
    myProject = project;
    myProgressIndicator = progressIndicator;
    myVcs = GitVcs.getInstance(project);
    mySkippedCommits = new ArrayList<GitRebaseUtils.CommitInfo>();
  }

  public void abortRebase(@NotNull VirtualFile root) {
    LOG.info("abortRebase " + root);
    final GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE);
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
    boolean success = true;
    for (VirtualFile root : rebasingRoots) {
      success &= continueRebase(root);
    }
    return success;
  }

  // start operation may be "--continue" or "--skip" depending on the situation.
  private boolean continueRebase(final @NotNull VirtualFile root, @NotNull String startOperation) {
    LOG.info("continueRebase " + root + " " + startOperation);
    final GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE);
    rh.addParameters(startOperation);
    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    rh.addLineListener(rebaseConflictDetector);

    final GitTask rebaseTask = new GitTask(myProject, rh, "git rebase " + startOperation);
    rebaseTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    rebaseTask.setProgressIndicator(myProgressIndicator);
    return executeRebaseTaskInBackground(root, rh, rebaseConflictDetector, rebaseTask);
  }

  /**
   * @return Roots which have unfinished rebase process. May be empty.
   */
  public @NotNull Collection<VirtualFile> getRebasingRoots() {
    final Collection<VirtualFile> rebasingRoots = new HashSet<VirtualFile>();
    for (VirtualFile root : ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(myVcs)) {
      if (GitRebaseUtils.isRebaseInTheProgress(root)) {
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
    List<String> allCommits = new ArrayList<String>(); //TODO
    if (olderCommits.isEmpty() || olderCommits.size() == allCommits.size()) {
      LOG.info("Nothing to reorder. olderCommits: " + olderCommits + " allCommits: " + allCommits);
      return true;
    }

    final GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.REBASE);
    Integer rebaseEditorNo = null;
    GitRebaseEditorService rebaseEditorService = GitRebaseEditorService.getInstance();
    try {
      h.addParameters("-i", "-m", "-v");
      h.addParameters(parentCommit);

      final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
      h.addLineListener(rebaseConflictDetector);

      final PushRebaseEditor pushRebaseEditor = new PushRebaseEditor(rebaseEditorService, root, olderCommits, false, h);
      rebaseEditorNo = pushRebaseEditor.getHandlerNo();
      rebaseEditorService.configureHandler(h, rebaseEditorNo);

      final GitTask rebaseTask = new GitTask(myProject, h, "Reordering commits");
      rebaseTask.setProgressIndicator(myProgressIndicator);
      return executeRebaseTaskInBackground(root, h, rebaseConflictDetector, rebaseTask);
    } finally {
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

  private boolean handleRebaseFailure(final VirtualFile root, final GitLineHandler h, GitRebaseProblemDetector rebaseConflictDetector) {
    if (rebaseConflictDetector.isMergeConflict()) {
      LOG.info("handleRebaseFailure merge conflict");
      return new GitMergeConflictResolver(myProject, true, "Merge conflicts detected. Resolve them before continuing rebase.",
                                              "Can't continue rebase", "Then you may <b>continue rebase</b>. <br/> You also may <b>abort rebase</b> to restore the original branch and stop rebasing.") {
        @Override protected boolean proceedIfNothingToMerge() {
          return continueRebase(root, "--continue");
        }

        @Override protected boolean proceedAfterAllMerged() {
          return continueRebase(root, "--continue");
        }
      }.merge(Collections.singleton(root));
    } else if (rebaseConflictDetector.isNoChangeError()) {
      LOG.info("handleRebaseFailure no change");
      mySkippedCommits.add(GitRebaseUtils.getCurrentRebaseCommit(root));
      return continueRebase(root, "--skip");
    } else {
      LOG.info("handleRebaseFailure error " + h.errors());
      GitUIUtil.notifyImportantError(myProject, "Error rebasing", GitUIUtil.stringifyErrors(h.errors()));
      return false;
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
                            boolean hasMerges,
                            GitHandler h) {
      super(rebaseEditorService, myProject, root, h);
      myCommits = commits;
      myHasMerges = hasMerges;
    }

    public int editCommits(String path) {
      if (!myRebaseEditorShown) {
        myRebaseEditorShown = true;
        if (myHasMerges) {
          return 0;
        }
        try {
          TreeMap<String, String> pickLines = new TreeMap<String, String>();
          StringScanner s = new StringScanner(new String(FileUtil.loadFileText(new File(path), GitUtil.UTF8_ENCODING)));
          while (s.hasMoreData()) {
            if (!s.tryConsume("pick ")) {
              s.line();
              continue;
            }
            String commit = s.spaceToken();
            pickLines.put(commit, "pick " + commit + " " + s.line());
          }
          PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(path), GitUtil.UTF8_ENCODING));
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
