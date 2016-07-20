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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.Function
import com.intellij.util.LineSeparator
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.CharArrayUtil
import git4idea.GitCommit
import git4idea.branch.GitBranchUtil.getTrackInfoForBranch
import git4idea.branch.GitDeleteBranchOperation.*
import git4idea.commands.GitCommandResult
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty
import git4idea.repo.GitRepository
import git4idea.test.GitExecutor.*
import git4idea.test.GitPlatformTest
import git4idea.test.GitScenarios.*
import java.util.*
import java.util.regex.Matcher

class GitBranchWorkerTest : GitPlatformTest() {

  private lateinit var myUltimate: GitRepository
  private lateinit var myCommunity: GitRepository
  private lateinit var myContrib: GitRepository
  private lateinit var myRepositories: List<GitRepository>

  public override fun setUp() {
    super.setUp()

    Executor.cd(myProjectRoot)
    val community = Executor.mkdir("community")
    val contrib = Executor.mkdir("contrib")

    myUltimate = createRepository(myProjectPath)
    myCommunity = createRepository(community.getPath())
    myContrib = createRepository(contrib.getPath())
    myRepositories = Arrays.asList<GitRepository>(myUltimate, myCommunity, myContrib)

    Executor.cd(myProjectRoot)
    Executor.touch(".gitignore", "community\ncontrib")
    git("add .gitignore")
    git("commit -m gitignore")
  }

  fun test_create_new_branch_without_problems() {
    checkoutNewBranch("feature", TestUiHandler())

    assertCurrentBranch("feature")
    assertEquals("Notification about successful branch creation is incorrect",
                 "Branch " + bcode("feature") + " was created", myVcsNotifier.getLastNotification().getContent())
  }

