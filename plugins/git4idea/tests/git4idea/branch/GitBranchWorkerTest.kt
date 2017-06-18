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
package git4idea.branch

import com.intellij.notification.Notification
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.LineSeparator
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.CharArrayUtil
import git4idea.GitCommit
import git4idea.branch.GitBranchUiHandler.DeleteRemoteBranchDecision
import git4idea.branch.GitBranchUtil.getTrackInfoForBranch
import git4idea.branch.GitDeleteBranchOperation.*
import git4idea.branch.GitSmartOperationDialog.Choice.*
import git4idea.commands.GitCommandResult
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty
import git4idea.repo.GitRepository
import git4idea.test.*
import git4idea.test.GitScenarios.*
import java.io.File
import java.util.*
import java.util.regex.Matcher

class GitBranchWorkerTest : GitPlatformTest() {

  private lateinit var myUltimate: GitRepository
  private lateinit var myCommunity: GitRepository
  private lateinit var myContrib: GitRepository
  private lateinit var myRepositories: List<GitRepository>

  public override fun setUp() {
    super.setUp()

    cd(myProjectRoot)
    val community = mkdir("community")
    val contrib = mkdir("contrib")

    myUltimate = createRepository(myProjectPath)
    myCommunity = createRepository(community.path)
    myContrib = createRepository(contrib.path)
    myRepositories = listOf(myUltimate, myCommunity, myContrib)

    cd(myProjectRoot)
    touch(".gitignore", "community\ncontrib")
    git("add .gitignore")
    git("commit -m gitignore")
    myUltimate.update()
  }

  fun test_create_new_branch_without_problems() {
    checkoutNewBranch("feature", TestUiHandler())

    assertCurrentBranch("feature")
    assertSuccessfulNotification("Branch ${bcode("feature")} was created")
  }

  fun `test create new branch without checkout not at HEAD`() {
    val hashMap = myRepositories.map { it to it.currentRevision!! }.toMap()
    myRepositories.forEach { cd(it); tac("f.txt") }

    GitBranchWorker(myProject, myGit, TestUiHandler()).createBranch("feature", myRepositories.map{ it to "HEAD^" }.toMap())

    assertCurrentBranch("master")
    myRepositories.forEach {
      val branch = it.branches.findLocalBranch("feature")
      assertNotNull("Branch not created in $it", branch)
      assertEquals("Branch feature created at wrong point", hashMap[it], it.branches.getHash(branch!!)!!.asString())
    }
    assertSuccessfulNotification("Branch ${bcode("feature")} was created")
  }

