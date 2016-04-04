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
package git4idea.branch;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import git4idea.GitCommit;
import git4idea.config.GitVersion;
import git4idea.config.GitVersionSpecialty;
import git4idea.repo.GitRepository;
import git4idea.test.GitPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static git4idea.test.GitExecutor.*;
import static git4idea.test.GitExecutor.commit;
import static git4idea.test.GitScenarios.*;

public class GitBranchWorkerTest extends GitPlatformTest {

  private GitRepository myUltimate;
  private GitRepository myCommunity;
  private GitRepository myContrib;

  private List<GitRepository> myRepositories;

  public void setUp() throws Exception {
    super.setUp();

    cd(myProjectRoot);
    File community = mkdir("community");
    File contrib = mkdir("contrib");

    myUltimate = createRepository(myProjectPath);
    myCommunity = createRepository(community.getPath());
    myContrib = createRepository(contrib.getPath());
    myRepositories = Arrays.asList(myUltimate, myCommunity, myContrib);

    cd(myProjectRoot);
    touch(".gitignore", "community\ncontrib");
    git("add .gitignore");
    git("commit -m gitignore");
  }

  public void test_create_new_branch_without_problems() {
    checkoutNewBranch("feature", new TestUiHandler());

    assertCurrentBranch("feature");
    assertEquals("Notification about successful branch creation is incorrect",
                 "Branch " + bcode("feature") + " was created", myVcsNotifier.getLastNotification().getContent());
  }

  private static String bcode(String s) {
    return "<b><code>" + s + "</code></b>";
  }

  public void test_create_new_branch_with_unmerged_files_in_first_repo_should_show_notification() {
    unmergedFiles(myUltimate);

    final Ref<Boolean> notificationShown = Ref.create(false);
    checkoutNewBranch("feature", new TestUiHandler() {
      @Override
      public void showUnmergedFilesNotification(@NotNull String operationName, @NotNull Collection<GitRepository> repositories) {
        notificationShown.set(true);
      }
    });

    assertTrue("Unmerged files notification was not shown", notificationShown.get());
  }

