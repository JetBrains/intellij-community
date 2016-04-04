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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.test.GitPlatformTest;
import git4idea.test.MockGitRepository;
import git4idea.update.GitUpdateResult;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static git4idea.push.GitPushNativeResult.Type.*;
import static git4idea.push.GitPushRepoResult.convertFromNative;
import static git4idea.test.GitTestUtil.assertNotification;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class GitPushResultNotificationTest extends GitPlatformTest {

  private static Project ourProject; // for static map initialization

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ourProject = myProject;
  }

  @Override
  public void tearDown() throws Exception {
    ourProject = null;
    super.tearDown();
  }

  public void test_single_success() {
    GitPushResultNotification notification = notification(singleResult(SUCCESS, "master", "origin/master", 1));
    assertNotification(NotificationType.INFORMATION, "Push successful", "Pushed 1 commit to origin/master", notification);
  }

  public void test_pushed_new_branch() {
    GitPushResultNotification notification = notification(singleResult(NEW_REF, "feature", "origin/feature", -1));
    assertNotification(NotificationType.INFORMATION, "Push successful", "Pushed feature to new branch origin/feature", notification);
  }

  public void test_force_pushed() {
    GitPushResultNotification notification = notification(singleResult(FORCED_UPDATE, "feature", "origin/feature", -1));
    assertNotification(NotificationType.INFORMATION, "Push successful", "Force pushed feature to origin/feature", notification);
  }

  public void test_success_and_fail() {
    GitPushResultNotification notification = notification(new HashMap<GitRepository, GitPushRepoResult>() {{
      put(repo("ultimate"), repoResult(SUCCESS, "master", "origin/master", 1));
      put(repo("community"), repoResult(ERROR, "master", "origin/master", "Permission denied"));
    }});
    assertNotification(NotificationType.ERROR, "Push partially failed",
                       "ultimate: pushed 1 commit to origin/master<br/>" +
                       "community: failed with error: Permission denied", notification);
  }

  public void test_success_and_reject() {
    GitPushResultNotification notification = notification(new HashMap<GitRepository, GitPushRepoResult>() {{
      put(repo("ultimate"), repoResult(SUCCESS, "master", "origin/master", 1));
      put(repo("community"), repoResult(REJECTED, "master", "origin/master", -1));
    }});
    assertNotification(NotificationType.WARNING, "Push partially rejected",
                       "ultimate: pushed 1 commit to origin/master<br/>" +
                       "community: push to origin/master was rejected", notification);
  }

  public void test_success_with_update() {
    GitPushResultNotification notification = notification(singleResult(SUCCESS, "master", "origin/master", 2, GitUpdateResult.SUCCESS));
    assertNotification(NotificationType.INFORMATION, "Push successful",
                       "Pushed 2 commits to origin/master<br/>" +
                       GitPushResultNotification.VIEW_FILES_UPDATED_DURING_THE_PUSH, notification);

  }

  public void test_success_and_resolved_conflicts() {
    GitPushResultNotification notification = notification(new HashMap<GitRepository, GitPushRepoResult>() {{
      put(repo("community"), repoResult(REJECTED, "master", "origin/master", -1, GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS));
      put(repo("contrib"), repoResult(REJECTED, "master", "origin/master", -1, GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS));
      put(repo("ultimate"), repoResult(SUCCESS, "master", "origin/master", 1));
    }});
    assertNotification(NotificationType.WARNING, "Push partially rejected",
                       "ultimate: pushed 1 commit to origin/master<br/>" +
                       "community: " + GitPushResultNotification.UPDATE_WITH_RESOLVED_CONFLICTS + "<br/>" +
                       "contrib: " + GitPushResultNotification.UPDATE_WITH_RESOLVED_CONFLICTS + "<br/>" +
                       GitPushResultNotification.VIEW_FILES_UPDATED_DURING_THE_PUSH, notification);

  }

  public void test_commits_and_tags() {
    GitPushNativeResult branchResult = new GitPushNativeResult(SUCCESS, "refs/heads/master");
    GitPushNativeResult tagResult = new GitPushNativeResult(NEW_REF, "refs/tags/v0.1");
    GitPushResultNotification notification = notification(convertFromNative(branchResult, singletonList(tagResult), 1,
                                                                            from("master"), to("origin/master")));
    assertNotification(NotificationType.INFORMATION, "Push successful",
                                   "Pushed 1 commit to origin/master, and tag v0.1 to origin", notification);
  }

  public void test_nothing() {
    GitPushNativeResult branchResult = new GitPushNativeResult(UP_TO_DATE, "refs/heads/master");
    GitPushResultNotification notification = notification(convertFromNative(branchResult, Collections.<GitPushNativeResult>emptyList(),
                                                                            0, from("master"), to("origin/master")));
    assertNotification(NotificationType.INFORMATION, "Push successful", "Everything is up-to-date", notification);
  }

  public void test_only_tags() {
    GitPushNativeResult branchResult = new GitPushNativeResult(UP_TO_DATE, "refs/heads/master");
    GitPushNativeResult tagResult = new GitPushNativeResult(NEW_REF, "refs/tags/v0.1");
    GitPushResultNotification notification = notification(convertFromNative(branchResult, singletonList(tagResult), 0,
                                                                            from("master"), to("origin/master")));
    assertNotification(NotificationType.INFORMATION, "Push successful", "Pushed tag v0.1 to origin", notification);
  }

  public void test_two_repo_with_tags() {
    GitPushNativeResult branchSuccess = new GitPushNativeResult(SUCCESS, "refs/heads/master");
    GitPushNativeResult branchUpToDate = new GitPushNativeResult(UP_TO_DATE, "refs/heads/master");
    GitPushNativeResult tagResult = new GitPushNativeResult(NEW_REF, "refs/tags/v0.1");
    final GitPushRepoResult comRes = convertFromNative(branchSuccess, singletonList(tagResult), 1, from("master"), to("origin/master"));
    final GitPushRepoResult ultRes = convertFromNative(branchUpToDate, singletonList(tagResult), 0, from("master"), to("origin/master"));

    GitPushResultNotification notification = notification(new HashMap<GitRepository, GitPushRepoResult>() {{
      put(repo("community"), comRes);
      put(repo("ultimate"), ultRes);
    }});

    assertNotification(NotificationType.INFORMATION, "Push successful",
                       "community: pushed 1 commit to origin/master, and tag v0.1 to origin<br/>" +
                       "ultimate: pushed tag v0.1 to origin", notification);
  }

  public void test_two_tags() {
    GitPushNativeResult branchResult = new GitPushNativeResult(UP_TO_DATE, "refs/heads/master");
    GitPushNativeResult tag1 = new GitPushNativeResult(NEW_REF, "refs/tags/v0.1");
    GitPushNativeResult tag2 = new GitPushNativeResult(NEW_REF, "refs/tags/v0.2");
    GitPushResultNotification notification = notification(convertFromNative(branchResult, asList(tag1, tag2), 0,
                                                                            from("master"), to("origin/master")));
    assertNotification(NotificationType.INFORMATION, "Push successful", "Pushed 2 tags to origin", notification);
  }

  private static Map<GitRepository, GitPushRepoResult> singleResult(final GitPushNativeResult.Type type,
                                                                       final String from,
                                                                       final String to,
                                                                       final int commits,
                                                                       @Nullable final GitUpdateResult updateResult) {
    return new HashMap<GitRepository, GitPushRepoResult>() {{
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
      convertFromNative(nr, Collections.<GitPushNativeResult>emptyList(), commits, from(from), to(to)),
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

  private static GitLocalBranch from(String from) {
    return new GitLocalBranch(from);
  }

  private static GitRemoteBranch to(String to) {
    int firstSlash = to.indexOf('/');
    GitRemote remote = new GitRemote(to.substring(0, firstSlash), Collections.<String>emptyList(), Collections.<String>emptyList(),
                                     Collections.<String>emptyList(), Collections.<String>emptyList());
    return new GitStandardRemoteBranch(remote, to.substring(firstSlash + 1));
  }

  private GitPushResultNotification notification(GitPushRepoResult singleResult) {
    return notification(Collections.<GitRepository, GitPushRepoResult>singletonMap(repo("community"), singleResult));
  }

  private GitPushResultNotification notification(Map<GitRepository, GitPushRepoResult> map) {
    boolean wasUpdatePerformed = ContainerUtil.exists(map.values(), new Condition<GitPushRepoResult>() {
      @Override
      public boolean value(GitPushRepoResult aNew) {
        return aNew.getUpdateResult() != null;
      }
    });
    UpdatedFiles updatedFiles = UpdatedFiles.create();
    if (wasUpdatePerformed) {
      updatedFiles.getTopLevelGroups().get(0).add("file.txt", "Git", null);
    }
    return GitPushResultNotification.create(myProject, new GitPushResult(map, updatedFiles, null, null), map.size() > 1);
  }

  private static MockGitRepository repo(final String name) {
    final Ref<VirtualFile> root = Ref.create();
    EdtTestUtil.runInEdtAndWait(new Runnable() {
      @Override
      public void run() {
        root.set(createChildData(ourProject.getBaseDir(), name));
      }
    });
    return new MockGitRepository(ourProject, root.get());
  }
}