  fun test_create_new_branch_with_unmerged_files_in_first_repo_should_show_notification() {
    unmergedFiles(myUltimate)

    var notificationShown = false
    checkoutNewBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesNotification(operationName: String, repositories: Collection<GitRepository>) {
        notificationShown = true
      }
    })

    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  fun test_create_new_branch_with_unmerged_files_in_second_repo_should_propose_to_rollback() {
    unmergedFiles(myCommunity)

    var rollbackProposed = false
    checkoutNewBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed = true
        return false
      }
    })

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  fun test_rollback_create_new_branch_should_delete_branch() {
    unmergedFiles(myCommunity)

    checkoutNewBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return true
      }
    })

    assertCurrentBranch("master")
    assertBranchDeleted(myUltimate, "feature")
  }

  fun test_deny_rollback_create_new_branch_should_leave_new_branch() {
    unmergedFiles(myCommunity)

    checkoutNewBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return false
      }
    })

    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "master")
    assertCurrentBranch(myContrib, "master")
  }

  fun test_checkout_without_problems() {
    branchWithCommit(myRepositories, "feature")

    checkoutBranch("feature", TestUiHandler())

    assertCurrentBranch("feature")
    assertEquals("Notification about successful branch checkout is incorrect", "Checked out " + bcode("feature"),
                 myVcsNotifier.lastNotification.content)
  }

  fun test_checkout_with_unmerged_files_in_first_repo_should_show_notification() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myUltimate)

    var notificationShown = false
    checkoutBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesNotification(operationName: String, repositories: Collection<GitRepository>) {
        notificationShown = true
      }
    })

    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  fun test_checkout_with_unmerged_file_in_second_repo_should_propose_to_rollback() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    var rollbackProposed = false
    checkoutBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed = true
        return false
      }
    })

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  fun test_rollback_checkout_should_return_to_previous_branch() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    checkoutBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String) = true
    })

    assertCurrentBranch("master")
  }

  fun test_deny_rollback_checkout_should_do_nothing() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    checkoutBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String) = false
    })

    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "master")
    assertCurrentBranch(myContrib, "master")
  }

  fun test_checkout_revision_checkout_branch_with_complete_success() {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("feature", TestUiHandler())

    assertDetachedState("feature")
    assertSuccessfulNotification("Checked out ${bcode("feature")}")
  }

  fun test_checkout_revision_checkout_ref_with_complete_success() {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("feature~1", TestUiHandler())

    assertDetachedState("master")
    assertSuccessfulNotification("Checked out ${bcode("feature~1")}")
  }

  fun test_checkout_revision_checkout_ref_with_complete_failure() {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("unknown_ref", TestUiHandler())

    assertCurrentBranch("master")
    assertCurrentRevision("master")
    assertErrorNotification("Couldn't checkout unknown_ref", "Revision not found in project, community and contrib")
  }

  fun test_checkout_revision_checkout_ref_with_partial_success() {
    branchWithCommit(listOf(myCommunity, myContrib), "feature")

    checkoutRevision("feature", TestUiHandler())

    assertCurrentBranch(myUltimate, "master")
    assertDetachedState(myCommunity, "feature")
    assertDetachedState(myContrib, "feature")

    assertSuccessfulNotification("Checked out ${bcode("feature")} in community and contrib<br/>" +
                                 "Revision not found in project<br><a href='rollback'>Rollback</a>")
  }

  fun test_checkout_with_untracked_files_overwritten_by_checkout_in_first_repo_should_show_notification() {
    test_untracked_files_overwritten_by_in_first_repo("checkout", 1)
  }

  fun test_checkout_with_several_untracked_files_overwritten_by_checkout_in_first_repo_should_show_notification() {
    // note that in old Git versions only one file is listed in the error.
    test_untracked_files_overwritten_by_in_first_repo("checkout", 3)
  }

  fun test_merge_with_untracked_files_overwritten_by_checkout_in_first_repo_should_show_notification() {
    test_untracked_files_overwritten_by_in_first_repo("merge", 1)
  }

  private fun test_untracked_files_overwritten_by_in_first_repo(operation: String, untrackedFiles: Int) {
    branchWithCommit(myRepositories, "feature")

    val files = ContainerUtil.newArrayList<String>()
    (0..untrackedFiles - 1).mapTo(files) { "untracked$it.txt" }
    untrackedFileOverwrittenBy(myUltimate, "feature", files)

    var notificationShown = false
    checkoutOrMerge(operation, "feature", object : TestUiHandler() {
      override fun showUntrackedFilesNotification(operationName: String,
                                                  root: VirtualFile,
                                                  relativePaths: Collection<String>) {
        notificationShown = true
      }
    })

    assertTrue("Untracked files notification was not shown", notificationShown)
  }

  fun test_checkout_with_untracked_files_overwritten_by_checkout_in_second_repo_should_show_rollback_proposal_with_file_list() {
    check_checkout_with_untracked_files_overwritten_by_in_second_repo("checkout")
  }

  fun test_merge_with_untracked_files_overwritten_by_checkout_in_second_repo_should_show_rollback_proposal_with_file_list() {
    check_checkout_with_untracked_files_overwritten_by_in_second_repo("merge")
  }

  private fun check_checkout_with_untracked_files_overwritten_by_in_second_repo(operation: String) {
    branchWithCommit(myRepositories, "feature")


    val untracked = Arrays.asList<String>("untracked.txt")
    untrackedFileOverwrittenBy(myCommunity, "feature", untracked)

    val untrackedPaths = ContainerUtil.newArrayList<String>()
    checkoutOrMerge(operation, "feature", object : TestUiHandler() {
      override fun showUntrackedFilesDialogWithRollback(operationName: String,
                                                        rollbackProposal: String,
                                                        root: VirtualFile,
                                                        relativePaths: Collection<String>): Boolean {
        untrackedPaths.addAll(relativePaths)
        return false
      }
    })

    assertTrue("Untracked files dialog was not shown", !untrackedPaths.isEmpty())
    assertEquals("Incorrect set of untracked files was shown in the dialog", untracked, untrackedPaths)
  }

  fun test_checkout_with_local_changes_overwritten_by_checkout_should_show_smart_checkout_dialog() {
    check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("checkout", 1)
  }

  fun test_checkout_with_several_local_changes_overwritten_by_checkout_should_show_smart_checkout_dialog() {
    check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("checkout", 3)
  }

  fun test_merge_with_local_changes_overwritten_by_merge_should_show_smart_merge_dialog() {
    check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog("merge", 1)
  }

  private fun check_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog(operation: String, numFiles: Int) {
    val expectedChanges = prepareLocalChangesOverwrittenBy(myUltimate, numFiles)

    val actualChanges = ContainerUtil.newArrayList<Change>()
    checkoutOrMerge(operation, "feature", object : TestUiHandler() {
      override fun showSmartOperationDialog(project: Project,
                                            changes: List<Change>,
                                            paths: Collection<String>,
                                            operation: String,
                                            forceButton: String?): GitSmartOperationDialog.Choice {
        actualChanges.addAll(changes)
        return GitSmartOperationDialog.Choice.CANCEL
      }
    })

    assertFalse("Local changes were not shown in the dialog", actualChanges.isEmpty())
    if (newGitVersion()) {
      val actualPaths = actualChanges.map { FileUtil.getRelativePath(myUltimate.root.path, it.afterRevision!!.file.path, '/')!! }
      assertSameElements("Incorrect set of local changes was shown in the dialog", actualPaths, expectedChanges)
    }
  }

  fun test_agree_to_smart_checkout_should_smart_checkout() {
    val localChanges = agree_to_smart_operation("checkout", "Checked out <b><code>feature</code></b>")

    assertCurrentBranch("feature")
    cd(myUltimate)
    val actual = cat(localChanges[0])
    val expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
        LOCAL_CHANGES_OVERWRITTEN_BY.initial +
        LOCAL_CHANGES_OVERWRITTEN_BY.masterLine
    assertContent(expectedContent, actual)
  }

  fun test_agree_to_smart_merge_should_smart_merge() {
    val localChanges = agree_to_smart_operation("merge",
                                                "Merged <b><code>feature</code></b> to <b><code>master</code></b><br/><a href='delete'>Delete feature</a>")

    cd(myUltimate)
    val actual = cat(localChanges.first())
    val expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
        LOCAL_CHANGES_OVERWRITTEN_BY.initial +
        LOCAL_CHANGES_OVERWRITTEN_BY.masterLine
    assertContent(expectedContent, actual)
  }

  private fun agree_to_smart_operation(operation: String, expectedSuccessMessage: String): List<String> {
    val localChanges = prepareLocalChangesOverwrittenBy(myUltimate)
    checkoutOrMerge(operation, "feature", TestUiHandler())
    assertSuccessfulNotification(expectedSuccessMessage)
    return localChanges
  }

  private fun prepareLocalChangesOverwrittenBy(repository: GitRepository, numFiles: Int = 1): List<String> {
    val localChanges = ContainerUtil.newArrayList<String>()
    (0..numFiles - 1).mapTo(localChanges) { String.format("local%d.txt", it) }
    localChangesOverwrittenByWithoutConflict(repository, "feature", localChanges)
    updateChangeListManager()

    myRepositories
      .filter { it != repository }
      .forEach { branchWithCommit(it, "feature") }
    return localChanges
  }

  fun test_deny_to_smart_checkout_in_first_repo_should_show_nothing() {
    check_deny_to_smart_operation_in_first_repo_should_show_nothing("checkout")
  }

  fun test_deny_to_smart_merge_in_first_repo_should_show_nothing() {
    check_deny_to_smart_operation_in_first_repo_should_show_nothing("merge")
  }

  fun check_deny_to_smart_operation_in_first_repo_should_show_nothing(operation: String) {
    prepareLocalChangesOverwrittenBy(myUltimate)

    checkoutOrMerge(operation, "feature", object : TestUiHandler() {
      override fun showSmartOperationDialog(project: Project,
                                            changes: List<Change>,
                                            paths: Collection<String>,
                                            operation: String,
                                            forceButton: String?) = GitSmartOperationDialog.Choice.CANCEL
    })

    assertNull("Notification was unexpectedly shown:" + myVcsNotifier.lastNotification, myVcsNotifier.lastNotification)
    assertCurrentBranch("master")
  }

  fun test_deny_to_smart_checkout_in_second_repo_should_show_rollback_proposal() {
    check_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal("checkout")
    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "master")
    assertCurrentBranch(myContrib, "master")
  }

  fun test_deny_to_smart_merge_in_second_repo_should_show_rollback_proposal() {
    check_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal("merge")
  }

  fun check_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal(operation: String) {
    prepareLocalChangesOverwrittenBy(myCommunity)

    val rollbackMsg = Ref.create<String>()
    checkoutOrMerge(operation, "feature", object : TestUiHandler() {
      override fun showSmartOperationDialog(project: Project,
                                            changes: List<Change>,
                                            paths: Collection<String>,
                                            operation: String,
                                            forceButton: String?) = CANCEL

      override fun notifyErrorWithRollbackProposal(title: String,
                                                   message: String,
                                                   rollbackProposal: String): Boolean {
        rollbackMsg.set(message)
        return false
      }
    })

    assertNotNull("Rollback proposal was not shown", rollbackMsg.get())
  }

  fun test_force_checkout_in_case_of_local_changes_that_would_be_overwritten_by_checkout() {
    // IDEA-99849
    prepareLocalChangesOverwrittenBy(myUltimate)

    val brancher = GitBranchWorker(myProject, myGit, object : TestUiHandler() {
      override fun showSmartOperationDialog(project: Project,
                                            changes: List<Change>,
                                            paths: Collection<String>,
                                            operation: String,
                                            forceButton: String?) = FORCE
    })
    brancher.checkoutNewBranchStartingFrom("new_branch", "feature", myRepositories)

    assertSuccessfulNotification("Checked out new branch <b><code>new_branch</code></b> from <b><code>feature</code></b>")
    assertCurrentBranch("new_branch")
  }

  fun test_rollback_of_checkout_branch_as_new_branch_should_delete_branches() {
    branchWithCommit(myRepositories, "feature")
    touch("feature.txt", "feature_content")
    git("add feature.txt")
    git("commit -m feature_changes")
    git("checkout master")

    unmergedFiles(myCommunity)

    var rollbackProposed = false
    val brancher = GitBranchWorker(myProject, myGit, object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed = true
        return true
      }
    })
    brancher.checkoutNewBranchStartingFrom("newBranch", "feature", myRepositories)

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
    assertCurrentBranch("master")
    myRepositories.forEach { assertBranchDeleted(it, "newBranch") }
  }

  fun test_delete_branch_that_is_fully_merged() {
    val todelete = "todelete"
    for (repository in myRepositories) {
      git(repository, "branch $todelete")
    }

    deleteBranch(todelete, TestUiHandler())

    `assert successful deleted branch notification`(todelete, false, RESTORE)
  }

  fun test_delete_unmerged_branch_should_restore_on_link_click() {
    prepareUnmergedBranch(myCommunity)

    myCommunity.deleteBranch("todelete")
    val notification = `assert successful deleted branch notification`("todelete", true, RESTORE, VIEW_COMMITS)
    val restoreAction = findAction(notification, RESTORE)

    myVcsNotifier.cleanup()
    runInEdtAndWait { Notification.fire(notification, restoreAction) }
    assertBranchExists(myCommunity, "todelete")
    assertNoNotification()
  }

  fun `test restore branch deletion should restore tracking`() {
    prepareRemoteRepo(myCommunity)
    cd(myCommunity)
    val feature = "feature"
    git("checkout -b $feature")
    git("push -u origin $feature")
    git("checkout master")

    myCommunity.deleteBranch(feature)

    val notification = `assert successful deleted branch notification`(feature, false, RESTORE, DELETE_TRACKED_BRANCH)
    val restoreAction = findAction(notification, RESTORE)
    runInEdtAndWait { Notification.fire(notification, restoreAction) }
    assertBranchExists(myCommunity, feature)
    val trackInfo = getTrackInfoForBranch(myCommunity, myCommunity.branches.findLocalBranch(feature)!!)
    assertNotNull("Track info should be preserved", trackInfo)
    assertEquals("Tracked branch is incorrect", "origin/$feature", trackInfo!!.remoteBranch.nameForLocalOperations)
  }

  private fun findAction(notification: Notification,
                         actionTitle: String) = notification.actions.find { it.templatePresentation.text == actionTitle }!!

  fun test_ok_in_unmerged_branch_dialog_should_force_delete_branch() {
    prepareUnmergedBranch(myUltimate)
    deleteBranch("todelete", object : TestUiHandler() {
      override fun showBranchIsNotFullyMergedDialog(project: Project,
                                                    history: Map<GitRepository, List<GitCommit>>,
                                                    baseBranches: Map<GitRepository, String>,
                                                    removedBranch: String) = true
    })
    assertBranchDeleted("todelete")
  }

  fun test_rollback_delete_branch_should_recreate_branches() {
    prepare_delete_branch_failure_in_2nd_repo()

    var rollbackMsg: String? = null
    deleteBranch("todelete", object : TestUiHandler() {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        rollbackMsg = message
        return true
      }
    })

    assertNotNull("Rollback messages was not shown", rollbackMsg)
    assertBranchExists(myUltimate, "todelete")
    assertBranchExists(myCommunity, "todelete")
    assertBranchExists(myContrib, "todelete")
  }

  fun test_deny_rollback_delete_branch_should_do_nothing() {
    prepare_delete_branch_failure_in_2nd_repo()

    var rollbackMsg: String? = null
    deleteBranch("todelete", object : TestUiHandler() {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        rollbackMsg = message
        return false
      }
    })

    assertNotNull("Rollback messages was not shown", rollbackMsg)
    assertBranchDeleted(myUltimate, "todelete")
    assertBranchExists(myCommunity, "todelete")
    assertBranchExists(myContrib, "todelete")
  }

  fun test_delete_branch_merged_to_head_but_unmerged_to_upstream_should_mention_this_in_notification() {
    // inspired by IDEA-83604
    // for the sake of simplicity we deal with a single myCommunity repository for remote operations
    val feature = "feature"
    prepareRemoteRepo(myCommunity)
    cd(myCommunity)
    git("checkout -b $feature")
    git("push -u origin $feature")

    // create a commit and merge it to master, but not to feature's upstream
    touch("feature.txt", "feature content")
    git("add feature.txt")
    git("commit -m feature_branch")
    git("checkout master")
    git("merge $feature")

    // delete feature fully merged to current HEAD, but not to the upstream
    var dialogShown = false
    val brancher = GitBranchWorker(myProject, myGit, object : TestUiHandler() {
      override fun showBranchIsNotFullyMergedDialog(project: Project,
                                                    history: Map<GitRepository, List<GitCommit>>,
                                                    baseBranches: Map<GitRepository, String>,
                                                    removedBranch: String): Boolean {
        dialogShown = true
        return false
      }
    })

    brancher.deleteBranch(feature, listOf(myCommunity))
    val notification = `assert successful deleted branch notification`(feature, true, RESTORE, VIEW_COMMITS, DELETE_TRACKED_BRANCH)
    val viewAction = findAction(notification, VIEW_COMMITS)
    assertFalse("'Branch is not fully merged' dialog shouldn't be shown yet", dialogShown)
    runInEdtAndWait { Notification.fire(notification, viewAction) }
    assertTrue("'Branch is not fully merged' dialog was not shown", dialogShown)
  }

  private fun prepare_delete_branch_failure_in_2nd_repo() {
    for (repository in myRepositories) {
      git(repository, "branch todelete")
    }
    myGit.onBranchDelete {
      if (myCommunity == it) GitCommandResult(false, 1, listOf("Couldn't remove branch"), listOf(), null)
      else null
    }
  }

  fun test_simple_merge_without_problems() {
    branchWithCommit(myRepositories, "master2", "branch_file.txt", "branch content")

    mergeBranch("master2", TestUiHandler())

    assertSuccessfulNotification("Merged ${bcode("master2")} to ${bcode("master")}<br/>" +
                                 "<a href='delete'>Delete master2</a>")
    assertFile(myUltimate, "branch_file.txt", "branch content")
    assertFile(myCommunity, "branch_file.txt", "branch content")
    assertFile(myContrib, "branch_file.txt", "branch content")
  }

  fun `test delete branch proposes to delete its tracked branch`() {
    prepareRemoteRepo(myCommunity)
    cd(myCommunity)

    val todelete = "todelete"
    git("branch $todelete")
    git("push -u origin todelete")

    myCommunity.deleteBranch(todelete)

    `assert successful deleted branch notification`(todelete, false, RESTORE, DELETE_TRACKED_BRANCH)
  }

  fun `test delete branch doesn't propose to delete tracked branch, if it is also tracked by another local branch`() {
    prepareRemoteRepo(myCommunity)
    cd(myCommunity)

    val todelete = "todelete"
    git("branch $todelete")
    git("push -u origin todelete")
    git("branch another origin/todelete")

    myCommunity.deleteBranch(todelete)

    `assert successful deleted branch notification`(todelete, false, RESTORE)
  }

  fun test_merge_branch_that_is_up_to_date() {
    for (repository in myRepositories) {
      git(repository, "branch master2")
    }

    mergeBranch("master2", TestUiHandler())

    assertNotNull("Success message wasn't shown", myVcsNotifier.lastNotification)
    assertEquals("Success message is incorrect", "Already up-to-date<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.lastNotification.content)
  }

  fun test_merge_one_simple_and_other_up_to_date() {
    branchWithCommit(myCommunity, "master2", "branch_file.txt", "branch content")
    git(myUltimate, "branch master2")
    git(myContrib, "branch master2")

    mergeBranch("master2", TestUiHandler())

    assertNotNull("Success message wasn't shown", myVcsNotifier.lastNotification)
    assertEquals("Success message is incorrect",
                 "Merged " + bcode("master2") + " to " + bcode("master") + "<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.lastNotification.content)
    assertFile(myCommunity, "branch_file.txt", "branch content")
  }

  fun test_merge_with_unmerged_files_in_first_repo_should_show_notification() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myUltimate)

    var notificationShown = false
    mergeBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesNotification(operationName: String,
                                                 repositories: Collection<GitRepository>) {
        notificationShown = true
      }
    })
    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  fun test_merge_with_unmerged_files_in_second_repo_should_propose_to_rollback() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    var rollbackProposed = false
    mergeBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed = true
        return false
      }
    })
    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  fun test_rollback_merge_should_reset_merge() {
    branchWithCommit(myRepositories, "feature")
    val ultimateTip = tip(myUltimate)
    unmergedFiles(myCommunity)

    mergeBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return true
      }
    })

    assertEquals("Merge in ultimate should have been reset", ultimateTip, tip(myUltimate))
  }

  fun test_deny_rollback_merge_should_leave_as_is() {
    branchWithCommit(myRepositories, "feature")
    cd(myUltimate)
    val ultimateTipAfterMerge = git("rev-list -1 feature")
    unmergedFiles(myCommunity)

    mergeBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return false
      }
    })

    assertEquals("Merge in ultimate should have been reset", ultimateTipAfterMerge, tip(myUltimate))
  }

  fun test_checkout_in_detached_head() {
    cd(myCommunity)
    touch("file.txt", "some content")
    add("file.txt")
    commit("msg")
    git(myCommunity, "checkout HEAD^")

    checkoutBranch("master", TestUiHandler())
    assertCurrentBranch("master")
  }

  // inspired by IDEA-127472
  fun test_checkout_to_common_branch_when_branches_have_diverged() {
    branchWithCommit(myUltimate, "feature", "feature-file.txt", "feature_content", false)
    branchWithCommit(myCommunity, "newbranch", "newbranch-file.txt", "newbranch_content", false)
    checkoutBranch("master", TestUiHandler())
    assertCurrentBranch("master")
  }

  fun test_rollback_checkout_from_diverged_branches_should_return_to_proper_branches() {
    branchWithCommit(myUltimate, "feature", "feature-file.txt", "feature_content", false)
    branchWithCommit(myCommunity, "newbranch", "newbranch-file.txt", "newbranch_content", false)
    unmergedFiles(myContrib)

    checkoutBranch("master", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return true
      }
    })

    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "newbranch")
    assertCurrentBranch(myContrib, "master")
  }

  fun `test delete remote branch`() {
    prepareLocalAndRemoteBranch("feature", track = false)

    deleteRemoteBranch("origin/feature", DeleteRemoteBranchDecision.DELETE)

    assertSuccessfulNotification("Deleted remote branch origin/feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchExists(it, "feature") }
  }

  fun `test delete remote branch should optionally delete the tracking branch as well`() {
    prepareLocalAndRemoteBranch("feature", track = true)

    deleteRemoteBranch("origin/feature", DeleteRemoteBranchDecision.DELETE_WITH_TRACKING)

    assertSuccessfulNotification("Deleted remote branch origin/feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchDeleted(it, "feature") }
  }

  fun `test delete remote branch when its tracking local branch is partially checked out`() {
    prepareLocalAndRemoteBranch("feature", track = true)
    git(myUltimate, "checkout feature")

    GitBranchWorker(myProject, myGit, object : TestUiHandler() {
      override fun confirmRemoteBranchDeletion(branchName: String,
                                               trackingBranches: MutableCollection<String>,
                                               repositories: MutableCollection<GitRepository>): DeleteRemoteBranchDecision {
        assertEmpty("No tracking branches should be proposed for deletion", trackingBranches)
        return DeleteRemoteBranchDecision.DELETE
      }
    }).deleteRemoteBranch("origin/feature", myRepositories)


    assertSuccessfulNotification("Deleted remote branch origin/feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchExists(it, "feature") }
  }

  private fun prepareLocalAndRemoteBranch(name: String, track: Boolean) {
    val parentRoot = File(myTestRoot, "parentRoot")
    parentRoot.mkdir()
    for (repository in myRepositories) {
      git(repository, "branch $name")
      prepareRemoteRepo(repository, File(parentRoot, "${repository.root.name}-parent.git"))
      git(repository, "push ${if (track) "-u" else ""} origin $name")
    }
  }

  private fun `assert remote branch deleted`(repository: GitRepository, name: String) {
    assertNull("Branch should be deleted", repository.branches.findBranchByName(name))
  }

  private fun assertDetachedState(reference: String) {
    for (repository in myRepositories) {
      assertDetachedState(repository, reference)
    }
  }

  private fun assertCurrentBranch(name: String) {
    for (repository in myRepositories) {
      assertCurrentBranch(repository, name)
    }
  }

  private fun assertCurrentRevision(reference: String) {
    for (repository in myRepositories) {
      assertCurrentRevision(repository, reference)
    }
  }

  private fun checkoutNewBranch(name: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(myProject, myGit, uiHandler)
    brancher.checkoutNewBranch(name, myRepositories)
  }

  private fun checkoutBranch(name: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(myProject, myGit, uiHandler)
    brancher.checkout(name, false, myRepositories)
  }

  private fun checkoutRevision(reference: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(myProject, myGit, uiHandler)
    brancher.checkout(reference, true, myRepositories)
  }

  private fun mergeBranch(name: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(myProject, myGit, uiHandler)
    brancher.merge(name, GitBrancher.DeleteOnMergeOption.PROPOSE, myRepositories)
  }

  private fun deleteBranch(name: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(myProject, myGit, uiHandler)
    brancher.deleteBranch(name, myRepositories)
  }

  private fun checkoutOrMerge(operation: String, name: String, uiHandler: GitBranchUiHandler) {
    if (operation == "checkout") {
      checkoutBranch(name, uiHandler)
    }
    else {
      mergeBranch(name, uiHandler)
    }
  }

  private fun prepareUnmergedBranch(unmergedRepo: GitRepository) {
    for (repository in myRepositories) {
      git(repository, "branch todelete")
    }
    cd(unmergedRepo)
    git("checkout todelete")
    touch("afile.txt", "content")
    git("add afile.txt")
    git("commit -m unmerged_commit")
    git("checkout master")
  }

  private fun assertBranchDeleted(name: String) {
    for (repository in myRepositories) {
      assertBranchDeleted(repository, name)
    }
  }

  private fun deleteRemoteBranch(branchName: String, decision: GitBranchUiHandler.DeleteRemoteBranchDecision) {
    GitBranchWorker(myProject, myGit, object : TestUiHandler() {
      override fun confirmRemoteBranchDeletion(branchName: String,
                                               trackingBranches: MutableCollection<String>,
                                               repositories: MutableCollection<GitRepository>): DeleteRemoteBranchDecision {
        return decision
      }
    })
      .deleteRemoteBranch(branchName, myRepositories)
  }

  private fun GitRepository.deleteBranch(branchName: String) {
    GitBranchWorker(myProject, myGit, TestUiHandler()).deleteBranch(branchName, listOf(this))
  }

  private fun `assert successful deleted branch notification`(branchName: String,
                                                              unmergedWarning: Boolean = false,
                                                              vararg actions: String): Notification {
    val title = """<b>Deleted Branch:</b> $branchName"""
    val warning = if (unmergedWarning) "<br/>Unmerged commits were discarded" else ""
    val notification = assertSuccessfulNotification("$title$warning")
    assertOrderedEquals("Notification actions are incorrect", notification.actions.map { it.templatePresentation.text }, *actions)
    return notification
  }

  private open class TestUiHandler : GitBranchUiHandler {
    override fun getProgressIndicator(): ProgressIndicator {
      return EmptyProgressIndicator()
    }

    override fun showSmartOperationDialog(project: Project,
                                          changes: List<Change>,
                                          paths: Collection<String>,
                                          operation: String,
                                          forceButton: String?): GitSmartOperationDialog.Choice = SMART

    override fun showBranchIsNotFullyMergedDialog(project: Project,
                                                  history: Map<GitRepository, List<GitCommit>>,
                                                  baseBranches: Map<GitRepository, String>,
                                                  removedBranch: String): Boolean {
      throw UnsupportedOperationException()
    }

    override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
      throw UnsupportedOperationException()
    }

    override fun showUnmergedFilesNotification(operationName: String, repositories: Collection<GitRepository>) {
      throw UnsupportedOperationException()
    }

    override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
      throw UnsupportedOperationException()
    }

    override fun showUntrackedFilesNotification(operationName: String, root: VirtualFile, relativePaths: Collection<String>) {
      throw UnsupportedOperationException()
    }

    override fun showUntrackedFilesDialogWithRollback(operationName: String,
                                                      rollbackProposal: String,
                                                      root: VirtualFile,
                                                      relativePaths: Collection<String>): Boolean {
      throw UnsupportedOperationException()
    }

    override fun confirmRemoteBranchDeletion(branchName: String,
                                             trackingBranches: MutableCollection<String>,
                                             repositories: MutableCollection<GitRepository>): DeleteRemoteBranchDecision {
      throw UnsupportedOperationException()
    }
  }

  private fun bcode(s: String): String {
    return "<b><code>$s</code></b>"
  }

  private fun newGitVersion(): Boolean {
    return !GitVersionSpecialty.OLD_STYLE_OF_UNTRACKED_AND_LOCAL_CHANGES_WOULD_BE_OVERWRITTEN.existsIn(GitVersion.parse(git("version")))
  }

  private fun tip(repo: GitRepository): String {
    cd(repo)
    return git("rev-list -1 HEAD")
  }

  private fun assertDetachedState(repository: GitRepository, reference: String) {
    assertCurrentRevision(repository, reference)

    val curBranch = getCurrentBranch(repository)
    val isDetached = curBranch.contains("detached")
    assertTrue("Current branch is not detached in \${repository} - $curBranch", isDetached)
  }

  private fun assertCurrentBranch(repository: GitRepository, name: String) {
    val curBranch = getCurrentBranch(repository)
    assertEquals("Current branch is incorrect in \${repository}", name, curBranch)
  }

  private fun getCurrentBranch(repository: GitRepository): String {
    return git(repository, "branch").lines().find { it.contains("*") }!!.replace('*', ' ').trim()
  }

  private fun assertCurrentRevision(repository: GitRepository, reference: String) {
    val expectedRef = git(repository, "rev-parse " + "HEAD")
    val currentRef = git(repository, "rev-parse " + reference)

    assertEquals("Current revision is incorrect in \${repository}", expectedRef, currentRef)
  }

  private fun assertBranchDeleted(repo: GitRepository, branch: String) {
    assertFalse("Branch \$branch should have been deleted from \$repo", git(repo, "branch").contains(branch))
  }

  private fun assertBranchExists(repo: GitRepository, branch: String) {
    assertTrue("Branch \$branch should exist in \$repo", branchExists(repo, branch))
  }

  private fun assertFile(repository: GitRepository, path: String, content: String) {
    cd(repository)
    assertEquals("Content doesn't match", content, cat(path))
  }

  private fun assertContent(expectedContent: String, actual: String) {
    var expectedContent = expectedContent
    var actual = actual
    expectedContent = StringUtil.convertLineSeparators(expectedContent, detectLineSeparators(actual).separatorString).trim()
    actual = actual.trim()
    assertEquals(String.format("Content doesn't match.%nExpected:%n%s%nActual:%n%s%n",
                               substWhitespaces(expectedContent), substWhitespaces(actual)), expectedContent, actual)
  }

  private fun detectLineSeparators(actual: String): LineSeparator {
    val chars = CharArrayUtil.fromSequence(actual)
    for (c in chars) {
      if (c == '\r') {
        return LineSeparator.CRLF
      }
      else if (c == '\n') {   // if we are here, there was no \r before
        return LineSeparator.LF
      }
    }
    return LineSeparator.LF
  }

  private fun substWhitespaces(s: String): String {
    return s.replace(("\r").toRegex(), Matcher.quoteReplacement("\\r")).replace(("\n").toRegex(),
                                                                                Matcher.quoteReplacement("\\n")).replace((" ").toRegex(),
                                                                                                                         "_")
  }
}
