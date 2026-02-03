// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.test.GitSingleRepoTest;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class GitPushTargetTest extends GitSingleRepoTest {
  @Override
  public void setUp() {
    super.setUp();
    addRemote("origin");
  }

  public void test_no_push_spec() {
    GitPushTarget target = GitPushTarget.getFromPushSpec(repo, Objects.requireNonNull(this.repo.getCurrentBranch()));
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
                               Objects.requireNonNull(this.repo.getBranches().findBranchByName("origin/master")));
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
    repo.update();
  }

  private void setPushSpec(@NotNull String remote, @NotNull String pushSpec) {
    git(String.format("config remote.%s.push %s", remote, pushSpec));
    repo.update();
  }

  private void setTracking(@NotNull String branch, @NotNull String remote, @NotNull String remoteBranch) {
    git(String.format("config branch.%s.remote %s", branch, remote));
    git(String.format("config branch.%s.merge %s", branch, remoteBranch));
    repo.update();
  }

  private void assertSpecialTargetRef(@NotNull String expectedRefName, @NotNull String expectedRemoteName) {
    GitPushTarget target = GitPushTarget.getFromPushSpec(repo, Objects.requireNonNull(this.repo.getCurrentBranch()));
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
    GitPushTarget target = GitPushTarget.getFromPushSpec(repo, Objects.requireNonNull(this.repo.getCurrentBranch()));
    assertNotNull(target);
    assertFalse(target.isSpecialRef());
    assertEquals(expectedPresentation, target.getPresentation());
    GitRemoteBranch remoteBranch = target.getBranch();
    assertEquals(expectedBranch, remoteBranch);
  }
}