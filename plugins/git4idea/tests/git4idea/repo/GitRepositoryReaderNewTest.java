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
package git4idea.repo;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchUtil;
import git4idea.test.GitSingleRepoTest;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static git4idea.test.GitExecutor.git;
import static git4idea.test.GitExecutor.last;
import static git4idea.test.GitScenarios.commit;
import static git4idea.test.GitScenarios.conflict;
import static git4idea.test.GitTestUtil.makeCommit;

/**
 * {@link GitRepositoryReaderTest} reads information from the pre-created .git directory from a real project.
 * This one, on the other hand, operates on a live Git repository, putting it to various situations and checking the results.
 */
public class GitRepositoryReaderNewTest extends GitSingleRepoTest {

  // inspired by IDEA-93806
  public void test_rebase_with_conflicts_while_being_on_detached_HEAD() throws IOException {
    makeCommit("file.txt");
    conflict(myRepo, "feature");
    commit(myRepo);
    commit(myRepo);
    git("checkout HEAD^");
    git("rebase feature", true);

    GitBranchState state = readState();
    assertNull("Current branch can't be identified for this case", state.getCurrentBranch());
    assertEquals("State value is incorrect", Repository.State.REBASING, state.getState());
  }

  // inspired by IDEA-124052
  public void test_remote_reference_without_remote() throws IOException {
    makeCommit("file.txt");
    final String INVALID_REMOTE = "invalid-remote";
    final String INVALID_REMOTE_BRANCH = "master";
    git("update-ref refs/remotes/" + INVALID_REMOTE + "/" + INVALID_REMOTE_BRANCH + " HEAD");

    Collection<GitRemoteBranch> remoteBranches = readState().getRemoteBranches().keySet();
    assertTrue("Remote branch not found", ContainerUtil.exists(remoteBranches, new Condition<GitRemoteBranch>() {
      @Override
      public boolean value(GitRemoteBranch branch) {
        return branch.getNameForLocalOperations().equals(INVALID_REMOTE + "/" + INVALID_REMOTE_BRANCH);
      }
    }));
  }

  // inspired by IDEA-134286
  public void test_detached_HEAD() throws IOException {
    String head = getToDetachedHead();
    GitBranchState state = readState();
    assertEquals("Detached HEAD is not detected", GitRepository.State.DETACHED, state.getState());
    assertEquals("Detached HEAD hash is incorrect", head, state.getCurrentRevision());
  }

  // inspired by IDEA-135966
  public void test_no_local_branches() throws IOException {
    String head = getToDetachedHead();
    git("branch -D master");
    GitBranchState state = readState();
    assertEquals("Detached HEAD is not detected", GitRepository.State.DETACHED, state.getState());
    assertEquals("Detached HEAD hash is incorrect", head, state.getCurrentRevision());
    assertTrue("There should be no local branches", state.getLocalBranches().isEmpty());
  }

  public void test_tracking_remote_with_complex_name() throws IOException {
    makeCommit("file.txt");
    git("remote add my/remote http://my.remote.git");
    git("update-ref refs/remotes/my/remote/master HEAD");
    git("config branch.master.remote my/remote");
    git("config branch.master.merge refs/heads/master");
    myRepo.update();


    GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(myRepo, ObjectUtils.assertNotNull(myRepo.getCurrentBranch()));
    assertNotNull(trackInfo);
    GitRemote remote = trackInfo.getRemote();
    assertEquals("my/remote", remote.getName());
    assertEquals("http://my.remote.git", remote.getFirstUrl());
  }

  @NotNull
  private static String getToDetachedHead() throws IOException {
    makeCommit("file.txt");
    makeCommit("file.txt");
    git("checkout HEAD^");
    return last();
  }

  @NotNull
  private GitBranchState readState() {
    VirtualFile gitDir = myRepo.getGitDir();
    GitConfig config = GitConfig.read(myPlatformFacade, new File(gitDir.getPath(), "config"));
    GitRepositoryReader reader = new GitRepositoryReader(GitRepositoryFiles.getInstance(gitDir));
    Collection<GitRemote> remotes = config.parseRemotes();
    return reader.readState(remotes);
  }

  // inspired by IDEA-134412
  public void test_fresh_repository_is_on_branch() {
    GitLocalBranch currentBranch = readState().getCurrentBranch();
    assertNotNull("Current branch shouldn't be null in a fresh repository", currentBranch);
    assertEquals("Fresh repository should be on master", "master", currentBranch.getName());
  }

  @Override
  protected boolean makeInitialCommit() {
    return false;
  }
}
