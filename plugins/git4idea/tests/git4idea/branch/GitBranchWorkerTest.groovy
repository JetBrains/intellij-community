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
package git4idea.branch

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Function
import com.intellij.util.LineSeparator
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.CharArrayUtil
import git4idea.GitCommit
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTest
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.util.regex.Matcher

import static git4idea.test.GitExecutor.*
import static git4idea.test.GitScenarios.*

class GitBranchWorkerTest extends GitPlatformTest {

  private GitRepository myUltimate
  private GitRepository myCommunity
  private GitRepository myContrib

  private List<GitRepository> myRepositories

  public void setUp() {
    super.setUp();

    try {
      cd(myProjectRoot)
      File community = mkdir("community")
      File contrib = mkdir("contrib")

      myUltimate = createRepository(myProjectRoot.path)
      myCommunity = createRepository(community.path)
      myContrib = createRepository(contrib.path)
      myRepositories = [ myUltimate, myCommunity, myContrib ]

      cd(myProjectRoot)
      touch(".gitignore", "community\ncontrib")
      git("add .gitignore")
      git("commit -m gitignore")
    }
    catch (Throwable e) {
      tearDown()
      throw e
    }
  }

  public void "test create new branch without problems"() {
    checkoutNewBranch "feature", [ ]

    assertCurrentBranch("feature")
    assertEquals("Notification about successful branch creation is incorrect",
                 "Branch ${bcode("feature")} was created", myVcsNotifier.lastNotification.content)
  }

  static String bcode(def s) {
    "<b><code>${s}</code></b>"
  }

  public void "test create new branch with unmerged files in first repo should show notification"() {
    unmergedFiles(myUltimate)

    boolean notificationShown = false;
    checkoutNewBranch("feature", [ showUnmergedFilesNotification: { String s, List l -> notificationShown = true } ])

    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  public void "test create new branch with unmerged files in second repo should propose to rollback"() {
    unmergedFiles(myCommunity)

    boolean rollbackProposed = false;
    checkoutNewBranch "feature", [ showUnmergedFilesMessageWithRollback: { String s1, String s2 -> rollbackProposed = true ; false } ]

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  public void "test rollback create new branch should delete branch"() {
    unmergedFiles(myCommunity)

    checkoutNewBranch "feature", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> true },
    ]

    assertCurrentBranch("master");
    assertBranchDeleted(myUltimate, "feature")
  }

