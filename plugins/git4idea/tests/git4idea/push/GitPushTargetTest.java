/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.push;

import com.intellij.util.ObjectUtils;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.test.GitSingleRepoTest;
import org.jetbrains.annotations.NotNull;

import static git4idea.test.GitExecutor.git;

public class GitPushTargetTest extends GitSingleRepoTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    addRemote("origin");
  }

  public void test_no_push_spec() {
    GitPushTarget target = GitPushTarget.getFromPushSpec(myRepo, ObjectUtils.assertNotNull(myRepo.getCurrentBranch()));
    assertNull(target);
  }

  public void test_refs_for_master() {
    setPushSpec("origin", "HEAD:refs/for/master");
    assertSpecialTargetRef("refs/for/master", "origin");
  }

  public void test_wildcard_special_ref() {
    setPushSpec("origin", "refs/heads/*:refs/for/*");
    assertSpecialTargetRef("refs/for/master", "origin");
  }

  public void test_complex_remote_name() {
    addRemote("my/remote");
    setPushSpec("my/remote", "HEAD:refs/for/master");
    setTracking("master", "my/remote", "refs/heads/master");

    assertSpecialTargetRef("refs/for/master", "my/remote");
  }

  public void test_standard_fetch_refspec() {
    setPushSpec("origin", "refs/heads/*:refs/remotes/origin/*");
    assertStandardRemoteBranch("master",
                               ObjectUtils.assertNotNull(myRepo.getBranches().findBranchByName("origin/master")));
  }

  public void test_tracked_remote_is_preferable_over_origin() {
    addRemote("github");
    setPushSpec("origin", "HEAD:refs/for/origin");
    setPushSpec("github", "HEAD:refs/for/github");
    setTracking("master", "github", "refs/heads/master");

    assertSpecialTargetRef("refs/for/github", "github");
  }

  private void addRemote(@NotNull String remoteName) {
    git(String.format("remote add %s http://example.git", remoteName));
    git(String.format("update-ref refs/remotes/%s/master HEAD", remoteName));
    myRepo.update();
  }

  private void setPushSpec(@NotNull String remote, @NotNull String pushSpec) {
    git(String.format("config remote.%s.push %s", remote, pushSpec));
    myRepo.update();
  }

  private void setTracking(@NotNull String branch, @NotNull String remote, @NotNull String remoteBranch) {
    git(String.format("config branch.%s.remote %s", branch, remote));
    git(String.format("config branch.%s.merge %s", branch, remoteBranch));
    myRepo.update();
  }

  private void assertSpecialTargetRef(@NotNull String expectedRefName, @NotNull String expectedRemoteName) {
    GitPushTarget target = GitPushTarget.getFromPushSpec(myRepo, ObjectUtils.assertNotNull(myRepo.getCurrentBranch()));
    assertNotNull(target);
    assertTrue(target.isSpecialRef());
    assertEquals(expectedRefName, target.getPresentation());
    GitRemoteBranch remoteBranch = target.getBranch();
    assertRemoteBranch(expectedRefName, expectedRefName, expectedRemoteName, remoteBranch);
  }

  private static void assertRemoteBranch(@NotNull String nameForLocalOperations,
                                         @NotNull String nameForRemoteOperations,
                                         @NotNull String remoteName,
                                         @NotNull GitRemoteBranch actualRemoteBranch) {
    assertEquals(nameForLocalOperations, actualRemoteBranch.getNameForLocalOperations());
    assertEquals(nameForRemoteOperations, actualRemoteBranch.getNameForRemoteOperations());
    assertEquals(remoteName, actualRemoteBranch.getRemote().getName());
  }
  private void assertStandardRemoteBranch(@NotNull String expectedPresentation, @NotNull GitBranch expectedBranch) {
    GitPushTarget target = GitPushTarget.getFromPushSpec(myRepo, ObjectUtils.assertNotNull(myRepo.getCurrentBranch()));
    assertNotNull(target);
    assertFalse(target.isSpecialRef());
    assertEquals(expectedPresentation, target.getPresentation());
    GitRemoteBranch remoteBranch = target.getBranch();
    assertEquals(expectedBranch, remoteBranch);
  }
}