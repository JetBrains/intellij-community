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
import com.intellij.dvcs.test.MockVirtualFile
import com.intellij.notification.NotificationListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePathImpl
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.vcs.MockChangeListManager
import com.intellij.util.LineSeparator
import com.intellij.util.text.CharArrayUtil
import git4idea.GitCommit
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty
import git4idea.repo.GitRepository
import git4idea.test.GitLightTest
import org.jetbrains.annotations.NotNull
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import java.util.regex.Matcher

import static com.intellij.openapi.vcs.Executor.*
import static git4idea.test.GitExecutor.cd
import static git4idea.test.GitExecutor.git
import static git4idea.test.GitScenarios.*
import static groovy.util.GroovyTestCase.assertEquals
import static junit.framework.Assert.*
/**
 *
 * @author Kirill Likhodedov
 */
@Ignore
class GitBranchWorkerTest extends GitLightTest {

  private GitRepository myUltimate
  private GitRepository myCommunity
  private GitRepository myContrib

  private List<GitRepository> myRepositories

  @Before
  public void setUp() {
    super.setUp();

    cd(myProjectRoot)
    def community = mkdir("community")
    def contrib = mkdir("contrib")

    myUltimate = createRepository(myProjectRoot)
    myCommunity = createRepository(community)
    myContrib = createRepository(contrib)
    myRepositories = [ myUltimate, myCommunity, myContrib ]

    cd(myProjectRoot)
    touch(".gitignore", "community\ncontrib")
    git("add .gitignore")
    git("commit -m gitignore")
  }

  @After
  public void tearDown() {
    super.tearDown();
  }

  @Test
  public void "create new branch without problems"() {
    def successMessage = null;
    checkoutNewBranch "feature", [ notifySuccess: { String message -> successMessage = message } ]

    assertCurrentBranch("feature")
    assertEquals("Notification about successful branch creation is incorrect", "Branch ${bcode("feature")} was created", successMessage)
  }

  static String bcode(def s) {
    "<b><code>${s}</code></b>"
  }

  @Test
  public void "create new branch with unmerged files in first repo should show notification"() {
    unmergedFiles(myUltimate)

    boolean notificationShown = false;
    checkoutNewBranch("feature", [ showUnmergedFilesNotification: { String s, List l -> notificationShown = true } ])

    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  @Test
  public void "create new branch with unmerged files in second repo should propose to rollback"() {
    unmergedFiles(myCommunity)

    boolean rollbackProposed = false;
    checkoutNewBranch "feature", [ showUnmergedFilesMessageWithRollback: { String s1, String s2 -> rollbackProposed = true ; false } ]

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  @Test
  public void "rollback create new branch should delete branch"() {
    unmergedFiles(myCommunity)

    checkoutNewBranch "feature", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> true },
            notifySuccess: { String title, String message -> }
    ]

    assertCurrentBranch("master");
    assertBranchDeleted(myUltimate, "feature")
  }

