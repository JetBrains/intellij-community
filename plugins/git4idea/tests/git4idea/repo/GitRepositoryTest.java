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
package git4idea.repo;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VfsUtil;
import git4idea.GitBranch;
import git4idea.GitPlatformFacade;
import git4idea.test.GitLightTest;
import git4idea.test.GitTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.dvcs.repo.Repository.State.*;
import static org.testng.Assert.*;

/**
 * Tests {@link GitRepositoryReader} and {@link GitRepository} together with {@link GitRepositoryUpdater}:
 * <ol>
 * <li>Puts the Git repository into a state: commit, rebase, make an unresolved conflicted merge/rebase, etc.</li>
 * <li>Checks that {@link GitRepositoryReader} correctly reads information.</li>
 * <li>Checks that {@link GitRepository} is updated and a listener is notified about the change.</li>
 * </ol>
 * @author Kirill Likhodedov
 * @deprecated Use {@link GitLightTest}
 */
@Deprecated
public class GitRepositoryTest extends GitTest {

  private GitRepositoryReader myReader;
  private GitRepository myRepository;

  @BeforeMethod
  public void setUp(Method testMethod) throws Exception {
    super.setUp(testMethod);
    GitPlatformFacade facade = ServiceManager.getService(myProject, GitPlatformFacade.class);
    myRepository = GitRepositoryImpl.getFullInstance(myRepo.getVFRootDir(), myProject, facade, myProject);
    myReader = new GitRepositoryReader(new File(VfsUtil.virtualToIoFile(myRepository.getRoot()), ".git"));
  }

  @Test
  public void initiallyNullOnMaster() {
    assertReaderAndRepo(NORMAL, null, "master", false);
  }

  @Test
  public void firstCommit() throws IOException {
    String curRev = myRepo.createAddCommit();
    assertMaster(curRev);
  }

  @Test
  public void twoCommits() throws IOException {
    myRepo.createAddCommit();
    String curRev = myRepo.createAddCommit();
    assertMaster(curRev);
  }

  @Test
  public void resetReturnsCommit() throws IOException {
    String firstRev = myRepo.createAddCommit();
    myRepo.createAddCommit();
    myRepo.run("reset", "HEAD^");
    assertMaster(firstRev);
  }

  @Test
  public void createBranchChangesNothing() throws IOException {
    String firstRev = myRepo.createAddCommit();
    myRepo.branch("feature");
    assertMaster(firstRev);
  }

  @Test
  public void createAndCheckoutChangesBranchButNotCommit() throws IOException {
    String firstRev = myRepo.createAddCommit();
    myRepo.run("checkout", "-b", "feature");
    assertNormal(firstRev, "feature");
  }

  @Test
  public void checkoutBranchChangesBranchAndCommit() throws IOException {
    myRepo.createAddCommit();
    myRepo.run("checkout", "-b", "feature");
    myRepo.createAddCommit();
  }

  @Test
  public void fastForwardMerge() throws IOException {
    myRepo.createAddCommit();
    myRepo.run("checkout", "-b", "feature");
    String rev = myRepo.createAddCommit();
    myRepo.checkout("master");
    myRepo.merge("feature");
    assertMaster(rev);
  }

  @Test
  public void mergeCommitChangesLastRevision() throws IOException {
    myRepo.createAddCommit();
    myRepo.run("checkout", "-b", "feature");
    myRepo.createAddCommit();
    myRepo.checkout("master");
    myRepo.createAddCommit();
    myRepo.merge("feature");
    String rev = myRepo.lastCommit();
    assertMaster(rev);
  }

  @Test
  public void conflictingMergeInProgressLeavesCurrentRevisionChangesState() throws IOException {
    myRepo.createAddCommit();
    myRepo.run("checkout", "-b", "feature");
    myRepo.createFile("new.txt", "feature content");
    myRepo.addCommit();
    myRepo.checkout("master");
    myRepo.createFile("new.txt", "master content");
    myRepo.addCommit();
    myRepo.merge("feature");
    String rev = myRepo.lastCommit();
    assertReaderAndRepo(MERGING, rev, "master");
  }

  @Test
  public void checkoutCommitMakesDetachedState() throws IOException {
    myRepo.createAddCommit();
    String secondRev = myRepo.createAddCommit();
    myRepo.createAddCommit();
    myRepo.checkout(secondRev);
    assertReaderAndRepo(DETACHED, secondRev, null);
  }

