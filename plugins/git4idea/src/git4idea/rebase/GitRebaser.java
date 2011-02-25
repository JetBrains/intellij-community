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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.merge.GitMergeConflictResolver;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Kirill Likhodedov
 */
public class GitRebaser {

  private final Project myProject;
  private GitVcs myVcs;
  private final AbstractVcsHelper myVcsHelper;
  private List<GitRebaseUtils.CommitInfo> mySkippedCommits;

  public GitRebaser(Project project) {
    myProject = project;
    myVcs = GitVcs.getInstance(project);
    myVcsHelper = AbstractVcsHelper.getInstance(project);
    mySkippedCommits = new ArrayList<GitRebaseUtils.CommitInfo>();
  }

  public void abortRebase(VirtualFile root) {
    final GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE);
    rh.addParameters("--abort");
    GitTask task = new GitTask(myProject, rh, "Aborting rebase");
    task.executeAsync(new GitTaskResultNotificationHandler(myProject, "Rebase aborted", "Abort rebase cancelled", "Error aborting rebase"));
  }

  public boolean continueRebase(@NotNull VirtualFile root) {
    return continueRebase(root, "--continue");
  }

  private boolean continueRebase(final @NotNull VirtualFile root, String startOperation) {
    final GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE);
    rh.addParameters(startOperation);
    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    rh.addLineListener(rebaseConflictDetector);

    final GitTask rebaseTask = new GitTask(myProject, rh, "git rebase " + startOperation);
    rebaseTask.setExecuteResultInAwt(false);
    rebaseTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    final AtomicBoolean result = new AtomicBoolean();
    rebaseTask.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        result.set(true);
      }

      @Override protected void onCancel() {
        result.set(false);
      }

      @Override protected void onFailure() {
        if (rebaseConflictDetector.isMergeConflict()) {
          result.set(new GitMergeConflictResolver(myProject, true, "Merge conflicts detected. Resolve them before continuing rebase.",
                                                  "Can't continue rebase", "Then you may <b>continue rebase</b>. <br/> You also may <b>abort rebase</b> to restore the original branch and stop rebasing.") {
            @Override protected boolean proceedIfNothingToMerge() {
              return continueRebase(root, "--continue");
            }

            @Override protected boolean proceedAfterAllMerged() {
              return continueRebase(root, "--continue");
            }
          }.mergeFiles(Collections.singleton(root)));
        } else if (rebaseConflictDetector.isNoChangeError()) {
          mySkippedCommits.add(GitRebaseUtils.getCurrentRebaseCommit(root));
          result.set(continueRebase(root, "--skip"));
        } else {
          result.set(false);
          GitUIUtil.notifyImportantError(myProject, "Error rebasing", GitUIUtil.stringifyErrors(rh.errors()));
        }
      }
    });
    return result.get();
  }

  public boolean continueRebase(Collection<VirtualFile> rebasingRoots) {
    boolean success = true;
    for (VirtualFile root : rebasingRoots) {
      success &= continueRebase(root);
    }
    return success;
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

}
