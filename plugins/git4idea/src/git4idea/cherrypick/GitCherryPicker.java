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
package git4idea.cherrypick;

import com.intellij.dvcs.cherrypick.VcsCherryPicker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import git4idea.GitApplyChangesProcess;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandlerListener;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class GitCherryPicker extends VcsCherryPicker {

  private static final Logger LOG = Logger.getInstance(GitCherryPicker.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  public GitCherryPicker(@NotNull Project project, @NotNull Git git) {
    myProject = project;
    myGit = git;
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
  }

  public void cherryPick(@NotNull List<VcsFullCommitDetails> commits) {
    GitApplyChangesProcess applyProcess = new GitApplyChangesProcess(myProject, commits, isAutoCommit(), "cherry-pick", "applied",
                                                                     (repository, commit, autoCommit, listeners) ->
      myGit.cherryPick(repository, commit.asString(), autoCommit, ArrayUtil.toObjectArray(listeners, GitLineHandlerListener.class)),
      result -> isNothingToCommitMessage(result),
      commit -> createCommitMessage(commit),
      originalChanges -> GitUtil.findCorrespondentLocalChanges(ChangeListManager.getInstance(myProject), originalChanges),
      true,
      repository -> cancelCherryPick(repository));
    applyProcess.execute();
  }

  private static boolean isNothingToCommitMessage(@NotNull GitCommandResult result) {
    String stdout = result.getOutputAsJoinedString();
    return stdout.contains("nothing to commit") || stdout.contains("previous cherry-pick is now empty");
  }

  @NotNull
  private static String createCommitMessage(@NotNull VcsFullCommitDetails commit) {
    return commit.getFullMessage() + "\n\n(cherry picked from commit " + commit.getId().toShortString() + ")";
  }

  /**
   * We control the cherry-pick workflow ourselves + we want to use partial commits ('git commit --only'), which is prohibited during
   * cherry-pick, i.e. until the CHERRY_PICK_HEAD exists.
   */
  private Unit cancelCherryPick(@NotNull GitRepository repository) {
    if (isAutoCommit()) {
      removeCherryPickHead(repository);
    }
    return Unit.INSTANCE;
  }

  private static void removeCherryPickHead(@NotNull GitRepository repository) {
    File cherryPickHeadFile = repository.getRepositoryFiles().getCherryPickHead();
    if (cherryPickHeadFile.exists()) {
      boolean deleted = FileUtil.delete(cherryPickHeadFile);
      if (!deleted) {
        LOG.warn("Couldn't delete " + cherryPickHeadFile);
      }
    }
    else {
      LOG.info("Cancel cherry-pick in " + repository.getPresentableUrl() + ": no CHERRY_PICK_HEAD found");
    }
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return GitVcs.getKey();
  }

  @NotNull
  @Override
  public String getActionTitle() {
    return "Cherry-Pick";
  }

  private boolean isAutoCommit() {
    return GitVcsSettings.getInstance(myProject).isAutoCommitOnCherryPick();
  }

  @Override
  public boolean canHandleForRoots(@NotNull Collection<VirtualFile> roots) {
    return roots.stream().allMatch(r -> myRepositoryManager.getRepositoryForRoot(r) != null);
  }
}
