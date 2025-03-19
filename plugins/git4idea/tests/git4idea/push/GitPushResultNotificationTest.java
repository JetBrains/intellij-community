// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.GitTag;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.test.GitPlatformTest;
import git4idea.test.MockGitRepository;
import git4idea.update.GitUpdateResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.vcs.test.UtilsKt.assertNotification;
import static git4idea.push.GitPushNativeResult.Type.*;
import static git4idea.push.GitPushRepoResult.convertFromNative;
import static git4idea.push.GitPushResultNotification.emulateTitle;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class GitPushResultNotificationTest extends GitPlatformTest {
  private static final String UPDATE_WITH_RESOLVED_CONFLICTS = GitBundle.message("push.notification.description.rejected.and.conflicts");

  private static Project ourProject; // for static map initialization

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ourProject = myProject;
  }

  @Override
  public void tearDown() {
    ourProject = null;
    super.tearDown();
  }

  public void test_single_success() {
    GitPushResultNotification notification = notification(singleResult(SUCCESS, "master", "origin/master", 1));
    assertPushNotification(NotificationType.INFORMATION, "Pushed 1 commit to origin/master", "", notification);
  }

  public void test_pushed_new_branch() {
    GitPushResultNotification notification = notification(singleResult(NEW_REF, "feature", "origin/feature", -1));
    assertPushNotification(NotificationType.INFORMATION, "Pushed feature to new branch origin/feature", "", notification);
  }

  public void test_force_pushed() {
    GitPushResultNotification notification = notification(singleResult(FORCED_UPDATE, "feature", "origin/feature", -1));
    assertPushNotification(NotificationType.INFORMATION, "Force pushed feature to origin/feature", "", notification);
  }

  public void test_success_and_fail() {
    GitPushResultNotification notification = notification(new HashMap<>() {{
      put(repo("ultimate"), repoResult(SUCCESS, "master", "origin/master", 1));
      put(repo("community"), repoResult(ERROR, "master", "origin/master", "Permission denied"));
    }});
    assertPushNotification(NotificationType.ERROR, "Push partially failed",
                           "ultimate: Pushed 1 commit to origin/master<br/>" +
                           "community: Permission denied", notification);
  }

  public void test_success_and_reject() {
    GitPushResultNotification notification = notification(new HashMap<>() {{
      put(repo("ultimate"), repoResult(SUCCESS, "master", "origin/master", 1));
      put(repo("community"), repoResult(REJECTED, "master", "origin/master", -1));
    }});
    assertPushNotification(NotificationType.WARNING, "Push partially rejected",
                           "ultimate: Pushed 1 commit to origin/master<br/>" +
                           "community: Push to origin/master was rejected", notification);
  }

  public void test_success_with_update() {
    GitPushResultNotification notification = notification(singleResult(SUCCESS, "master", "origin/master", 2, GitUpdateResult.SUCCESS));
    assertPushNotification(NotificationType.INFORMATION, "Pushed 2 commits to origin/master", "", notification);
  }

  public void test_success_and_resolved_conflicts() {
    GitPushResultNotification notification = notification(new HashMap<>() {{
      put(repo("community"), repoResult(REJECTED, "master", "origin/master", -1, GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS));
      put(repo("contrib"), repoResult(REJECTED, "master", "origin/master", -1, GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS));
      put(repo("ultimate"), repoResult(SUCCESS, "master", "origin/master", 1));
    }});
    assertPushNotification(NotificationType.WARNING, "Push partially rejected",
                           "ultimate: Pushed 1 commit to origin/master<br/>" +
                           "community: " + UPDATE_WITH_RESOLVED_CONFLICTS + "<br/>" +
                           "contrib: " + UPDATE_WITH_RESOLVED_CONFLICTS,
                           notification);
  }

  public void test_commits_and_tags() {
    GitPushNativeResult branchResult = new GitPushNativeResult(SUCCESS, "refs/heads/master");
    GitPushNativeResult tagResult = new GitPushNativeResult(NEW_REF, "refs/tags/v0.1");
    GitPushResultNotification notification = notification(convertFromNative(branchResult, singletonList(tagResult), 1,
                                                                            from("master"), to("origin/master")));
    assertPushNotification(NotificationType.INFORMATION, "Pushed 1 commit to origin/master, and tag v0.1 to origin", "", notification);
  }

  public void test_nothing() {
    GitPushNativeResult branchResult = new GitPushNativeResult(UP_TO_DATE, "refs/heads/master");
    GitPushResultNotification notification = notification(convertFromNative(branchResult, Collections.emptyList(),
                                                                            0, from("master"), to("origin/master")));
    assertPushNotification(NotificationType.INFORMATION, "Everything is up to date", "", notification);
  }

  public void test_only_tags() {
    GitPushNativeResult branchResult = new GitPushNativeResult(UP_TO_DATE, "refs/heads/master");
    GitPushNativeResult tagResult = new GitPushNativeResult(NEW_REF, "refs/tags/v0.1");
    GitPushResultNotification notification = notification(convertFromNative(branchResult, singletonList(tagResult), 0,
                                                                            from("master"), to("origin/master")));
    assertPushNotification(NotificationType.INFORMATION, "Pushed tag v0.1 to origin", "", notification);
  }

  public void test_two_repo_with_tags() {
    GitPushNativeResult branchSuccess = new GitPushNativeResult(SUCCESS, "refs/heads/master");
    GitPushNativeResult branchUpToDate = new GitPushNativeResult(UP_TO_DATE, "refs/heads/master");
    GitPushNativeResult tagResult = new GitPushNativeResult(NEW_REF, "refs/tags/v0.1");
    final GitPushRepoResult comRes = convertFromNative(branchSuccess, singletonList(tagResult), 1, from("master"), to("origin/master"));
    final GitPushRepoResult ultRes = convertFromNative(branchUpToDate, singletonList(tagResult), 0, from("master"), to("origin/master"));

    GitPushResultNotification notification = notification(new HashMap<>() {{
      put(repo("community"), comRes);
      put(repo("ultimate"), ultRes);
    }});

    assertPushNotification(NotificationType.INFORMATION, "Push successful",
                           "community: Pushed 1 commit to origin/master, and tag v0.1 to origin<br/>" +
                           "ultimate: Pushed tag v0.1 to origin", notification);
  }

  public void test_two_tags() {
    GitPushNativeResult branchResult = new GitPushNativeResult(UP_TO_DATE, "refs/heads/master");
    GitPushNativeResult tag1 = new GitPushNativeResult(NEW_REF, "refs/tags/v0.1");
    GitPushNativeResult tag2 = new GitPushNativeResult(NEW_REF, "refs/tags/v0.2");
    GitPushResultNotification notification = notification(convertFromNative(branchResult, asList(tag1, tag2), 0,
                                                                            from("master"), to("origin/master")));
    assertPushNotification(NotificationType.INFORMATION, "Pushed 2 tags to origin", "", notification);
  }

  public void test_tag_no_commits() {
    GitPushResultNotification notification = pushSingleTagNotification(NEW_REF);
    assertPushNotification(NotificationType.INFORMATION, "Pushed tag v0.1 to origin", "", notification);
  }

  public void test_tag_no_commits_up_to_date() {
    GitPushResultNotification notification = pushSingleTagNotification(UP_TO_DATE);
    assertPushNotification(NotificationType.INFORMATION, "Everything is up to date", "", notification);
  }

  public void test_tag_no_commits_already_exists() {
    GitPushResultNotification notification = pushSingleTagNotification(REJECTED);
    assertPushNotification(NotificationType.WARNING, "Push rejected", "Push of v0.1 was rejected by the remote", notification);
  }

  private GitPushResultNotification pushSingleTagNotification(GitPushNativeResult.Type upToDate) {
    String tagRef = "refs/tags/v0.1";
    GitPushNativeResult nativeResult = new GitPushNativeResult(upToDate, tagRef);
    GitPushRepoResult result = GitPushRepoResult.tagPushResult(nativeResult,
                                                               new GitPushSource.Tag(new GitTag(tagRef)),
                                                               new GitSpecialRefRemoteBranch(tagRef, remote("origin")));
    GitPushResultNotification notification = notification(result);
    return notification;
  }

  private static Map<GitRepository, GitPushRepoResult> singleResult(final GitPushNativeResult.Type type,
                                                                    final String from,
                                                                    final String to,
                                                                    final int commits,
                                                                    @Nullable final GitUpdateResult updateResult) {
    return new HashMap<>() {{
      put(repo("community"), repoResult(type, from, to, commits, updateResult));
    }};
  }

  private static GitPushRepoResult repoResult(GitPushNativeResult.Type nativeType, String from, String to, int commits) {
    return repoResult(nativeType, from, to, commits, null);
  }

  private static GitPushRepoResult repoResult(GitPushNativeResult.Type nativeType, String from, String to, int commits,
                                              @Nullable GitUpdateResult updateResult) {
    String reason = nativeType == REJECTED ? GitPushNativeResult.FETCH_FIRST_REASON : null;
    GitPushNativeResult nr = new GitPushNativeResult(nativeType, from, reason, null);
    return GitPushRepoResult.addUpdateResult(
      convertFromNative(nr, Collections.emptyList(), commits, from(from), to(to)),
      updateResult);
  }

  private static Map<GitRepository, GitPushRepoResult> singleResult(final GitPushNativeResult.Type type,
                                                                    final String from,
                                                                    final String to, final int commits) {
    return singleResult(type, from, to, commits, null);
  }

  // keep params for unification
  @SuppressWarnings("UnusedParameters")
  private static GitPushRepoResult repoResult(GitPushNativeResult.Type nativeType, String from, String to, String errorText) {
    return GitPushRepoResult.error(from(from), to(to), errorText);
  }

  private static GitPushSource from(String from) {
    return GitPushSource.create(new GitLocalBranch(from));
  }

  private static GitRemoteBranch to(String to) {
    int firstSlash = to.indexOf('/');
    GitRemote remote = remote(to.substring(0, firstSlash));
    return new GitStandardRemoteBranch(remote, to.substring(firstSlash + 1));
  }

  private static @NotNull GitRemote remote(String name) {
    return new GitRemote(name, Collections.emptyList(), Collections.emptyList(),
                         Collections.emptyList(), Collections.emptyList());
  }

  private GitPushResultNotification notification(GitPushRepoResult singleResult) {
    return notification(Collections.singletonMap(repo("community"), singleResult));
  }

  private GitPushResultNotification notification(Map<GitRepository, GitPushRepoResult> map) {
    boolean wasUpdatePerformed = ContainerUtil.exists(map.values(), aNew -> aNew.getUpdateResult() != null);
    UpdatedFiles updatedFiles = UpdatedFiles.create();
    if (wasUpdatePerformed) {
      updatedFiles.getTopLevelGroups().get(0).add("file.txt", "Git", null);
    }
    Ref<GitPushResultNotification> ref = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ref.set(GitPushResultNotification.create(myProject, new GitPushResult(map, updatedFiles, null, null, Collections.emptyMap()),
                                               null, map.size() > 1, null, Collections.emptyMap()));
    });
    return ref.get();
  }

  private static void assertPushNotification(@NotNull NotificationType type,
                                             @NotNull String title,
                                             @NotNull String content,
                                             @NotNull GitPushResultNotification actual) {
    assertNotification(type, "", emulateTitle(title, content), actual);
  }

  private static MockGitRepository repo(final String name) {
    final Ref<VirtualFile> root = Ref.create();
    EdtTestUtil.runInEdtAndWait(() -> root.set(createChildData(PlatformTestUtil.getOrCreateProjectBaseDir(ourProject), name)));
    return new MockGitRepository(ourProject, root.get());
  }
}