  public void "test deny rollback create new branch should leave new branch"() {
    unmergedFiles(myCommunity)

    checkoutNewBranch "feature", [ showUnmergedFilesMessageWithRollback: { String s1, String s2 -> false } ]

    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "master")
    assertCurrentBranch(myContrib, "master")
  }

  public void "test checkout without problems"() {
    branchWithCommit(myRepositories, "feature")

    checkoutBranch "feature", []

    assertCurrentBranch("feature")
    assertEquals("Notification about successful branch checkout is incorrect", "Checked out ${bcode("feature")}",
                 myVcsNotifier.lastNotification.content)
  }

  public void "test checkout_with_unmerged_files_in_first_repo_should_show_notification"() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myUltimate)

    boolean notificationShown = false;
    checkoutBranch("feature", [ showUnmergedFilesNotification: { String s, List l -> notificationShown = true } ])

    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  public void test_checkout_with_unmerged_file_in_second_repo_should_propose_to_rollback() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    boolean rollbackProposed = false;
    checkoutBranch "feature", [ showUnmergedFilesMessageWithRollback: { String s1, String s2 -> rollbackProposed = true ; false } ]

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  public void test_rollback_checkout_should_return_to_previous_branch() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    checkoutBranch "feature", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> true },
    ]

    assertCurrentBranch("master");
  }

  public void test_deny_rollback_checkout_should_do_nothing() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    checkoutBranch "feature", [ showUnmergedFilesMessageWithRollback: { String s1, String s2 -> false } ]

    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "master")
    assertCurrentBranch(myContrib, "master")
  }

  public void "test checkout with untracked files overwritten by checkout in first repo should show notification"() {
    test_untracked_files_overwritten_by_in_first_repo("checkout");
  }

  public void "test checkout with several untracked files overwritten by checkout in first repo should show notification"() {
    // note that in old Git versions only one file is listed in the error.
    test_untracked_files_overwritten_by_in_first_repo("checkout", 3);
  }

  public void "test merge with untracked files overwritten by checkout in first repo should show notification"() {
    test_untracked_files_overwritten_by_in_first_repo("merge");
  }

  def test_untracked_files_overwritten_by_in_first_repo(String operation, int untrackedFiles = 1) {
    branchWithCommit(myRepositories, "feature")
    def files = []
    for (int i = 0; i < untrackedFiles; i++) {
      files.add("untracked${i}.txt")
    }
    untrackedFileOverwrittenBy(myUltimate, "feature", files)

    boolean notificationShown = false;
    checkoutOrMerge operation, "feature", [
      showUntrackedFilesNotification: { String s, VirtualFile root, Collection c -> notificationShown = true }
    ]

    assertTrue "Untracked files notification was not shown", notificationShown
  }

  public void "test checkout with untracked files overwritten by checkout in second repo should show rollback proposal with file list"() {
    check_checkout_with_untracked_files_overwritten_by_in_second_repo("checkout");
  }

  public void "test merge with untracked files overwritten by checkout in second repo should show rollback proposal with file list"() {
    check_checkout_with_untracked_files_overwritten_by_in_second_repo("merge");
  }

  def check_checkout_with_untracked_files_overwritten_by_in_second_repo(String operation) {
    branchWithCommit(myRepositories, "feature")
    def untracked = ["untracked.txt"]
    untrackedFileOverwrittenBy(myCommunity, "feature", untracked)

    Collection<String> untrackedPaths = null;
    checkoutOrMerge operation, "feature", [
      showUntrackedFilesDialogWithRollback: {
        String s, String p, VirtualFile root, Collection<String> files -> untrackedPaths = files; false
      }
    ]

    assertTrue "Untracked files dialog was not shown", untrackedPaths != null
    assertEquals "Incorrect set of untracked files was shown in the dialog", untracked.asList(), untrackedPaths.asList()
  }

  public void "test checkout with local changes overwritten by checkout should show smart checkout dialog"() {
    check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("checkout");
  }

  public void "test checkout with several local changes overwritten by checkout should show smart checkout dialog"() {
    check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("checkout", 3);
  }

  public void "test merge with local changes overwritten by merge should show smart merge dialog"() {
    check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("merge");
  }

  def check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog(String operation, int numFiles = 1) {
    Collection<String> expectedChanges = prepareLocalChangesOverwrittenBy(myUltimate, numFiles)

    List<Change> changes = null;
    checkoutOrMerge(operation, "feature", [
      showSmartOperationDialog: { Project p, List<Change> cs, Collection<String> paths, String op, String force ->
              changes = cs
              DialogWrapper.CANCEL_EXIT_CODE
            }
    ])

    assertNotNull "Local changes were not shown in the dialog", changes
    if (newGitVersion()) {
      Iterable<String> actualChanges = ContainerUtil.map(changes, new Function<Change, String>() {
        @Override
        public String fun(Change change) {
          return FileUtil.getRelativePath(myUltimate.root.path, change.afterRevision.file.path, '/'.toCharacter());
        }
      })
      assertSameElements "Incorrect set of local changes was shown in the dialog", actualChanges, expectedChanges;
    }
  }

  static boolean newGitVersion() {
    return !GitVersionSpecialty.OLD_STYLE_OF_UNTRACKED_AND_LOCAL_CHANGES_WOULD_BE_OVERWRITTEN.existsIn(GitVersion.parse(git("version")));
  }

  public void "test agree to smart checkout should smart checkout"() {
    def localChanges = agree_to_smart_operation("checkout", "Checked out <b><code>feature</code></b>")

    assertCurrentBranch("feature");
    cd myUltimate
    def actual = cat(localChanges[0])
    def expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
                          LOCAL_CHANGES_OVERWRITTEN_BY.initial +
                          LOCAL_CHANGES_OVERWRITTEN_BY.masterLine;
    assertContent(expectedContent, actual)
  }

  public void "test agree to smart merge should smart merge"() {
    def localChanges = agree_to_smart_operation("merge",
                       "Merged <b><code>feature</code></b> to <b><code>master</code></b><br/><a href='delete'>Delete feature</a>")

    cd myUltimate
    def actual = cat(localChanges[0])
    def expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
                          LOCAL_CHANGES_OVERWRITTEN_BY.initial +
                          LOCAL_CHANGES_OVERWRITTEN_BY.masterLine;
    assertContent(expectedContent, actual)
  }

  Collection<String> agree_to_smart_operation(String operation, String expectedSuccessMessage) {
    def localChanges = prepareLocalChangesOverwrittenBy(myUltimate)

    AgreeToSmartOperationTestUiHandler handler = new AgreeToSmartOperationTestUiHandler()
    checkoutOrMerge(operation, "feature", handler)

    assertNotNull "No success notification was shown", myVcsNotifier.lastNotification
    assertEquals "Success message is incorrect", expectedSuccessMessage, myVcsNotifier.lastNotification.content

    localChanges
  }

  Collection<String> prepareLocalChangesOverwrittenBy(GitRepository repository, int numFiles = 1) {
    Collection<String> localChanges = ContainerUtil.newArrayList();
    for (int i = 0; i < numFiles; i++) {
      localChanges.add(String.format("local%d.txt", i));
    }
    localChangesOverwrittenByWithoutConflict(repository, "feature", localChanges)
    updateChangeListManager()

    myRepositories.each {
      if (it != repository) {
        branchWithCommit(it, "feature")
      }
    }
    return localChanges;
  }

  public void "test deny to smart checkout in first repo should show nothing"() {
    check_deny_to_smart_operation_in_first_repo_should_show_nothing("checkout");
  }

  public void "test deny to smart merge in first repo should show nothing"() {
    check_deny_to_smart_operation_in_first_repo_should_show_nothing("merge");
  }

  public void check_deny_to_smart_operation_in_first_repo_should_show_nothing(String operation) {
    prepareLocalChangesOverwrittenBy(myUltimate)

    checkoutOrMerge(operation, "feature", [
      showSmartOperationDialog: { Project p, List<Change> cs, Collection<String> paths, String op, String force
        -> GitSmartOperationDialog.CANCEL_EXIT_CODE
      },
    ] as GitBranchUiHandler )

    assertNull "Notification was unexpectedly shown:" + myVcsNotifier.lastNotification, myVcsNotifier.lastNotification
    assertCurrentBranch("master");
  }

  public void "test deny to smart checkout in second repo should show rollback proposal"() {
    check_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal("checkout");
    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "master")
    assertCurrentBranch(myContrib, "master")
  }

  public void "test deny to smart merge in second repo should show rollback proposal"() {
    check_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal("merge");
  }

  public void check_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal(String operation) {
    prepareLocalChangesOverwrittenBy(myCommunity)

    def rollbackMsg = null
    checkoutOrMerge(operation, "feature", [
      showSmartOperationDialog       : {
        Project p, List<Change> cs, Collection<String> paths, String op, String f -> GitSmartOperationDialog.CANCEL_EXIT_CODE
      },
      notifyErrorWithRollbackProposal: { String t, String m, String rp -> rollbackMsg = m; false }
    ] as GitBranchUiHandler )

    assertNotNull "Rollback proposal was not shown", rollbackMsg
  }

  void "test force checkout in case of local changes that would be overwritten by checkout"() {
    // IDEA-99849
    prepareLocalChangesOverwrittenBy(myUltimate)

    def uiHandler = [
      showSmartOperationDialog: { Project p, List<Change> cs, Collection<String> paths, String op, String force ->
        GitSmartOperationDialog.FORCE_EXIT_CODE;
      },
    ] as GitBranchUiHandler
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler)
    brancher.checkoutNewBranchStartingFrom("new_branch", "feature", myRepositories)

    assertEquals("Notification about successful branch creation is incorrect",
                 "Checked out new branch <b><code>new_branch</code></b> from <b><code>feature</code></b>",
                 myVcsNotifier.lastNotification.content)
    assertCurrentBranch("new_branch")
  }

  public void "test rollback of 'checkout branch as new branch' should delete branches"() {
    branchWithCommit(myRepositories, "feature")
    touch("feature.txt", "feature_content")
    git("add feature.txt")
    git("commit -m feature_changes")
    git("checkout master")

    unmergedFiles(myCommunity)

    boolean rollbackProposed = false;
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> rollbackProposed = true ; true }
    ] as GitBranchUiHandler)
    brancher.checkoutNewBranchStartingFrom("newBranch", "feature", myRepositories)

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
    assertCurrentBranch("master");
    myRepositories.each {
      assertTrue "Branch 'newBranch' should have been deleted on rollback",
                 git(it, "branch").split("\n").grep( { it.contains("newBranch") }).isEmpty()
    }
  }

  public void "test delete branch that is fully merged should go without problems"() {
    myRepositories.each { cd it ; git("branch todelete") }

    deleteBranch("todelete", []);

    assertNotNull "Successful notification was not shown", myVcsNotifier.lastNotification
    assertEquals "Successful notification is incorrect", "Deleted branch ${bcode("todelete")}", myVcsNotifier.lastNotification.content
  }

  public void "test delete unmerged branch should show dialog"() {
    prepareUnmergedBranch(myCommunity)

    boolean dialogShown = false
    deleteBranch("todelete", [
            showBranchIsNotFullyMergedDialog : { Project p, Map h, String ub, List mb, String bb -> dialogShown = true ; false },
            notifyErrorWithRollbackProposal: { String t, String m, String rp -> false }
    ]);

    assertTrue "'Branch is not fully merged' dialog was not shown", dialogShown
  }

  public void "test ok in unmerged branch dialog should force delete branch"() {
    prepareUnmergedBranch(myUltimate)

    deleteBranch("todelete", [
            showBranchIsNotFullyMergedDialog : { Project p, Map h, String ub, List mb, String bb -> true },
    ]);

    assertBranchDeleted("todelete")
  }

  public void "test cancel in unmerged branch dialog in not first repository should show rollback proposal"() {
    prepareUnmergedBranch(myCommunity)

    def rollbackMsg = null
    deleteBranch("todelete", [
            showBranchIsNotFullyMergedDialog : { Project p, Map h, String ub, List mb, String bb -> false },
            notifyErrorWithRollbackProposal: { String t, String m, String rp -> rollbackMsg = m ; false }
    ]);

    assertNotNull "Rollback messages was not shown", rollbackMsg
  }

  public void "test rollback delete branch should recreate branches"() {
    prepareUnmergedBranch(myCommunity)

    def rollbackMsg = null
    deleteBranch("todelete", [
            showBranchIsNotFullyMergedDialog : { Project p, Map h, String ub, List mb, String bb -> false },
            notifyErrorWithRollbackProposal: { String t, String m, String rp -> rollbackMsg = m ; true }
    ]);

    assertNotNull "Rollback messages was not shown", rollbackMsg
    assertBranchExists(myUltimate, "todelete")
    assertBranchExists(myCommunity, "todelete")
    assertBranchExists(myContrib, "todelete")
  }

  public void "test deny rollback delete branch should do nothing"() {
    prepareUnmergedBranch(myCommunity)

    def rollbackMsg = null
    deleteBranch("todelete", [
            showBranchIsNotFullyMergedDialog : { Project p, Map h, String ub, List mb, String bb -> false },
            notifyErrorWithRollbackProposal: { String t, String m, String rp -> rollbackMsg = m ; false }
    ]);

    assertNotNull "Rollback messages was not shown", rollbackMsg

    assertBranchDeleted(myUltimate, "todelete")
    assertBranchExists(myCommunity, "todelete")
    assertBranchExists(myContrib, "todelete")
  }

  public void "test delete branch merged to head but unmerged to upstream should show dialog"() {
    // inspired by IDEA-83604
    // for the sake of simplicity we deal with a single myCommunity repository for remote operations
    prepareRemoteRepo(myCommunity)
    cd myCommunity
    git("checkout -b feature");
    git("push -u origin feature")

    // create a commit and merge it to master, but not to feature's upstream
    touch("feature.txt", "feature content")
    git("add feature.txt")
    git("commit -m feature_branch")
    git("checkout master")
    git("merge feature")

    // delete feature fully merged to current HEAD, but not to the upstream
    boolean dialogShown = false;
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, [
            showBranchIsNotFullyMergedDialog : { Project p, Map h, String ub, List mb, String bb -> dialogShown = true; false }
    ] as GitBranchUiHandler)
    brancher.deleteBranch("feature", [myCommunity])

    assertTrue "'Branch is not fully merged' dialog was not shown", dialogShown
  }

  public void "test simple merge without problems"() {
    branchWithCommit(myRepositories, "master2", "branch_file.txt", "branch content")

    mergeBranch("master2", []);

    assertNotNull "Success message wasn't shown", myVcsNotifier.lastNotification
    assertEquals "Success message is incorrect",
                 "Merged ${bcode("master2")} to ${bcode("master")}<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.lastNotification.content
    assertFile(myUltimate, "branch_file.txt", "branch content");
    assertFile(myCommunity, "branch_file.txt", "branch content");
    assertFile(myContrib, "branch_file.txt", "branch content");
  }

  public void "test merge branch that is up-to-date"() {
    myRepositories.each { cd it ; git("branch master2") }

    mergeBranch("master2", []);

    assertNotNull "Success message wasn't shown", myVcsNotifier.lastNotification
    assertEquals "Success message is incorrect", "Already up-to-date<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.lastNotification.content
  }

  public void "test merge one simple and other up to date"() {
    branchWithCommit(myCommunity, "master2", "branch_file.txt", "branch content")
    [myUltimate, myContrib].each { cd it ; git("branch master2") }

    mergeBranch("master2", []);

    assertNotNull "Success message wasn't shown", myVcsNotifier.lastNotification
    assertEquals "Success message is incorrect",
                 "Merged ${bcode("master2")} to ${bcode("master")}<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.lastNotification.content
    assertFile(myCommunity, "branch_file.txt", "branch content");
  }

  public void "test merge with unmerged files in first repo should show notification"() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myUltimate)

    boolean notificationShown = false;
    mergeBranch("feature", [
            showUnmergedFilesNotification: { String s, List l -> notificationShown = true }
    ])

    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  public void "test merge with unmerged files in second repo should propose to rollback"() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    boolean rollbackProposed = false;
    mergeBranch "feature", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> rollbackProposed = true ; false }
    ]

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  public void "test rollback merge should reset merge"() {
    branchWithCommit(myRepositories, "feature")
    String ultimateTip = tip(myUltimate)
    unmergedFiles(myCommunity)

    mergeBranch "feature", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> true },
            getProgressIndicator: { new ProgressIndicatorBase() }
    ]

    assertEquals "Merge in ultimate should have been reset", ultimateTip, tip(myUltimate)
  }

  private static String tip(GitRepository repo) {
    cd repo
    git("rev-list -1 HEAD")
  }

  public void "test deny rollback merge should leave as is"() {
    branchWithCommit(myRepositories, "feature")
    cd myUltimate
    String ultimateTipAfterMerge = git("rev-list -1 feature")
    unmergedFiles(myCommunity)

    mergeBranch "feature", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> false }
    ]

    assertEquals "Merge in ultimate should have been reset", ultimateTipAfterMerge, tip(myUltimate)
  }

  public void test_checkout_in_detached_head() {
    cd(myCommunity);
    touch("file.txt", "some content");
    add("file.txt");
    commit("msg");
    git(myCommunity, "checkout HEAD^");

    checkoutBranch("master", []);
    assertCurrentBranch("master");
  }

  // inspired by IDEA-127472
  public void test_checkout_to_common_branch_when_branches_have_diverged() {
    branchWithCommit(myUltimate, "feature", "feature-file.txt", "feature_content", false);
    branchWithCommit(myCommunity, "newbranch", "newbranch-file.txt", "newbranch_content", false);
    checkoutBranch("master", [])
    assertCurrentBranch("master");
  }

  public void test_rollback_checkout_from_diverged_branches_should_return_to_proper_branches() {
    branchWithCommit(myUltimate, "feature", "feature-file.txt", "feature_content", false);
    branchWithCommit(myCommunity, "newbranch", "newbranch-file.txt", "newbranch_content", false);
    unmergedFiles(myContrib)

    checkoutBranch "master", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> true },
    ]

    assertCurrentBranch(myUltimate, "feature");
    assertCurrentBranch(myCommunity, "newbranch");
    assertCurrentBranch(myContrib, "master");
  }

  static def assertCurrentBranch(GitRepository repository, String name) {
    def curBranch = git(repository, "branch").split("\n").find { it -> it.contains("*") }.replace('*', ' ').trim()
    assertEquals("Current branch is incorrect in ${repository}", name, curBranch)
  }

  def assertCurrentBranch(String name) {
    myRepositories.each { assertCurrentBranch(it, name) }
  }

  def checkoutNewBranch(String name, def uiHandler) {
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler as GitBranchUiHandler)
    brancher.checkoutNewBranch(name, myRepositories)
  }

  def checkoutBranch(String name, def uiHandler) {
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler as GitBranchUiHandler)
    brancher.checkout(name, false, myRepositories)
  }

  def mergeBranch(String name, def uiHandler) {
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler as GitBranchUiHandler)
    brancher.merge(name, GitBrancher.DeleteOnMergeOption.PROPOSE, myRepositories)
  }

  def deleteBranch(String name, def uiHandler) {
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler as GitBranchUiHandler)
    brancher.deleteBranch(name, myRepositories)
  }

  def checkoutOrMerge(def operation, String name, def uiHandler) {
    if (operation == "checkout") {
      checkoutBranch(name, uiHandler)
    }
    else {
      mergeBranch(name, uiHandler)
    }
  }

  private void prepareUnmergedBranch(GitRepository unmergedRepo) {
    myRepositories.each {
      git(it, "branch todelete")
    }
    cd unmergedRepo
    git("checkout todelete")
    touch("afile.txt", "content")
    git("add afile.txt")
    git("commit -m unmerged_commit")
    git("checkout master")
  }

  void assertBranchDeleted(String name) {
    myRepositories.each { assertBranchDeleted(it, name) }
  }

  static def assertBranchDeleted(GitRepository repo, String branch) {
    assertFalse("Branch $branch should have been deleted from $repo", git(repo, "branch").contains(branch))
  }

  static def assertBranchExists(GitRepository repo, String branch) {
    assertTrue("Branch $branch should exist in $repo", branchExists(repo, branch))
  }

  private static void assertFile(GitRepository repository, String path, String content) throws IOException {
    cd repository
    assertEquals "Content doesn't match", content, cat(path)
  }

  private static void assertContent(String expectedContent, String actual) {
    expectedContent = StringUtil.convertLineSeparators(expectedContent, detectLineSeparators(actual).separatorString).trim()
    actual = actual.trim()
    assertEquals String.format("Content doesn't match.%nExpected:%n%s%nActual:%n%s%n",
                               substWhitespaces(expectedContent), substWhitespaces(actual)), expectedContent, actual
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
    return s.replaceAll("\r", Matcher.quoteReplacement("\\r")).replaceAll("\n", Matcher.quoteReplacement("\\n")).replaceAll(" ", "_")
  }

  class AgreeToSmartOperationTestUiHandler implements GitBranchUiHandler {

    @NotNull
    @Override
    ProgressIndicator getProgressIndicator() {
      new ProgressIndicatorBase()
    }

    @Override
    int showSmartOperationDialog(
      @NotNull Project project,
      @NotNull List<Change> changes,
      @NotNull Collection<String> paths,
      @NotNull String operation,
      @Nullable String forceButton) {
      GitSmartOperationDialog.SMART_EXIT_CODE
    }

    @Override
    boolean showBranchIsNotFullyMergedDialog(
      Project project,
      Map<GitRepository, List<GitCommit>> history,
      String unmergedBranch,
      List<String> mergedToBranches,
      String baseBranch) {
      throw new UnsupportedOperationException()
    }

    @Override
    boolean notifyErrorWithRollbackProposal(@NotNull String title, @NotNull String message, @NotNull String rollbackProposal) {
      throw new UnsupportedOperationException()
    }

    @Override
    void showUnmergedFilesNotification(@NotNull String operationName, @NotNull Collection<GitRepository> repositories) {
      throw new UnsupportedOperationException()
    }

    @Override
    boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
      throw new UnsupportedOperationException()
    }

    @Override
    void showUntrackedFilesNotification(@NotNull String operationName, @NotNull VirtualFile root, @NotNull Collection<String> relativePaths) {
      throw new UnsupportedOperationException()
    }

    @Override
    boolean showUntrackedFilesDialogWithRollback(
      @NotNull String operationName, @NotNull String rollbackProposal, @NotNull VirtualFile root, @NotNull Collection<String> relativePaths) {
      throw new UnsupportedOperationException()
    }

  }

}