  @Test
  public void fastForwardRebase() throws IOException {
    myRepo.createAddCommit();
    myRepo.run("checkout", "-b", "feature");
    String rev = myRepo.createAddCommit();
    myRepo.checkout("master");
    myRepo.rebase("feature");
    assertMaster(rev);
  }

  @Test
  public void rebase() throws IOException {
    myRepo.createAddCommit();
    myRepo.run("checkout", "-b", "feature");
    myRepo.createAddCommit();
    myRepo.checkout("master");
    myRepo.createAddCommit();
    myRepo.rebase("feature");
    String rev = myRepo.lastCommit();
    assertMaster(rev);
  }

  @Test
  public void conflictingRebaseInProgress() throws IOException {
    myRepo.createAddCommit();
    myRepo.run("checkout", "-b", "feature");
    myRepo.createFile("new.txt", "feature content");
    myRepo.addCommit();
    myRepo.checkout("master");
    myRepo.createFile("new.txt", "master content");
    myRepo.addCommit();
    myRepo.rebase("feature");
    String rev = myRepo.lastCommit();
    assertReaderAndRepo(REBASING, rev, "master");
  }

  @Test
  public void fetchChangesNothing() throws IOException {
    String firstRev = myRepo.createAddCommit();
    myBrotherRepo.createAddCommit();
    myBrotherRepo.push();
    myRepo.fetch();
    assertMaster(firstRev);
  }

  @Test
  public void addChangesNothing() throws IOException {
    String firstRev = myRepo.createAddCommit();
    myRepo.createFile("new.txt", "master content");
    myRepo.add();
    assertMaster(firstRev);
  }

  @Test
  public void stashChangesNothing() throws IOException {
    String firstRev = myRepo.createAddCommit();
    myRepo.createFile("new.txt", "master content");
    myRepo.add();
    myRepo.stash();
    assertMaster(firstRev);
  }

  @Test
  public void unstashChangesNothing() throws IOException {
    myRepo.createAddCommit();
    myRepo.createFile("new.txt", "master content");
    myRepo.add();
    myRepo.stash();
    String rev = myRepo.createAddCommit();
    myRepo.unstash();
    assertMaster(rev);
  }

  private void assertMaster(@Nullable String curRev) {
    assertNormal(curRev, "master");
  }

  private void assertNormal(String curRev, String branch) {
    assertReaderAndRepo(NORMAL, curRev, branch);
  }

  private void assertReaderAndRepo(@NotNull GitRepository.State state, @Nullable String curRev, @Nullable String branch) {
    assertReaderAndRepo(state, curRev, branch, true);
  }

  private void assertReaderAndRepo(@NotNull GitRepository.State state, @Nullable String curRev, @Nullable String branch, boolean waitForEvent) {
    assertReader(state, curRev, branch);

    if (waitForEvent) {
      final AtomicBoolean repoChanged = new AtomicBoolean();
      final Object LOCK = new Object();
      myProject.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
        @Override
        public void repositoryChanged(@NotNull GitRepository repository) {
          repoChanged.set(true);
          synchronized (LOCK) {
            LOCK.notifyAll();
          }
        }
      });
      myRepo.refresh();
      synchronized (LOCK) {
        try {
          LOCK.wait(2000);
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      if (!repoChanged.get()) {
        fail("Repository change event wasn't received.");
      }
    }

    assertRepo(state, curRev, branch);
  }

  private void assertReader(GitRepository.State state, String curRev, String branch) {
    assertEquals(myReader.readState(), state);
    assertEquals(myReader.readCurrentRevision(), curRev);
    final GitBranch curBranch = myReader.readCurrentBranch();
    if (branch == null) {
      assertNull(curBranch);
    } else {
      assertNotNull(curBranch);
      assertEquals(curBranch.getName(), branch);
    }
  }

  private void assertRepo(GitRepository.State state, String curRev, String branch) {
    assertEquals(myRepository.getState(), state);
    assertEquals(myRepository.getCurrentRevision(), curRev);
    final GitBranch curBranch = myRepository.getCurrentBranch();
    if (branch == null) {
      assertNull(curBranch);
    } else {
      assertNotNull(curBranch);
      assertEquals(curBranch.getName(), branch);
    }
  }

}