  public void test_create_new_branch_with_unmerged_files_in_second_repo_should_propose_to_rollback() {
    unmergedFiles(myCommunity);

    final Ref<Boolean> rollbackProposed = Ref.create(false);
    checkoutNewBranch("feature", new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        rollbackProposed.set(true);
        return false;
      }
    });

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed.get());
  }

  public void test_rollback_create_new_branch_should_delete_branch() {
    unmergedFiles(myCommunity);

    checkoutNewBranch("feature", new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        return true;
      }
    });

    assertCurrentBranch("master");
    assertBranchDeleted(myUltimate, "feature");
  }

  public void test_deny_rollback_create_new_branch_should_leave_new_branch() {
    unmergedFiles(myCommunity);

    checkoutNewBranch("feature", new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        return false;
      }
    });

    assertCurrentBranch(myUltimate, "feature");
    assertCurrentBranch(myCommunity, "master");
    assertCurrentBranch(myContrib, "master");
  }

  public void test_checkout_without_problems() {
    branchWithCommit(myRepositories, "feature");

    checkoutBranch("feature", new TestUiHandler());

    assertCurrentBranch("feature");
    assertEquals("Notification about successful branch checkout is incorrect", "Checked out " + bcode("feature"),
                 myVcsNotifier.getLastNotification().getContent());
  }

  public void test_checkout_with_unmerged_files_in_first_repo_should_show_notification() {
    branchWithCommit(myRepositories, "feature");
    unmergedFiles(myUltimate);

    final Ref<Boolean> notificationShown = Ref.create(false);
    checkoutBranch("feature", new TestUiHandler() {
      @Override
      public void showUnmergedFilesNotification(@NotNull String operationName, @NotNull Collection<GitRepository> repositories) {
        notificationShown.set(true);
      }
    });

    assertTrue("Unmerged files notification was not shown", notificationShown.get());
  }

  public void test_checkout_with_unmerged_file_in_second_repo_should_propose_to_rollback() {
    branchWithCommit(myRepositories, "feature");
    unmergedFiles(myCommunity);

    final Ref<Boolean> rollbackProposed = Ref.create(false);
    checkoutBranch("feature", new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        rollbackProposed.set(true);
        return false;
      }
    });

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed.get());
  }

  public void test_rollback_checkout_should_return_to_previous_branch() {
    branchWithCommit(myRepositories, "feature");
    unmergedFiles(myCommunity);

    checkoutBranch("feature", new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        return true;
      }
    });

    assertCurrentBranch("master");
  }

  public void test_deny_rollback_checkout_should_do_nothing() {
    branchWithCommit(myRepositories, "feature");
    unmergedFiles(myCommunity);

    checkoutBranch("feature", new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        return false;
      }
    });

    assertCurrentBranch(myUltimate, "feature");
    assertCurrentBranch(myCommunity, "master");
    assertCurrentBranch(myContrib, "master");
  }

  public void test_checkout_revision_checkout_branch_with_complete_success() {
    branchWithCommit(myRepositories, "feature");

    checkoutRevision("feature", new TestUiHandler());

    assertDetachedState("feature");
    assertEquals("Notification about successful branch checkout is incorrect", "Checked out " + bcode("feature"),
                 myVcsNotifier.getLastNotification().getContent());
  }

  public void test_checkout_revision_checkout_ref_with_complete_success() {
    branchWithCommit(myRepositories, "feature");

    checkoutRevision("feature~1", new TestUiHandler());

    assertDetachedState("master");
    assertEquals("Notification about successful branch checkout is incorrect", "Checked out " + bcode("feature~1"),
                 myVcsNotifier.getLastNotification().getContent());
  }

  public void test_checkout_revision_checkout_ref_with_complete_failure() {
    branchWithCommit(myRepositories, "feature");

    checkoutRevision("unknown_ref", new TestUiHandler());

    assertCurrentBranch("master");
    assertCurrentRevision("master");
    assertEquals("Notification about successful branch checkout is incorrect", "Revision not found in project, community and contrib",
                 myVcsNotifier.getLastNotification().getContent());
  }

  public void test_checkout_revision_checkout_ref_with_partial_success() {
    branchWithCommit(ContainerUtil.list(myCommunity, myContrib), "feature");

    checkoutRevision("feature", new TestUiHandler());

    assertCurrentBranch(myUltimate, "master");
    assertDetachedState(myCommunity, "feature");
    assertDetachedState(myContrib, "feature");

    assertEquals("Notification about successful branch checkout is incorrect",
                 "Checked out " + bcode("feature") + " in community and contrib" + "<br>" +
                 "Revision not found in project" + "<br><a href='rollback'>Rollback</a>",
                 myVcsNotifier.getLastNotification().getContent());
  }

  public void test_checkout_with_untracked_files_overwritten_by_checkout_in_first_repo_should_show_notification() {
    test_untracked_files_overwritten_by_in_first_repo("checkout", 1);
  }

  public void test_checkout_with_several_untracked_files_overwritten_by_checkout_in_first_repo_should_show_notification() {
    // note that in old Git versions only one file is listed in the error.
    test_untracked_files_overwritten_by_in_first_repo("checkout", 3);
  }

  public void test_merge_with_untracked_files_overwritten_by_checkout_in_first_repo_should_show_notification() {
    test_untracked_files_overwritten_by_in_first_repo("merge", 1);
  }

  private void test_untracked_files_overwritten_by_in_first_repo(String operation, int untrackedFiles) {
    branchWithCommit(myRepositories, "feature");

    Collection<String> files = ContainerUtil.newArrayList();
    for (int i = 0; i < untrackedFiles; i++) {
      files.add("untracked" + i + ".txt");
    }
    untrackedFileOverwrittenBy(myUltimate, "feature", files);

    final Ref<Boolean> notificationShown = Ref.create(false);
    checkoutOrMerge(operation, "feature", new TestUiHandler() {
      @Override
      public void showUntrackedFilesNotification(@NotNull String operationName,
                                                 @NotNull VirtualFile root,
                                                 @NotNull Collection<String> relativePaths) {
        notificationShown.set(true);
      }
    });

    assertTrue("Untracked files notification was not shown", notificationShown.get());
  }

  public void test_checkout_with_untracked_files_overwritten_by_checkout_in_second_repo_should_show_rollback_proposal_with_file_list() {
    check_checkout_with_untracked_files_overwritten_by_in_second_repo("checkout");
  }

  public void test_merge_with_untracked_files_overwritten_by_checkout_in_second_repo_should_show_rollback_proposal_with_file_list() {
    check_checkout_with_untracked_files_overwritten_by_in_second_repo("merge");
  }

  private void check_checkout_with_untracked_files_overwritten_by_in_second_repo(String operation) {
    branchWithCommit(myRepositories, "feature");


    List<String> untracked = Arrays.asList("untracked.txt");
    untrackedFileOverwrittenBy(myCommunity, "feature", untracked);

    final Collection<String> untrackedPaths = ContainerUtil.newArrayList();
    checkoutOrMerge(operation, "feature", new TestUiHandler() {
      @Override
      public boolean showUntrackedFilesDialogWithRollback(@NotNull String operationName,
                                                          @NotNull String rollbackProposal,
                                                          @NotNull VirtualFile root,
                                                          @NotNull Collection<String> relativePaths) {
        untrackedPaths.addAll(relativePaths);
        return false;
      }
    });

    assertTrue("Untracked files dialog was not shown", !untrackedPaths.isEmpty());
    assertEquals("Incorrect set of untracked files was shown in the dialog", untracked, untrackedPaths);
  }

  public void test_checkout_with_local_changes_overwritten_by_checkout_should_show_smart_checkout_dialog() throws ParseException {
    check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("checkout", 1);
  }

  public void test_checkout_with_several_local_changes_overwritten_by_checkout_should_show_smart_checkout_dialog() throws ParseException {
    check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("checkout", 3);
  }

  public void test_merge_with_local_changes_overwritten_by_merge_should_show_smart_merge_dialog() throws ParseException {
    check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("merge", 1);
  }

  private void check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog(String operation, int numFiles)
    throws ParseException {
    List<String> expectedChanges = prepareLocalChangesOverwrittenBy(myUltimate, numFiles);

    final List<Change> actualChanges = ContainerUtil.newArrayList();
    checkoutOrMerge(operation, "feature", new TestUiHandler() {
      @Override
      public int showSmartOperationDialog(@NotNull Project project,
                                          @NotNull List<Change> changes,
                                          @NotNull Collection<String> paths,
                                          @NotNull String operation,
                                          @Nullable String forceButton) {
        actualChanges.addAll(changes);
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    assertFalse("Local changes were not shown in the dialog", actualChanges.isEmpty());
    if (newGitVersion()) {
      Collection<String> actualPaths = ContainerUtil.map(actualChanges, new Function<Change, String>() {
        @Override
        public String fun(Change change) {
          return FileUtil.getRelativePath(myUltimate.getRoot().getPath(), change.getAfterRevision().getFile().getPath(), '/');
        }
      });
      assertSameElements("Incorrect set of local changes was shown in the dialog", actualPaths, expectedChanges);
    }
  }

  static boolean newGitVersion() throws ParseException {
    return !GitVersionSpecialty.OLD_STYLE_OF_UNTRACKED_AND_LOCAL_CHANGES_WOULD_BE_OVERWRITTEN.existsIn(GitVersion.parse(git("version")));
  }

  public void test_agree_to_smart_checkout_should_smart_checkout() {
    List<String> localChanges = agree_to_smart_operation("checkout", "Checked out <b><code>feature</code></b>");

    assertCurrentBranch("feature");
    cd(myUltimate);
    String actual = cat(localChanges.get(0));
    String expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
                          LOCAL_CHANGES_OVERWRITTEN_BY.initial +
                          LOCAL_CHANGES_OVERWRITTEN_BY.masterLine;
    assertContent(expectedContent, actual);
  }

  public void test_agree_to_smart_merge_should_smart_merge() {
    Collection<String> localChanges = agree_to_smart_operation("merge",
                       "Merged <b><code>feature</code></b> to <b><code>master</code></b><br/><a href='delete'>Delete feature</a>");

    cd(myUltimate);
    String actual = cat(ContainerUtil.getFirstItem(localChanges));
    String expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
                          LOCAL_CHANGES_OVERWRITTEN_BY.initial +
                          LOCAL_CHANGES_OVERWRITTEN_BY.masterLine;
    assertContent(expectedContent, actual);
  }

  List<String> agree_to_smart_operation(String operation, String expectedSuccessMessage) {
    List<String> localChanges = prepareLocalChangesOverwrittenBy(myUltimate);

    TestUiHandler handler = new TestUiHandler();
    checkoutOrMerge(operation, "feature", handler);

    assertNotNull("No success notification was shown", myVcsNotifier.getLastNotification());
    assertEquals("Success message is incorrect", expectedSuccessMessage, myVcsNotifier.getLastNotification().getContent());

    return localChanges;
  }

  List<String> prepareLocalChangesOverwrittenBy(GitRepository repository) {
    return prepareLocalChangesOverwrittenBy(repository, 1);
  }

  List<String> prepareLocalChangesOverwrittenBy(GitRepository repository, int numFiles) {
    List<String> localChanges = ContainerUtil.newArrayList();
    for (int i = 0; i < numFiles; i++) {
      localChanges.add(String.format("local%d.txt", i));
    }
    localChangesOverwrittenByWithoutConflict(repository, "feature", localChanges);
    updateChangeListManager();

    for (GitRepository repo : myRepositories) {
      if (!repo.equals(repository)) {
        branchWithCommit(repo, "feature");
      }
    }
    return localChanges;
  }

  public void test_deny_to_smart_checkout_in_first_repo_should_show_nothing() {
    check_deny_to_smart_operation_in_first_repo_should_show_nothing("checkout");
  }

  public void test_deny_to_smart_merge_in_first_repo_should_show_nothing() {
    check_deny_to_smart_operation_in_first_repo_should_show_nothing("merge");
  }

  public void check_deny_to_smart_operation_in_first_repo_should_show_nothing(String operation) {
    prepareLocalChangesOverwrittenBy(myUltimate);

    checkoutOrMerge(operation, "feature", new TestUiHandler() {
      @Override
      public int showSmartOperationDialog(@NotNull Project project,
                                          @NotNull List<Change> changes,
                                          @NotNull Collection<String> paths,
                                          @NotNull String operation,
                                          @Nullable String forceButton) {
        return GitSmartOperationDialog.CANCEL_EXIT_CODE;
      }
    });

    assertNull("Notification was unexpectedly shown:" + myVcsNotifier.getLastNotification(), myVcsNotifier.getLastNotification());
    assertCurrentBranch("master");
  }

  public void test_deny_to_smart_checkout_in_second_repo_should_show_rollback_proposal() {
    check_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal("checkout");
    assertCurrentBranch(myUltimate, "feature");
    assertCurrentBranch(myCommunity, "master");
    assertCurrentBranch(myContrib, "master");
  }

  public void test_deny_to_smart_merge_in_second_repo_should_show_rollback_proposal() {
    check_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal("merge");
  }

  public void check_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal(String operation) {
    prepareLocalChangesOverwrittenBy(myCommunity);

    final Ref<String> rollbackMsg = Ref.create();
    checkoutOrMerge(operation, "feature", new TestUiHandler() {
                      @Override
                      public int showSmartOperationDialog(@NotNull Project project,
                                                          @NotNull List<Change> changes,
                                                          @NotNull Collection<String> paths,
                                                          @NotNull String operation,
                                                          @Nullable String forceButton) {
                        return GitSmartOperationDialog.CANCEL_EXIT_CODE;
                      }

                      @Override
                      public boolean notifyErrorWithRollbackProposal(@NotNull String title,
                                                                     @NotNull String message,
                                                                     @NotNull String rollbackProposal) {
                        rollbackMsg.set(message);
                        return false;
                      }
                    });

    assertNotNull("Rollback proposal was not shown", rollbackMsg.get());
  }

  public void test_force_checkout_in_case_of_local_changes_that_would_be_overwritten_by_checkout() {
    // IDEA-99849
    prepareLocalChangesOverwrittenBy(myUltimate);

    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, new TestUiHandler() {
      @Override
      public int showSmartOperationDialog(@NotNull Project project,
                                          @NotNull List<Change> changes,
                                          @NotNull Collection<String> paths,
                                          @NotNull String operation,
                                          @Nullable String forceButton) {
        return GitSmartOperationDialog.FORCE_EXIT_CODE;
      }
    });
    brancher.checkoutNewBranchStartingFrom("new_branch", "feature", myRepositories);

    assertEquals("Notification about successful branch creation is incorrect",
                 "Checked out new branch <b><code>new_branch</code></b> from <b><code>feature</code></b>",
                 myVcsNotifier.getLastNotification().getContent());
    assertCurrentBranch("new_branch");
  }

  public void test_rollback_of_checkout_branch_as_new_branch_should_delete_branches() {
    branchWithCommit(myRepositories, "feature");
    touch("feature.txt", "feature_content");
    git("add feature.txt");
    git("commit -m feature_changes");
    git("checkout master");

    unmergedFiles(myCommunity);

    final Ref<Boolean> rollbackProposed = Ref.create(false);
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        rollbackProposed.set(true);
        return true;
      }
    });
    brancher.checkoutNewBranchStartingFrom("newBranch", "feature", myRepositories);

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed.get());
    assertCurrentBranch("master");
    for (GitRepository repository : myRepositories) {
      assertFalse("Branch 'newBranch' should have been deleted on rollback",
                  ContainerUtil.exists(git(repository, "branch").split("\n"), new Condition<String>() {
                    @Override
                    public boolean value(String s) {
                      return s.contains("newBranch");
                    }
                  }));
    }
  }

  public void test_delete_branch_that_is_fully_merged_should_go_without_problems() {
    for (GitRepository repository : myRepositories) {
      git(repository, "branch todelete");
    }

    deleteBranch("todelete", new TestUiHandler());

    assertNotNull("Successful notification was not shown", myVcsNotifier.getLastNotification());
    assertEquals("Successful notification is incorrect", "Deleted branch " + bcode("todelete"),
                 myVcsNotifier.getLastNotification().getContent());
  }

  public void test_delete_unmerged_branch_should_show_dialog() {
    prepareUnmergedBranch(myCommunity);

    final Ref<Boolean> dialogShown = Ref.create(false);
    deleteBranch("todelete", new TestUiHandler() {
      @Override
      public boolean showBranchIsNotFullyMergedDialog(@NotNull Project project,
                                                      @NotNull Map<GitRepository, List<GitCommit>> history,
                                                      @NotNull String unmergedBranch,
                                                      @NotNull List<String> mergedToBranches,
                                                      @NotNull String baseBranch) {
        dialogShown.set(true);
        return false;
      }

      @Override
      public boolean notifyErrorWithRollbackProposal(@NotNull String title, @NotNull String message, @NotNull String rollbackProposal) {
        return false;
      }
    });

    assertTrue("'Branch is not fully merged' dialog was not shown", dialogShown.get());
  }

  public void test_ok_in_unmerged_branch_dialog_should_force_delete_branch() {
    prepareUnmergedBranch(myUltimate);
    deleteBranch("todelete", new TestUiHandler() {
      @Override
      public boolean showBranchIsNotFullyMergedDialog(@NotNull Project project,
                                                      @NotNull Map<GitRepository, List<GitCommit>> history,
                                                      @NotNull String unmergedBranch,
                                                      @NotNull List<String> mergedToBranches,
                                                      @NotNull String baseBranch) {
        return true;
      }
    });
    assertBranchDeleted("todelete");
  }

  public void test_cancel_in_unmerged_branch_dialog_in_not_first_repository_should_show_rollback_proposal() {
    prepareUnmergedBranch(myCommunity);

    final Ref<String> rollbackMsg = Ref.create();
    deleteBranch("todelete", new TestUiHandler() {
      @Override
      public boolean showBranchIsNotFullyMergedDialog(@NotNull Project project,
                                                      @NotNull Map<GitRepository, List<GitCommit>> history,
                                                      @NotNull String unmergedBranch,
                                                      @NotNull List<String> mergedToBranches,
                                                      @NotNull String baseBranch) {
        return false;
      }

      @Override
      public boolean notifyErrorWithRollbackProposal(@NotNull String title, @NotNull String message, @NotNull String rollbackProposal) {
        rollbackMsg.set(message);
        return false;
      }
    });

    assertNotNull("Rollback messages was not shown", rollbackMsg.get());
  }

  public void test_rollback_delete_branch_should_recreate_branches() {
    prepareUnmergedBranch(myCommunity);

    final Ref<String> rollbackMsg = Ref.create();
    deleteBranch("todelete", new TestUiHandler() {
      @Override
      public boolean showBranchIsNotFullyMergedDialog(@NotNull Project project,
                                                      @NotNull Map<GitRepository, List<GitCommit>> history,
                                                      @NotNull String unmergedBranch,
                                                      @NotNull List<String> mergedToBranches,
                                                      @NotNull String baseBranch) {
        return false;
      }

      @Override
      public boolean notifyErrorWithRollbackProposal(@NotNull String title, @NotNull String message, @NotNull String rollbackProposal) {
        rollbackMsg.set(message);
        return true;
      }
    });

    assertNotNull("Rollback messages was not shown", rollbackMsg.get());
    assertBranchExists(myUltimate, "todelete");
    assertBranchExists(myCommunity, "todelete");
    assertBranchExists(myContrib, "todelete");
  }

  public void test_deny_rollback_delete_branch_should_do_nothing() {
    prepareUnmergedBranch(myCommunity);

    final Ref<String> rollbackMsg = Ref.create();
    deleteBranch("todelete", new TestUiHandler() {
      @Override
      public boolean showBranchIsNotFullyMergedDialog(@NotNull Project project,
                                                      @NotNull Map<GitRepository, List<GitCommit>> history,
                                                      @NotNull String unmergedBranch,
                                                      @NotNull List<String> mergedToBranches,
                                                      @NotNull String baseBranch) {
        return false;
      }

      @Override
      public boolean notifyErrorWithRollbackProposal(@NotNull String title, @NotNull String message, @NotNull String rollbackProposal) {
        rollbackMsg.set(message);
        return false;
      }
    });

    assertNotNull("Rollback messages was not shown", rollbackMsg.get());

    assertBranchDeleted(myUltimate, "todelete");
    assertBranchExists(myCommunity, "todelete");
    assertBranchExists(myContrib, "todelete");
  }

  public void test_delete_branch_merged_to_head_but_unmerged_to_upstream_should_show_dialog() {
    // inspired by IDEA-83604
    // for the sake of simplicity we deal with a single myCommunity repository for remote operations
    prepareRemoteRepo(myCommunity);
    cd(myCommunity);
    git("checkout -b feature");
    git("push -u origin feature");

    // create a commit and merge it to master, but not to feature's upstream
    touch("feature.txt", "feature content");
    git("add feature.txt");
    git("commit -m feature_branch");
    git("checkout master");
    git("merge feature");

    // delete feature fully merged to current HEAD, but not to the upstream
    final Ref<Boolean> dialogShown = Ref.create(false);
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, new TestUiHandler() {
      @Override
      public boolean showBranchIsNotFullyMergedDialog(@NotNull Project project,
                                                      @NotNull Map<GitRepository, List<GitCommit>> history,
                                                      @NotNull String unmergedBranch,
                                                      @NotNull List<String> mergedToBranches,
                                                      @NotNull String baseBranch) {
        dialogShown.set(true);
        return false;
      }
    });
    brancher.deleteBranch("feature", Arrays.asList(myCommunity));

    assertTrue("'Branch is not fully merged' dialog was not shown", dialogShown.get());
  }

  public void test_simple_merge_without_problems() throws IOException {
    branchWithCommit(myRepositories, "master2", "branch_file.txt", "branch content");

    mergeBranch("master2", new TestUiHandler());

    assertNotNull("Success message wasn't shown", myVcsNotifier.getLastNotification());
    assertEquals("Success message is incorrect",
                 "Merged " + bcode("master2") + " to " +  bcode("master") + "<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.getLastNotification().getContent());
    assertFile(myUltimate, "branch_file.txt", "branch content");
    assertFile(myCommunity, "branch_file.txt", "branch content");
    assertFile(myContrib, "branch_file.txt", "branch content");
  }

  public void test_merge_branch_that_is_up_to_date() {
    for (GitRepository repository : myRepositories) {
      git(repository, "branch master2");
    }

    mergeBranch("master2", new TestUiHandler());

    assertNotNull("Success message wasn't shown", myVcsNotifier.getLastNotification());
    assertEquals("Success message is incorrect", "Already up-to-date<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.getLastNotification().getContent());
  }

  public void test_merge_one_simple_and_other_up_to_date() throws IOException {
    branchWithCommit(myCommunity, "master2", "branch_file.txt", "branch content");
    git(myUltimate, "branch master2");
    git(myContrib, "branch master2");

    mergeBranch("master2", new TestUiHandler());

    assertNotNull("Success message wasn't shown", myVcsNotifier.getLastNotification());
    assertEquals("Success message is incorrect",
                 "Merged " + bcode("master2") + " to " + bcode("master") + "<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.getLastNotification().getContent());
    assertFile(myCommunity, "branch_file.txt", "branch content");
  }

  public void test_merge_with_unmerged_files_in_first_repo_should_show_notification() {
    branchWithCommit(myRepositories, "feature");
    unmergedFiles(myUltimate);

    final Ref<Boolean> notificationShown = Ref.create(false);
    mergeBranch("feature", new TestUiHandler() {
                  @Override
                  public void showUnmergedFilesNotification(@NotNull String operationName,
                                                            @NotNull Collection<GitRepository> repositories) {
                    notificationShown.set(true);
                  }
                });
    assertTrue("Unmerged files notification was not shown", notificationShown.get());
  }

  public void test_merge_with_unmerged_files_in_second_repo_should_propose_to_rollback() {
    branchWithCommit(myRepositories, "feature");
    unmergedFiles(myCommunity);

    final Ref<Boolean> rollbackProposed = Ref.create(false);
    mergeBranch("feature", new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        rollbackProposed.set(true);
        return false;
      }
    });
    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed.get());
  }

  public void test_rollback_merge_should_reset_merge() {
    branchWithCommit(myRepositories, "feature");
    String ultimateTip = tip(myUltimate);
    unmergedFiles(myCommunity);

    mergeBranch("feature", new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        return true;
      }
    });

    assertEquals("Merge in ultimate should have been reset", ultimateTip, tip(myUltimate));
  }

  private static String tip(GitRepository repo) {
    cd(repo);
    return git("rev-list -1 HEAD");
  }

  public void test_deny_rollback_merge_should_leave_as_is() {
    branchWithCommit(myRepositories, "feature");
    cd(myUltimate);
    String ultimateTipAfterMerge = git("rev-list -1 feature");
    unmergedFiles(myCommunity);

    mergeBranch("feature", new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        return false;
      }
    });

    assertEquals("Merge in ultimate should have been reset", ultimateTipAfterMerge, tip(myUltimate));
  }

  public void test_checkout_in_detached_head() {
    cd(myCommunity);
    touch("file.txt", "some content");
    add("file.txt");
    commit("msg");
    git(myCommunity, "checkout HEAD^");

    checkoutBranch("master", new TestUiHandler());
    assertCurrentBranch("master");
  }

  // inspired by IDEA-127472
  public void test_checkout_to_common_branch_when_branches_have_diverged() {
    branchWithCommit(myUltimate, "feature", "feature-file.txt", "feature_content", false);
    branchWithCommit(myCommunity, "newbranch", "newbranch-file.txt", "newbranch_content", false);
    checkoutBranch("master", new TestUiHandler());
    assertCurrentBranch("master");
  }

  public void test_rollback_checkout_from_diverged_branches_should_return_to_proper_branches() {
    branchWithCommit(myUltimate, "feature", "feature-file.txt", "feature_content", false);
    branchWithCommit(myCommunity, "newbranch", "newbranch-file.txt", "newbranch_content", false);
    unmergedFiles(myContrib);

    checkoutBranch("master", new TestUiHandler() {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        return true;
      }
    });

    assertCurrentBranch(myUltimate, "feature");
    assertCurrentBranch(myCommunity, "newbranch");
    assertCurrentBranch(myContrib, "master");
  }

  static private void assertDetachedState(GitRepository repository, String reference) {
    assertCurrentRevision(repository, reference);

    String curBranch = getCurrentBranch(repository);
    boolean isDetached = curBranch.contains("detached");
    assertTrue("Current branch is not detached in ${repository} - " + curBranch, isDetached);
  }

  static private void assertCurrentBranch(GitRepository repository, String name) {
    String curBranch = getCurrentBranch(repository);
    assertEquals("Current branch is incorrect in ${repository}", name, curBranch);
  }

  @NotNull
  private static String getCurrentBranch(GitRepository repository) {
    return ObjectUtils.assertNotNull(ContainerUtil.find(git(repository, "branch").split("\n"), new Condition<String>() {
      @Override
      public boolean value(String s) {
        return s.contains("*");
      }
    })).replace('*', ' ').trim();
  }

  static private void assertCurrentRevision(GitRepository repository, String reference) {
    String expectedRef = git(repository, "rev-parse " + "HEAD");
    String currentRef = git(repository, "rev-parse " + reference);

    assertEquals("Current revision is incorrect in ${repository}", expectedRef, currentRef);
  }

  private void assertDetachedState(String reference) {
    for (GitRepository repository : myRepositories) {
      assertDetachedState(repository, reference);
    }
  }

  private void assertCurrentBranch(String name) {
    for (GitRepository repository : myRepositories) {
      assertCurrentBranch(repository, name);
    }
  }

  private void assertCurrentRevision(String reference) {
    for (GitRepository repository : myRepositories) {
      assertCurrentRevision(repository, reference);
    }
  }

  private void checkoutNewBranch(String name, GitBranchUiHandler uiHandler) {
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler);
    brancher.checkoutNewBranch(name, myRepositories);
  }

  private void checkoutBranch(String name, GitBranchUiHandler uiHandler) {
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler);
    brancher.checkout(name, false, myRepositories);
  }

  private void checkoutRevision(String reference, GitBranchUiHandler uiHandler) {
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler);
    brancher.checkout(reference, true, myRepositories);
  }

  private void mergeBranch(String name, GitBranchUiHandler uiHandler) {
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler);
    brancher.merge(name, GitBrancher.DeleteOnMergeOption.PROPOSE, myRepositories);
  }

  private void deleteBranch(String name, GitBranchUiHandler uiHandler) {
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler);
    brancher.deleteBranch(name, myRepositories);
  }

  private void checkoutOrMerge(String operation, String name, GitBranchUiHandler uiHandler) {
    if (operation.equals("checkout")) {
      checkoutBranch(name, uiHandler);
    }
    else {
      mergeBranch(name, uiHandler);
    }
  }

  private void prepareUnmergedBranch(GitRepository unmergedRepo) {
    for (GitRepository repository : myRepositories) {
      git(repository, "branch todelete");
    }
    cd(unmergedRepo);
    git("checkout todelete");
    touch("afile.txt", "content");
    git("add afile.txt");
    git("commit -m unmerged_commit");
    git("checkout master");
  }

  void assertBranchDeleted(String name) {
    for (GitRepository repository : myRepositories) {
      assertBranchDeleted(repository, name);
    }
  }

  static private void assertBranchDeleted(GitRepository repo, String branch) {
    assertFalse("Branch $branch should have been deleted from $repo", git(repo, "branch").contains(branch));
  }

  static private void assertBranchExists(GitRepository repo, String branch) {
    assertTrue("Branch $branch should exist in $repo", branchExists(repo, branch));
  }

  private static void assertFile(GitRepository repository, String path, String content) throws IOException {
    cd(repository);
    assertEquals("Content doesn't match", content, cat(path));
  }

  private static void assertContent(String expectedContent, String actual) {
    expectedContent = StringUtil.convertLineSeparators(expectedContent, detectLineSeparators(actual).getSeparatorString()).trim();
    actual = actual.trim();
    assertEquals(String.format("Content doesn't match.%nExpected:%n%s%nActual:%n%s%n",
                               substWhitespaces(expectedContent), substWhitespaces(actual)), expectedContent, actual);
  }

  private static LineSeparator detectLineSeparators(String actual) {
    char[] chars = CharArrayUtil.fromSequence(actual);
    for (char c : chars) {
      if (c == '\r') {
        return LineSeparator.CRLF;
      }
      else if (c == '\n') {   // if we are here, there was no \r before
        return LineSeparator.LF;
      }
    }
    return LineSeparator.LF;
  }

  private static String substWhitespaces(String s) {
    return s.replaceAll("\r", Matcher.quoteReplacement("\\r")).replaceAll("\n", Matcher.quoteReplacement("\\n")).replaceAll(" ", "_");
  }

  private static class TestUiHandler implements GitBranchUiHandler {

    @NotNull
    @Override
    public ProgressIndicator getProgressIndicator() {
      return new EmptyProgressIndicator();
    }

    @Override
    public int showSmartOperationDialog(@NotNull Project project,
                                        @NotNull List<Change> changes,
                                        @NotNull Collection<String> paths,
                                        @NotNull String operation,
                                        @Nullable String forceButton) {
      return GitSmartOperationDialog.SMART_EXIT_CODE;
    }

    @Override
    public boolean showBranchIsNotFullyMergedDialog(@NotNull Project project,
                                                    @NotNull Map<GitRepository, List<GitCommit>> history,
                                                    @NotNull String unmergedBranch,
                                                    @NotNull List<String> mergedToBranches,
                                                    @NotNull String baseBranch) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean notifyErrorWithRollbackProposal(@NotNull String title, @NotNull String message, @NotNull String rollbackProposal) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void showUnmergedFilesNotification(@NotNull String operationName, @NotNull Collection<GitRepository> repositories) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void showUntrackedFilesNotification(@NotNull String operationName,
                                               @NotNull VirtualFile root,
                                               @NotNull Collection<String> relativePaths) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean showUntrackedFilesDialogWithRollback(@NotNull String operationName,
                                                        @NotNull String rollbackProposal,
                                                        @NotNull VirtualFile root,
                                                        @NotNull Collection<String> relativePaths) {
      throw new UnsupportedOperationException();
    }
  }
}
