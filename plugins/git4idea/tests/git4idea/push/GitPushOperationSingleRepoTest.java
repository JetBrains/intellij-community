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
package git4idea.push;

import com.intellij.dvcs.push.PushSpec;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.config.UpdateMethod;
import git4idea.repo.GitRepository;
import git4idea.test.TestDialogHandler;
import git4idea.test.TestMessageHandler;
import git4idea.update.GitRebaseOverMergeProblem;
import git4idea.update.GitUpdateResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static git4idea.push.GitPushRepoResult.Type.*;
import static git4idea.test.GitExecutor.*;
import static git4idea.test.GitTestUtil.makeCommit;
import static java.util.Collections.singletonMap;

@SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
public class GitPushOperationSingleRepoTest extends GitPushOperationBaseTest {

  protected GitRepository myRepository;
  protected File myParentRepo;
  protected File myBroRepo;

  @Override
  protected void setUp() throws Exception {
    try {
      super.setUp();
    }
    catch (Exception e) {
      super.tearDown();
      throw e;
    }

    try {
      Trinity<GitRepository, File, File> trinity = setupRepositories(myProjectPath, "parent", "bro");
      myParentRepo = trinity.second;
      myBroRepo = trinity.third;
      myRepository = trinity.first;

      cd(myProjectPath);
      refresh();
    }
    catch (Exception e) {
      tearDown();
      throw e;
    }
  }

  public void test_successful_push() throws IOException {
    String hash = makeCommit("file.txt");
    GitPushResult result = push("master", "origin/master");

    assertResult(SUCCESS, 1, "master", "origin/master", result);
    assertPushed(hash, "master");
  }

  public void test_push_new_branch() {
    git("checkout -b feature");
    GitPushResult result = push("feature", "origin/feature");

    assertResult(NEW_BRANCH, -1, "feature", "origin/feature", result);
    assertBranchExists("feature");
  }

  public void test_push_new_branch_with_commits() {
    touch("feature.txt", "content");
    addCommit("feature commit");
    String hash = last();
    git("checkout -b feature");
    GitPushResult result = push("feature", "origin/feature");

    assertResult(NEW_BRANCH, -1, "feature", "origin/feature", result);
    assertBranchExists("feature");
    assertPushed(hash, "feature");
  }

  public void test_upstream_is_set_for_new_branch() {
    git("checkout -b feature");
    push("feature", "origin/feature");
    assertUpstream("feature", "origin", "feature");
  }

  public void test_upstream_is_not_modified_if_already_set() {
    push("master", "origin/feature");
    assertUpstream("master", "origin", "master");
  }