  @Test
  public void "deny rollback create new branch should leave new branch"() {
    unmergedFiles(myCommunity)

    checkoutNewBranch "feature", [ showUnmergedFilesMessageWithRollback: { String s1, String s2 -> false } ]

    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "master")
    assertCurrentBranch(myContrib, "master")
  }

  @Test
  public void "checkout without problems"() {
    branchWithCommit(myRepositories, "feature")

    def successMessage = null;
    checkoutBranch "feature", [ notifySuccess: { String message -> successMessage = message } ]

    assertCurrentBranch("feature")
    assertEquals("Notification about successful branch checkout is incorrect", "Checked out ${bcode("feature")}", successMessage)
  }

  @Test
  public void "checkout_with_unmerged_files_in_first_repo_should_show_notification"() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myUltimate)

    boolean notificationShown = false;
    checkoutBranch("feature", [ showUnmergedFilesNotification: { String s, List l -> notificationShown = true } ])

    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  @Test
  public void checkout_with_unmerged_file_in_second_repo_should_propose_to_rollback() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    boolean rollbackProposed = false;
    checkoutBranch "feature", [ showUnmergedFilesMessageWithRollback: { String s1, String s2 -> rollbackProposed = true ; false } ]

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  @Test
  public void rollback_checkout_should_return_to_previous_branch() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    checkoutBranch "feature", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> true },
            notifySuccess: { String title, String message -> }
    ]

    assertCurrentBranch("master");
  }

  @Test
  public void deny_rollback_checkout_should_do_nothing() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    checkoutBranch "feature", [ showUnmergedFilesMessageWithRollback: { String s1, String s2 -> false } ]

    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "master")
    assertCurrentBranch(myContrib, "master")
  }

  @Test
  public void "checkout with untracked files overwritten by checkout in first repo should show notification"() {
    test_untracked_files_overwritten_by_in_first_repo("checkout");
  }

  @Test
  public void "checkout with several untracked files overwritten by checkout in first repo should show notification"() {
    // note that in old Git versions only one file is listed in the error.
    test_untracked_files_overwritten_by_in_first_repo("checkout", 3);
  }

  @Test
  public void "merge with untracked files overwritten by checkout in first repo should show notification"() {
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
            showUntrackedFilesNotification : { String s, Collection c -> notificationShown = true }
    ]

    assertTrue "Untracked files notification was not shown", notificationShown
  }

  @Test
  public void "checkout with untracked files overwritten by checkout in second repo should show rollback proposal with file list"() {
    test_checkout_with_untracked_files_overwritten_by_in_second_repo("checkout");
  }

  @Test
  public void "merge with untracked files overwritten by checkout in second repo should show rollback proposal with file list"() {
    test_checkout_with_untracked_files_overwritten_by_in_second_repo("merge");
  }

  def test_checkout_with_untracked_files_overwritten_by_in_second_repo(String operation) {
    branchWithCommit(myRepositories, "feature")
    def untracked = ["untracked.txt"]
    untrackedFileOverwrittenBy(myCommunity, "feature", untracked)

    Collection<VirtualFile> untrackedFiles = null;
    checkoutOrMerge operation, "feature", [
            showUntrackedFilesDialogWithRollback : {  String s, String p, Collection files -> untrackedFiles = files; false }
    ]

    assertTrue "Untracked files dialog was not shown", untrackedFiles != null
    assertEquals "Incorrect set of untracked files was shown in the dialog",
                 untracked,
                 untrackedFiles.collect { FileUtil.getRelativePath(myCommunity.root.path, it.path, '/'.toCharacter()) }
  }

  @Test
  public void "checkout with local changes overwritten by checkout should show smart checkout dialog"() {
    test_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("checkout");
  }

  @Test
  public void "checkout with several local changes overwritten by checkout should show smart checkout dialog"() {
    test_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("checkout", 3);
  }

  @Test
  public void "merge with local changes overwritten by merge should show smart merge dialog"() {
    test_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("merge");
  }

  def test_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog(String operation, int numFiles = 1) {
    def localChanges = prepareLocalChangesOverwrittenBy(myUltimate, numFiles)

    List<Change> changes = null;
    checkoutOrMerge(operation, "feature", [
            showSmartOperationDialog: { Project p, List<Change> cs, String op, boolean force ->
              changes = cs
              DialogWrapper.CANCEL_EXIT_CODE
            }
    ])

    assertNotNull "Local changes were not shown in the dialog", changes
    if (newGitVersion()) {
      assertEquals "Incorrect set of local changes was shown in the dialog",
                   localChanges,
                   changes.collect({
                     FileUtil.getRelativePath(myUltimate.root.path, it.afterRevision.file.path, '/'.toCharacter()) }
                   ).sort()
    }
  }

  boolean newGitVersion() {
    return !GitVersionSpecialty.OLD_STYLE_OF_UNTRACKED_AND_LOCAL_CHANGES_WOULD_BE_OVERWRITTEN.existsIn(GitVersion.parse(git("version")));
  }

  Change[] changesFromFiles(Collection<String> paths) {
    paths.collect {
      toChange(it)
    }
  }

  Change toChange(String relPath) {
    // we don't care about the before revision
    new Change(null, CurrentContentRevision.create(new FilePathImpl(new MockVirtualFile(myProjectRoot + "/" + relPath))))
  }

  @Test
  public void "agree to smart checkout should smart checkout"() {
    def localChanges = agree_to_smart_operation("checkout", "Checked out <b><code>feature</code></b>")

    assertCurrentBranch("feature");
    cd myUltimate
    def actual = cat(localChanges[0])
    def expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
                          LOCAL_CHANGES_OVERWRITTEN_BY.initial +
                          LOCAL_CHANGES_OVERWRITTEN_BY.masterLine;
    assertContent(expectedContent, actual)
  }

  @Test
  public void "agree to smart merge should smart merge"() {
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

    assertNotNull "No success notification was shown", handler.mySuccessMessage
    assertEquals "Success message is incorrect", expectedSuccessMessage, handler.mySuccessMessage

    localChanges
  }

  Collection<String> prepareLocalChangesOverwrittenBy(GitRepository repository, int numFiles = 1) {
    def localChanges = []
    for (int i = 0; i < numFiles; i++) {
      localChanges.add("local${i}.txt")
    }
    localChangesOverwrittenByWithoutConflict(repository, "feature", localChanges)
    // TODO we'd better avoid manual adding changes to the ChangeListManager.
    // Probably we should create GitTestChangeListManager that would fairly call git status and analyze the output.
    // Maybe we could reuse GitChangeProvider or at least GitNewChangesCollector.
    ((MockChangeListManager)myPlatformFacade.getChangeListManager(myProject)).addChanges(changesFromFiles(localChanges))

    myRepositories.each {
      if (it != repository) {
        branchWithCommit(it, "feature")
      }
    }
    localChanges
  }

  @Test
  public void "deny to smart checkout in first repo should show nothing"() {
    test_deny_to_smart_operation_in_first_repo_should_show_notification("checkout");
  }

  @Test
  public void "deny to smart merge in first repo should show nothing"() {
    test_deny_to_smart_operation_in_first_repo_should_show_notification("merge");
  }

  public void test_deny_to_smart_operation_in_first_repo_should_show_notification(String operation) {
    prepareLocalChangesOverwrittenBy(myUltimate)

    def errorMessage = null
    checkoutOrMerge(operation, "feature", [
            showSmartOperationDialog : { Project p, List<Change> cs, String op, boolean f -> GitSmartOperationDialog.CANCEL_EXIT_CODE },
            notifyError: { String title, String message -> errorMessage = message }
    ] as GitBranchUiHandler )

    assertNull "Error message was not shown", errorMessage
    assertCurrentBranch("master");
  }

  @Test
  public void "deny to smart checkout in second repo should show rollback proposal"() {
    test_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal("checkout");
    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "master")
    assertCurrentBranch(myContrib, "master")
  }

  @Test
  public void "deny to smart merge in second repo should show rollback proposal"() {
    test_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal("merge");
  }

  public void test_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal(String operation) {
    prepareLocalChangesOverwrittenBy(myCommunity)

    def rollbackMsg = null
    checkoutOrMerge(operation, "feature", [
            showSmartOperationDialog : { Project p, List<Change> cs, String op, boolean f -> GitSmartOperationDialog.CANCEL_EXIT_CODE },
            notifyErrorWithRollbackProposal: { String t, String m, String rp -> rollbackMsg = m ; false }
    ] as GitBranchUiHandler )

    assertNotNull "Rollback proposal was not shown", rollbackMsg
  }

  @Test
  void "Force checkout in case of local changes that would be overwritten by checkout"() {
    // IDEA-99849
    prepareLocalChangesOverwrittenBy(myUltimate)

    String errorMessage = null;
    String successMessage = null;
    def uiHandler = [
      showSmartOperationDialog: { Project p, List<Change> cs, String op, boolean force ->
        GitSmartOperationDialog.FORCE_EXIT_CODE;
      },
      notifySuccess: { String message -> successMessage = message },
      notifyError: { String title, String message -> errorMessage = message }
    ] as GitBranchUiHandler
    GitBranchWorker brancher = new GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler)
    brancher.checkoutNewBranchStartingFrom("new_branch", "feature", myRepositories)

    assertNull("Error notification is unexpected, but was: $errorMessage", errorMessage)
    assertEquals("Notification about successful branch creation is incorrect",
                 "Checked out new branch <b><code>new_branch</code></b> from <b><code>feature</code></b>", successMessage)
    assertCurrentBranch("new_branch")
  }

  @Test
  public void "rollback of 'checkout branch as new branch' should delete branches"() {
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

  @Test
  public void "delete branch that is fully merged should go without problems"() {
    myRepositories.each { cd it ; git("branch todelete") }

    def msg = null
    deleteBranch("todelete", [
            notifySuccess: { String message -> msg = message}
    ]);

    assertNotNull "Successful notification was not shown", msg
    assertEquals "Successful notification is incorrect", "Deleted branch ${bcode("todelete")}", msg
  }

  @Test
  public void "delete unmerged branch should show dialog"() {
    prepareUnmergedBranch(myCommunity)

    boolean dialogShown = false
    deleteBranch("todelete", [
            showBranchIsNotFullyMergedDialog : { Project p, Map h, String ub, List mb, String bb -> dialogShown = true ; false },
            notifyErrorWithRollbackProposal: { String t, String m, String rp -> false }
    ]);

    assertTrue "'Branch is not fully merged' dialog was not shown", dialogShown
  }

  @Test
  public void "ok in unmerged branch dialog should force delete branch"() {
    prepareUnmergedBranch(myUltimate)

    deleteBranch("todelete", [
            showBranchIsNotFullyMergedDialog : { Project p, Map h, String ub, List mb, String bb -> true },
            notifySuccess: { String message -> }
    ]);

    assertBranchDeleted("todelete")
  }

  @Test
  public void "cancel in unmerged branch dialog in not first repository should show rollback proposal"() {
    prepareUnmergedBranch(myCommunity)

    def rollbackMsg = null
    deleteBranch("todelete", [
            showBranchIsNotFullyMergedDialog : { Project p, Map h, String ub, List mb, String bb -> false },
            notifyErrorWithRollbackProposal: { String t, String m, String rp -> rollbackMsg = m ; false }
    ]);

    assertNotNull "Rollback messages was not shown", rollbackMsg
  }

  @Test
  public void "rollback delete branch should recreate branches"() {
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

  @Test
  public void "deny rollback delete branch should do nothing"() {
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

  @Test
  public void "delete branch merged to head but unmerged to upstream should show dialog"() {
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

  @Test
  public void "simple merge without problems"() {
    branchWithCommit(myRepositories, "master2", "branch_file.txt", "branch content")

    def message = null
    mergeBranch("master2", [
            notifySuccess: { String t, String m, NotificationListener l -> message = m }
    ]);

    assertNotNull "Success message wasn't shown", message
    assertEquals "Success message is incorrect",
                 "Merged ${bcode("master2")} to ${bcode("master")}<br/><a href='delete'>Delete master2</a>", message
    assertFile(myUltimate, "branch_file.txt", "branch content");
    assertFile(myCommunity, "branch_file.txt", "branch content");
    assertFile(myContrib, "branch_file.txt", "branch content");
  }

  @Test
  public void "merge branch that is up-to-date"() {
    myRepositories.each { cd it ; git("branch master2") }

    def message = null
    mergeBranch("master2", [
            notifySuccess: { String t, String m, NotificationListener l -> message = m }
    ]);

    assertNotNull "Success message wasn't shown", message
    assertEquals "Success message is incorrect", "Already up-to-date<br/><a href='delete'>Delete master2</a>", message
  }

  @Test
  public void "merge one simple and other up to date"() {
    branchWithCommit(myCommunity, "master2", "branch_file.txt", "branch content")
    [myUltimate, myContrib].each { cd it ; git("branch master2") }

    def message = null
    mergeBranch("master2", [
            notifySuccess: { String t, String m, NotificationListener l -> message = m }
    ]);

    assertNotNull "Success message wasn't shown", message
    assertEquals "Success message is incorrect",
                 "Merged ${bcode("master2")} to ${bcode("master")}<br/><a href='delete'>Delete master2</a>", message
    assertFile(myCommunity, "branch_file.txt", "branch content");
  }

  @Test
  public void "merge with unmerged files in first repo should show notification"() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myUltimate)

    boolean notificationShown = false;
    mergeBranch("feature", [
            showUnmergedFilesNotification: { String s, List l -> notificationShown = true }
    ])

    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  @Test
  public void "merge with unmerged files in second repo should propose to rollback"() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    boolean rollbackProposed = false;
    mergeBranch "feature", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> rollbackProposed = true ; false }
    ]

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  @Test
  public void "rollback merge should reset merge"() {
    branchWithCommit(myRepositories, "feature")
    String ultimateTip = tip(myUltimate)
    unmergedFiles(myCommunity)

    mergeBranch "feature", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> true },
            getProgressIndicator: { new ProgressIndicatorBase() }
    ]

    assertEquals "Merge in ultimate should have been reset", ultimateTip, tip(myUltimate)
  }

  private String tip(GitRepository repo) {
    cd repo
    git("rev-list -1 HEAD")
  }

  @Test
  public void "deny rollback merge should leave as is"() {
    branchWithCommit(myRepositories, "feature")
    cd myUltimate
    String ultimateTipAfterMerge = git("rev-list -1 feature")
    unmergedFiles(myCommunity)

    mergeBranch "feature", [
            showUnmergedFilesMessageWithRollback: { String s1, String s2 -> false }
    ]

    assertEquals "Merge in ultimate should have been reset", ultimateTipAfterMerge, tip(myUltimate)
  }

  def assertCurrentBranch(GitRepository repository, String name) {
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
    brancher.checkout(name, myRepositories)
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

  def assertBranchDeleted(GitRepository repo, String branch) {
    assertFalse("Branch $branch should have been deleted from $repo", git(repo, "branch").contains(branch))
  }

  def assertBranchExists(GitRepository repo, String branch) {
    assertTrue("Branch $branch should exist in $repo", branchExists(repo, branch))
  }

  private void assertFile(GitRepository repository, String path, String content) throws IOException {
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

  // TODO Somehow I wasn't able to make dynamic partial implementations, because both overloaded notifySuccess() methods are needed,
  // therefore there are duplicate entries in the map => only one method gets implemented.
  class AgreeToSmartOperationTestUiHandler implements GitBranchUiHandler {
    String mySuccessMessage

    @NotNull
    @Override
    ProgressIndicator getProgressIndicator() {
      new ProgressIndicatorBase()
    }

    @Override
    int showSmartOperationDialog(@NotNull Project project, @NotNull List<Change> changes, @NotNull String operation, boolean force) {
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
    void notifySuccess(@NotNull String message) {
      mySuccessMessage = message
    }

    @Override
    void notifySuccess(@NotNull String title, @NotNull String message, NotificationListener listener) {
      mySuccessMessage = message
    }

    @Override
    void notifyError(@NotNull String title, @NotNull String message) {
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
    void showUntrackedFilesNotification(@NotNull String operationName, @NotNull Collection<VirtualFile> untrackedFiles) {
      throw new UnsupportedOperationException()
    }

    @Override
    boolean showUntrackedFilesDialogWithRollback(@NotNull String operationName, @NotNull String rollbackProposal, @NotNull Collection<VirtualFile> untrackedFiles) {
      throw new UnsupportedOperationException()
    }

    @Override
    void notifySuccess(@NotNull String title, @NotNull String message) {
      mySuccessMessage = message
    }
  }

}