  fun test_create_new_branch_with_unmerged_files_in_first_repo_should_show_notification() {
    unmergedFiles(myUltimate)

    val notificationShown = Ref.create<Boolean>(false)
    checkoutNewBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesNotification(operationName: String, repositories: Collection<GitRepository>) {
        notificationShown.set(true)
      }
    })

    assertTrue("Unmerged files notification was not shown", notificationShown.get())
  }

  fun test_create_new_branch_with_unmerged_files_in_second_repo_should_propose_to_rollback() {
    unmergedFiles(myCommunity)

    val rollbackProposed = Ref.create<Boolean>(false)
    checkoutNewBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed.set(true)
        return false
      }
    })

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed.get())
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
                 myVcsNotifier.getLastNotification().getContent())
  }

  fun test_checkout_with_unmerged_files_in_first_repo_should_show_notification() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myUltimate)

    val notificationShown = Ref.create<Boolean>(false)
    checkoutBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesNotification(operationName: String, repositories: Collection<GitRepository>) {
        notificationShown.set(true)
      }
    })

    assertTrue("Unmerged files notification was not shown", notificationShown.get())
  }

  fun test_checkout_with_unmerged_file_in_second_repo_should_propose_to_rollback() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    val rollbackProposed = Ref.create<Boolean>(false)
    checkoutBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed.set(true)
        return false
      }
    })

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed.get())
  }

  fun test_rollback_checkout_should_return_to_previous_branch() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    checkoutBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return true
      }
    })

    assertCurrentBranch("master")
  }

  fun test_deny_rollback_checkout_should_do_nothing() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    checkoutBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return false
      }
    })

    assertCurrentBranch(myUltimate, "feature")
    assertCurrentBranch(myCommunity, "master")
    assertCurrentBranch(myContrib, "master")
  }

  fun test_checkout_revision_checkout_branch_with_complete_success() {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("feature", TestUiHandler())

    assertDetachedState("feature")
    assertEquals("Notification about successful branch checkout is incorrect", "Checked out " + bcode("feature"),
                 myVcsNotifier.getLastNotification().getContent())
  }

  fun test_checkout_revision_checkout_ref_with_complete_success() {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("feature~1", TestUiHandler())

    assertDetachedState("master")
    assertEquals("Notification about successful branch checkout is incorrect", "Checked out " + bcode("feature~1"),
                 myVcsNotifier.getLastNotification().getContent())
  }

  fun test_checkout_revision_checkout_ref_with_complete_failure() {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("unknown_ref", TestUiHandler())

    assertCurrentBranch("master")
    assertCurrentRevision("master")
    assertEquals("Notification about successful branch checkout is incorrect",
                 "Revision not found in project, community and contrib",
                 myVcsNotifier.getLastNotification().getContent())
  }

  fun test_checkout_revision_checkout_ref_with_partial_success() {
    branchWithCommit(ContainerUtil.list<GitRepository>(myCommunity, myContrib), "feature")

    checkoutRevision("feature", TestUiHandler())

    assertCurrentBranch(myUltimate, "master")
    assertDetachedState(myCommunity, "feature")
    assertDetachedState(myContrib, "feature")

    assertEquals("Notification about successful branch checkout is incorrect",
                 "Checked out " + bcode("feature") + " in community and contrib" + "<br>" +
                     "Revision not found in project" + "<br><a href='rollback'>Rollback</a>",
                 myVcsNotifier.getLastNotification().getContent())
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
    for (i in 0..untrackedFiles - 1) {
      files.add("untracked" + i + ".txt")
    }
    untrackedFileOverwrittenBy(myUltimate, "feature", files)

    val notificationShown = Ref.create<Boolean>(false)
    checkoutOrMerge(operation, "feature", object : TestUiHandler() {
      override fun showUntrackedFilesNotification(operationName: String,
                                                  root: VirtualFile,
                                                  relativePaths: Collection<String>) {
        notificationShown.set(true)
      }
    })

    assertTrue("Untracked files notification was not shown", notificationShown.get())
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
                                            forceButton: String?): Int {
        actualChanges.addAll(changes)
        return DialogWrapper.CANCEL_EXIT_CODE
      }
    })

    assertFalse("Local changes were not shown in the dialog", actualChanges.isEmpty())
    if (newGitVersion()) {
      val actualPaths = ContainerUtil.map<Change, String>(actualChanges, object : Function<Change, String> {
        override fun `fun`(change: Change): String {
          return FileUtil.getRelativePath(myUltimate.getRoot().getPath(), change.getAfterRevision()!!.getFile().getPath(), '/')!!
        }
      })
      assertSameElements("Incorrect set of local changes was shown in the dialog", actualPaths, expectedChanges)
    }
  }

  fun test_agree_to_smart_checkout_should_smart_checkout() {
    val localChanges = agree_to_smart_operation("checkout", "Checked out <b><code>feature</code></b>")

    assertCurrentBranch("feature")
    cd(myUltimate)
    val actual = Executor.cat(localChanges.get(0))
    val expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
        LOCAL_CHANGES_OVERWRITTEN_BY.initial +
        LOCAL_CHANGES_OVERWRITTEN_BY.masterLine
    assertContent(expectedContent, actual)
  }

  fun test_agree_to_smart_merge_should_smart_merge() {
    val localChanges = agree_to_smart_operation("merge",
                                                "Merged <b><code>feature</code></b> to <b><code>master</code></b><br/><a href='delete'>Delete feature</a>")

    cd(myUltimate)
    val actual = Executor.cat(ContainerUtil.getFirstItem<String>(localChanges)!!)
    val expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
        LOCAL_CHANGES_OVERWRITTEN_BY.initial +
        LOCAL_CHANGES_OVERWRITTEN_BY.masterLine
    assertContent(expectedContent, actual)
  }

  private fun agree_to_smart_operation(operation: String, expectedSuccessMessage: String): List<String> {
    val localChanges = prepareLocalChangesOverwrittenBy(myUltimate)

    val handler = TestUiHandler()
    checkoutOrMerge(operation, "feature", handler)

    assertNotNull("No success notification was shown", myVcsNotifier.getLastNotification())
    assertEquals("Success message is incorrect", expectedSuccessMessage, myVcsNotifier.getLastNotification().getContent())

    return localChanges
  }

  @JvmOverloads internal fun prepareLocalChangesOverwrittenBy(repository: GitRepository, numFiles: Int = 1): List<String> {
    val localChanges = ContainerUtil.newArrayList<String>()
    for (i in 0..numFiles - 1) {
      localChanges.add(String.format("local%d.txt", i))
    }
    localChangesOverwrittenByWithoutConflict(repository, "feature", localChanges)
    updateChangeListManager()

    for (repo in myRepositories) {
      if (repo != repository) {
        branchWithCommit(repo, "feature")
      }
    }
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
                                            forceButton: String?): Int {
        return GitSmartOperationDialog.CANCEL_EXIT_CODE
      }
    })

    assertNull("Notification was unexpectedly shown:" + myVcsNotifier.getLastNotification(), myVcsNotifier.getLastNotification())
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
                                            forceButton: String?): Int {
        return GitSmartOperationDialog.CANCEL_EXIT_CODE
      }

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
                                            forceButton: String?): Int {
        return GitSmartOperationDialog.FORCE_EXIT_CODE
      }
    })
    brancher.checkoutNewBranchStartingFrom("new_branch", "feature", myRepositories!!)

    assertEquals("Notification about successful branch creation is incorrect",
                 "Checked out new branch <b><code>new_branch</code></b> from <b><code>feature</code></b>",
                 myVcsNotifier.getLastNotification().getContent())
    assertCurrentBranch("new_branch")
  }

  fun test_rollback_of_checkout_branch_as_new_branch_should_delete_branches() {
    branchWithCommit(myRepositories, "feature")
    Executor.touch("feature.txt", "feature_content")
    git("add feature.txt")
    git("commit -m feature_changes")
    git("checkout master")

    unmergedFiles(myCommunity)

    val rollbackProposed = Ref.create<Boolean>(false)
    val brancher = GitBranchWorker(myProject, myGit, object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed.set(true)
        return true
      }
    })
    brancher.checkoutNewBranchStartingFrom("newBranch", "feature", myRepositories!!)

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed.get())
    assertCurrentBranch("master")
    for (repository in myRepositories!!) {
      assertFalse("Branch 'newBranch' should have been deleted on rollback",
                  ContainerUtil.exists<String>(
                      git(repository, "branch").split(("\n").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray(),
                      object : Condition<String> {
                        override fun value(s: String): Boolean {
                          return s.contains("newBranch")
                        }
                      }))
    }
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
    val notification = `assert successful deleted branch notification`("todelete", true, RESTORE, VIEW_COMMITS);
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

    val notification = `assert successful deleted branch notification`(feature, false, RESTORE, DELETE_TRACKED_BRANCH);
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
                                                    removedBranch: String): Boolean {
        return true
      }
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
    Executor.touch("feature.txt", "feature content")
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
    val notification = `assert successful deleted branch notification`(feature, true, RESTORE, VIEW_COMMITS, DELETE_TRACKED_BRANCH);
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

    assertNotNull("Success message wasn't shown", myVcsNotifier.getLastNotification())
    assertEquals("Success message is incorrect",
                 "Merged " + bcode("master2") + " to " + bcode("master") + "<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.getLastNotification().getContent())
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
    for (repository in myRepositories!!) {
      git(repository, "branch master2")
    }

    mergeBranch("master2", TestUiHandler())

    assertNotNull("Success message wasn't shown", myVcsNotifier.getLastNotification())
    assertEquals("Success message is incorrect", "Already up-to-date<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.getLastNotification().getContent())
  }

  fun test_merge_one_simple_and_other_up_to_date() {
    branchWithCommit(myCommunity, "master2", "branch_file.txt", "branch content")
    git(myUltimate, "branch master2")
    git(myContrib, "branch master2")

    mergeBranch("master2", TestUiHandler())

    assertNotNull("Success message wasn't shown", myVcsNotifier.getLastNotification())
    assertEquals("Success message is incorrect",
                 "Merged " + bcode("master2") + " to " + bcode("master") + "<br/><a href='delete'>Delete master2</a>",
                 myVcsNotifier.getLastNotification().getContent())
    assertFile(myCommunity, "branch_file.txt", "branch content")
  }

  fun test_merge_with_unmerged_files_in_first_repo_should_show_notification() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myUltimate)

    val notificationShown = Ref.create<Boolean>(false)
    mergeBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesNotification(operationName: String,
                                                 repositories: Collection<GitRepository>) {
        notificationShown.set(true)
      }
    })
    assertTrue("Unmerged files notification was not shown", notificationShown.get())
  }

  fun test_merge_with_unmerged_files_in_second_repo_should_propose_to_rollback() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(myCommunity)

    val rollbackProposed = Ref.create<Boolean>(false)
    mergeBranch("feature", object : TestUiHandler() {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed.set(true)
        return false
      }
    })
    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed.get())
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
    Executor.touch("file.txt", "some content")
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

  private fun assertDetachedState(reference: String) {
    for (repository in myRepositories!!) {
      assertDetachedState(repository, reference)
    }
  }

  private fun assertCurrentBranch(name: String) {
    for (repository in myRepositories!!) {
      assertCurrentBranch(repository, name)
    }
  }

  private fun assertCurrentRevision(reference: String) {
    for (repository in myRepositories!!) {
      assertCurrentRevision(repository, reference)
    }
  }

  private fun checkoutNewBranch(name: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(myProject, myGit, uiHandler)
    brancher.checkoutNewBranch(name, myRepositories!!)
  }

  private fun checkoutBranch(name: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(myProject, myGit, uiHandler)
    brancher.checkout(name, false, myRepositories!!)
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
    Executor.touch("afile.txt", "content")
    git("add afile.txt")
    git("commit -m unmerged_commit")
    git("checkout master")
  }

  private fun assertBranchDeleted(name: String) {
    for (repository in myRepositories) {
      assertBranchDeleted(repository, name)
    }
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
                                          forceButton: String?): Int {
      return GitSmartOperationDialog.SMART_EXIT_CODE
    }

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
    assertTrue("Current branch is not detached in \${repository} - " + curBranch, isDetached)
  }

  private fun assertCurrentBranch(repository: GitRepository, name: String) {
    val curBranch = getCurrentBranch(repository)
    assertEquals("Current branch is incorrect in \${repository}", name, curBranch)
  }

  private fun getCurrentBranch(repository: GitRepository): String {
    return ObjectUtils.assertNotNull<String>(
        ContainerUtil.find<String>(git(repository, "branch").split(("\n").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray(),
                                   object : Condition<String> {
                                     override fun value(s: String): Boolean {
                                       return s.contains("*")
                                     }
                                   })).replace('*', ' ').trim({ it <= ' ' })
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
    assertEquals("Content doesn't match", content, Executor.cat(path))
  }

  private fun assertContent(expectedContent: String, actual: String) {
    var expectedContent = expectedContent
    var actual = actual
    expectedContent = StringUtil.convertLineSeparators(expectedContent, detectLineSeparators(actual).getSeparatorString()).trim(
        { it <= ' ' })
    actual = actual.trim({ it <= ' ' })
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