  public void test_rejected_push_to_tracked_branch_proposes_to_update() throws IOException {
    pushCommitFromBro();

    final Ref<Boolean> dialogShown = Ref.create(false);
    myDialogManager.registerDialogHandler(GitRejectedPushUpdateDialog.class, new TestDialogHandler<GitRejectedPushUpdateDialog>() {
      @Override
      public int handleDialog(GitRejectedPushUpdateDialog dialog) {
        dialogShown.set(true);
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    GitPushResult result = push("master", "origin/master");

    assertTrue("Rejected push dialog wasn't shown", dialogShown.get());
    assertResult(REJECTED, -1, "master", "origin/master", result);
  }

  public void test_rejected_push_to_other_branch_doesnt_propose_to_update() throws IOException {
    pushCommitFromBro();
    cd(myRepository);
    git("checkout -b feature");

    final Ref<Boolean> dialogShown = Ref.create(false);
    myDialogManager.registerDialogHandler(GitRejectedPushUpdateDialog.class, new TestDialogHandler<GitRejectedPushUpdateDialog>() {
      @Override
      public int handleDialog(GitRejectedPushUpdateDialog dialog) {
        dialogShown.set(true);
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    GitPushResult result = push("feature", "origin/master");

    assertFalse("Rejected push dialog shouldn't be shown", dialogShown.get());
    assertResult(REJECTED, -1, "feature", "origin/master", result);
  }

  public void test_push_is_rejected_too_many_times() throws IOException {
    pushCommitFromBro();
    cd(myRepository);
    String hash = makeCommit("afile.txt");

    agreeToUpdate(GitRejectedPushUpdateDialog.MERGE_EXIT_CODE);

    refresh();
    PushSpec<GitPushSource, GitPushTarget> pushSpec = makePushSpec(myRepository, "master", "origin/master");

    GitPushResult result = new GitPushOperation(myProject, myPushSupport, singletonMap(myRepository, pushSpec), null, false) {
      @NotNull
      @Override
      protected GitUpdateResult update(@NotNull Collection<GitRepository> rootsToUpdate,
                                       @NotNull UpdateMethod updateMethod,
                                       boolean checkForRebaseOverMergeProblem) {
        GitUpdateResult updateResult = super.update(rootsToUpdate, updateMethod, checkForRebaseOverMergeProblem);
        try {
          pushCommitFromBro();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        return updateResult;
      }
    }.execute();
    assertResult(REJECTED, -1, "master", "origin/master", GitUpdateResult.SUCCESS, Collections.singletonList("bro.txt"), result);

    cd(myParentRepo.getPath());
    String history = git("log --all --pretty=%H ");
    assertFalse("The commit shouldn't be pushed", history.contains(hash));
  }

  public void test_use_selected_update_method_for_all_consecutive_updates() throws IOException {
    pushCommitFromBro();
    cd(myRepository);
    makeCommit("afile.txt");

    agreeToUpdate(GitRejectedPushUpdateDialog.REBASE_EXIT_CODE);

    refresh();
    PushSpec<GitPushSource, GitPushTarget> pushSpec = makePushSpec(myRepository, "master", "origin/master");

    GitPushResult result = new GitPushOperation(myProject, myPushSupport, singletonMap(myRepository, pushSpec), null, false) {
      boolean updateHappened;

      @NotNull
      @Override
      protected GitUpdateResult update(@NotNull Collection<GitRepository> rootsToUpdate,
                                       @NotNull UpdateMethod updateMethod,
                                       boolean checkForRebaseOverMergeProblem) {
        GitUpdateResult updateResult = super.update(rootsToUpdate, updateMethod, checkForRebaseOverMergeProblem);
        try {
          if (!updateHappened) {
            updateHappened = true;
            pushCommitFromBro();
          }
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        return updateResult;
      }
    }.execute();

    assertResult(SUCCESS, 1, "master", "origin/master", GitUpdateResult.SUCCESS, result.getResults().get(myRepository));
    cd(myRepository);
    String[] commitMessages = StringUtil.splitByLines(log("--pretty=%s"));
    boolean mergeCommitsInTheLog = ContainerUtil.exists(commitMessages, new Condition<String>() {
      @Override
      public boolean value(String s) {
        return s.toLowerCase().contains("merge");
      }
    });
    assertFalse("Unexpected merge commits when rebase method is selected", mergeCommitsInTheLog);
  }

  public void test_force_push() throws IOException {
    String lostHash = pushCommitFromBro();
    cd(myRepository);
    String hash = makeCommit("anyfile.txt");

    GitPushResult result = push("master", "origin/master", true);

    assertResult(FORCED, -1, "master", "origin/master", result);

    cd(myParentRepo.getPath());
    String history = git("log --all --pretty=%H ");
    assertFalse(history.contains(lostHash));
    assertEquals(hash, StringUtil.splitByLines(history)[0]);
  }

  public void test_dont_propose_to_update_if_force_push_is_rejected() throws IOException {
    final Ref<Boolean> dialogShown = Ref.create(false);
    myDialogManager.registerDialogHandler(GitRejectedPushUpdateDialog.class, new TestDialogHandler<GitRejectedPushUpdateDialog>() {
      @Override
      public int handleDialog(GitRejectedPushUpdateDialog dialog) {
        dialogShown.set(true);
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    Pair<String, GitPushResult> remoteTipAndPushResult = forcePushWithReject();
    assertResult(REJECTED, -1, "master", "origin/master", remoteTipAndPushResult.second);
    assertFalse("Rejected push dialog should not be shown", dialogShown.get());
    cd(myParentRepo.getPath());
    assertEquals("The commit pushed from bro should be the last one", remoteTipAndPushResult.first, last());
  }

  public void test_dont_silently_update_if_force_push_is_rejected() throws IOException {
    myGitSettings.setUpdateType(UpdateMethod.REBASE);
    myGitSettings.setAutoUpdateIfPushRejected(true);

    Pair<String, GitPushResult> remoteTipAndPushResult = forcePushWithReject();

    assertResult(REJECTED, -1, "master", "origin/master", remoteTipAndPushResult.second);
    cd(myParentRepo.getPath());
    assertEquals("The commit pushed from bro should be the last one", remoteTipAndPushResult.first, last());
  }

  @NotNull
  private Pair<String, GitPushResult> forcePushWithReject() throws IOException {
    String pushedHash = pushCommitFromBro();
    cd(myParentRepo);
    git("config receive.denyNonFastForwards true");
    cd(myRepository);
    makeCommit("anyfile.txt");

    Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> map = singletonMap(myRepository,
                                                                                  makePushSpec(myRepository, "master", "origin/master"));
    GitPushResult result = new GitPushOperation(myProject, myPushSupport, map, null, true).execute();
    return Pair.create(pushedHash, result);
  }

  public void test_merge_after_rejected_push() throws IOException {
    String broHash = pushCommitFromBro();
    cd(myRepository);
    String hash = makeCommit("file.txt");

    agreeToUpdate(GitRejectedPushUpdateDialog.MERGE_EXIT_CODE);

    GitPushResult result = push("master", "origin/master");

    cd(myRepository);
    String log = git("log -3 --pretty=%H#%s");
    String[] commits = StringUtil.splitByLines(log);
    String lastCommitMsg = commits[0].split("#")[1];
    assertTrue("The last commit doesn't look like a merge commit: " + lastCommitMsg, lastCommitMsg.contains("Merge"));
    assertEquals(hash, commits[1].split("#")[0]);
    assertEquals(broHash, commits[2].split("#")[0]);

    assertResult(SUCCESS, 2, "master", "origin/master", GitUpdateResult.SUCCESS, Collections.singletonList("bro.txt"), result);
  }

  public void test_update_with_conflicts_cancels_push() throws IOException {
    cd(myBroRepo.getPath());
    append("bro.txt", "bro content");
    makeCommit("msg");
    git("push origin master:master");

    cd(myRepository);
    append("bro.txt", "main content");
    makeCommit("msg");

    agreeToUpdate(GitRejectedPushUpdateDialog.REBASE_EXIT_CODE);

    GitPushResult result = push("master", "origin/master");
    assertResult(REJECTED, -1, "master", "origin/master", GitUpdateResult.INCOMPLETE, Collections.singletonList("bro.txt"), result);
  }

  public void test_push_tags() throws IOException {
    cd(myRepository);
    git("tag v1");

    refresh();
    PushSpec<GitPushSource, GitPushTarget> spec = makePushSpec(myRepository, "master", "origin/master");
    GitPushResult pushResult = new GitPushOperation(myProject, myPushSupport, singletonMap(myRepository, spec),
                                                    GitPushTagMode.ALL, false).execute();
    GitPushRepoResult result = pushResult.getResults().get(myRepository);
    List<String> pushedTags = result.getPushedTags();
    assertEquals(1, pushedTags.size());
    assertEquals("refs/tags/v1", pushedTags.get(0));
  }

  public void test_warn_if_rebasing_over_merge() throws IOException {
    generateUnpushedMergedCommitProblem();

    final Ref<Boolean> rebaseOverMergeProblemDetected = Ref.create(false);
    myDialogManager.registerDialogHandler(GitRejectedPushUpdateDialog.class, new TestDialogHandler<GitRejectedPushUpdateDialog>() {
      @Override
      public int handleDialog(@NotNull GitRejectedPushUpdateDialog dialog) {
        rebaseOverMergeProblemDetected.set(dialog.warnsAboutRebaseOverMerge());
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });
    push("master", "origin/master");
    assertTrue(rebaseOverMergeProblemDetected.get());
  }

  public void test_warn_if_silently_rebasing_over_merge() throws IOException {
    generateUnpushedMergedCommitProblem();

    myGitSettings.setAutoUpdateIfPushRejected(true);
    myGitSettings.setUpdateType(UpdateMethod.REBASE);

    final Ref<Boolean> rebaseOverMergeProblemDetected = Ref.create(false);
    myDialogManager.registerMessageHandler(new TestMessageHandler() {
      @Override
      public int handleMessage(@NotNull String description) {
        rebaseOverMergeProblemDetected.set(description.contains(GitRebaseOverMergeProblem.DESCRIPTION));
        return Messages.CANCEL;
      }
    });
    push("master", "origin/master");
    assertTrue(rebaseOverMergeProblemDetected.get());
  }

  public void test_dont_overwrite_rebase_setting_when_chose_to_merge_due_to_unpushed_merge_commits() throws IOException {
    generateUnpushedMergedCommitProblem();

    myGitSettings.setUpdateType(UpdateMethod.REBASE);

    final Ref<Boolean> rebaseOverMergeProblemDetected = Ref.create(false);
    myDialogManager.registerDialogHandler(GitRejectedPushUpdateDialog.class, new TestDialogHandler<GitRejectedPushUpdateDialog>() {
      @Override
      public int handleDialog(@NotNull GitRejectedPushUpdateDialog dialog) {
        rebaseOverMergeProblemDetected.set(dialog.warnsAboutRebaseOverMerge());
        return GitRejectedPushUpdateDialog.MERGE_EXIT_CODE;
      }
    });
    push("master", "origin/master");
    assertTrue(rebaseOverMergeProblemDetected.get());
    assertEquals("Update method was overwritten by temporary update-via-merge decision",
                 UpdateMethod.REBASE, myGitSettings.getUpdateType());
  }

  public void test_respect_branch_default_setting_for_rejected_push_dialog() throws IOException {
    generateUpdateNeeded();
    myGitSettings.setUpdateType(UpdateMethod.BRANCH_DEFAULT);
    git("config branch.master.rebase true");

    final Ref<String> defaultActionName = Ref.create();
    myDialogManager.registerDialogHandler(GitRejectedPushUpdateDialog.class, new TestDialogHandler<GitRejectedPushUpdateDialog>() {
      @Override
      public int handleDialog(@NotNull GitRejectedPushUpdateDialog dialog) {
        defaultActionName.set((String)dialog.getDefaultAction().getValue(Action.NAME));
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    push("master", "origin/master");
    assertTrue("Default action in rejected-push dialog is incorrect: " + defaultActionName.get(),
               defaultActionName.get().toLowerCase().contains("rebase"));

    git("config branch.master.rebase false");
    push("master", "origin/master");
    assertTrue("Default action in rejected-push dialog is incorrect: " + defaultActionName.get(),
               defaultActionName.get().toLowerCase().contains("merge"));
  }

  public void test_respect_branch_default_setting_for_silent_update_when_rejected_push() throws IOException {
    generateUpdateNeeded();
    myGitSettings.setUpdateType(UpdateMethod.BRANCH_DEFAULT);
    git("config branch.master.rebase true");
    myGitSettings.setAutoUpdateIfPushRejected(true);

    push("master", "origin/master");
    assertFalse("Unexpected merge commit: rebase should have happened", log("-1 --pretty=%s").toLowerCase().startsWith("merge"));
  }

  // there is no "branch default" choice in the rejected push dialog
  // => simply don't rewrite the setting if the same value is chosen, as was default value initially
  public void test_dont_overwrite_branch_default_setting_when_agree_in_rejected_push_dialog() throws IOException {
    generateUpdateNeeded();
    myGitSettings.setUpdateType(UpdateMethod.BRANCH_DEFAULT);
    git("config branch.master.rebase true");

    myDialogManager.registerDialogHandler(GitRejectedPushUpdateDialog.class, new TestDialogHandler<GitRejectedPushUpdateDialog>() {
      @Override
      public int handleDialog(@NotNull GitRejectedPushUpdateDialog dialog) {
        return GitRejectedPushUpdateDialog.REBASE_EXIT_CODE;
      }
    });

    push("master", "origin/master");
    assertEquals(UpdateMethod.BRANCH_DEFAULT, myGitSettings.getUpdateType());
  }

  private void generateUpdateNeeded() throws IOException {
    pushCommitFromBro();
    cd(myRepository);
    makeCommit("file.txt");
  }

  private void generateUnpushedMergedCommitProblem() throws IOException {
    pushCommitFromBro();
    cd(myRepository);
    git("checkout -b branch1");
    makeCommit("branch1.txt");
    git("checkout master");
    makeCommit("master.txt");
    git("merge branch1");
  }

  @NotNull
  private GitPushResult push(@NotNull String from, @NotNull String to) {
    return push(from, to, false);
  }

  @NotNull
  private GitPushResult push(@NotNull String from, @NotNull String to, boolean force) {
    refresh();
    PushSpec<GitPushSource, GitPushTarget> spec = makePushSpec(myRepository, from, to);
    return new GitPushOperation(myProject, myPushSupport, singletonMap(myRepository, spec), null, force).execute();
  }

  private String pushCommitFromBro() throws IOException {
    cd(myBroRepo.getPath());
    String hash = makeCommit("bro.txt");
    git("push");
    return hash;
  }

  private void assertResult(GitPushRepoResult.Type type, int pushedCommits, String from, String to, GitPushResult actualResult) {
    assertResult(type, pushedCommits, from, to, null, null, actualResult);
  }

  private void assertResult(@NotNull GitPushRepoResult.Type type, int pushedCommits, @NotNull String from, @NotNull String to,
                              @Nullable GitUpdateResult updateResult,
                              @Nullable List<String> updatedFiles,
                              @NotNull GitPushResult actualResult) {
    assertResult(type, pushedCommits, from, to, updateResult, actualResult.getResults().get(myRepository));
    assertSameElements("Updated files set is incorrect",
                       getUpdatedFiles(actualResult.getUpdatedFiles()), ContainerUtil.notNullize(updatedFiles));
  }

  @Nullable
  private Collection<String> getUpdatedFiles(@NotNull UpdatedFiles updatedFiles) {
    Collection<String> result = ContainerUtil.newArrayList();
    for (FileGroup group : updatedFiles.getTopLevelGroups()) {
      result.addAll(getUpdatedFiles(group));
    }
    return result;
  }

  @NotNull
  private Collection<String> getUpdatedFiles(@NotNull FileGroup group) {
    Function<String, String> getRelative = new Function<String, String>() {
      @Override
      public String fun(String path) {
        return FileUtil.getRelativePath(new File(myProjectPath), new File(path));
      }
    };
    Collection<String> result = ContainerUtil.newArrayList();
    result.addAll(ContainerUtil.map(group.getFiles(), getRelative));
    for (FileGroup child : group.getChildren()) {
      result.addAll(getUpdatedFiles(child));
    }
    return result;
  }

  private void assertPushed(String expectedHash, String branch) {
    cd(myParentRepo.getPath());
    String actualHash = git("log -1 --pretty=%H " + branch);
    assertEquals(expectedHash, actualHash);
  }

  private void assertBranchExists(String branch) {
    cd(myParentRepo.getPath());
    String out = git("branch");
    assertTrue(out.contains(branch));
  }

  private static void assertUpstream(final String localBranch,
                                     String expectedUpstreamRemote,
                                     String expectedUpstreamBranch) {
    String upstreamRemote = GitBranchUtil.stripRefsPrefix(git("config branch." + localBranch + ".remote"));
    String upstreamBranch = GitBranchUtil.stripRefsPrefix(git("config branch." + localBranch + ".merge"));
    assertEquals(expectedUpstreamRemote, upstreamRemote);
    assertEquals(expectedUpstreamBranch, upstreamBranch);
  }

}