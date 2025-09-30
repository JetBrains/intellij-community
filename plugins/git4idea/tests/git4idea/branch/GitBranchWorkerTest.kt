// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.repo.Repository
import com.intellij.notification.Notification
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.getRelativePath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.LineSeparator
import git4idea.GitCommit
import git4idea.GitLocalBranch
import git4idea.GitNotificationIdsHolder
import git4idea.branch.GitBranchUiHandler.DeleteRemoteBranchDecision
import git4idea.branch.GitBranchUtil.getTrackInfo
import git4idea.branch.GitBranchUtil.getTrackInfoForBranch
import git4idea.branch.GitDeleteBranchOperation.*
import git4idea.branch.GitSmartOperationDialog.Choice.*
import git4idea.commands.GitCommandResult
import git4idea.config.GitSharedSettings
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty
import git4idea.repo.GitRepository
import git4idea.test.*
import git4idea.test.GitScenarios.*
import java.io.File
import java.nio.file.Files

class GitBranchWorkerTest : GitPlatformTest() {
  private lateinit var first: GitRepository
  private lateinit var second: GitRepository
  private lateinit var last: GitRepository
  private lateinit var myRepositories: List<GitRepository>

  public override fun setUp() {
    super.setUp()

    cd(projectRoot)
    val community = mkdir("community")
    val contrib = mkdir("contrib")

    first = createRepository(community.path)
    second = createRepository(contrib.path)
    last = createRepository(projectPath)
    myRepositories = listOf(first, second, last)

    cd(projectRoot)
    touch(".gitignore", "community\ncontrib")
    git("add .gitignore")
    git("commit -m gitignore")
    last.update()
  }

  fun `test create new branch without problems`() {
    checkoutNewBranch("feature", TestUiHandler(project))

    assertCurrentBranch("feature")
    assertSuccessfulNotification("Branch ${code("feature")} was created")
  }

  fun `test create new branch without checkout not at HEAD`() {
    val hashMap = myRepositories.map { it to it.currentRevision!! }.toMap()
    myRepositories.forEach { cd(it); it.tac("f.txt") }

    GitBranchWorker(project, git, TestUiHandler(project)).createBranch("feature", myRepositories.map { it to "HEAD^" }.toMap())

    assertCurrentBranch("master")
    myRepositories.forEach {
      val branch = it.branches.findLocalBranch("feature")
      assertNotNull("Branch not created in $it", branch)
      assertEquals("Branch feature created at wrong point", hashMap[it], it.branches.getHash(branch!!)!!.asString())
    }
    assertSuccessfulNotification("Branch ${code("feature")} was created")
  }

  fun `test if create new branch fails with error in first repo, then notification should be shown`() {
    git.onCheckoutNewBranch { if (it == first) GitCommandResult.error(UNKNOWN_ERROR_TEXT) else null }

    checkoutNewBranch("feature", TestUiHandler(project))

    assertErrorNotification("Could not create new branch feature", "unknown error")
  }

  fun `test if create new branch fails with error in second repo, then we should propose to rollback`() {
    git.onCheckoutNewBranch { if (it == second) GitCommandResult.error(UNKNOWN_ERROR_TEXT) else null }

    var rollbackProposed = false
    checkoutNewBranch("feature", object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        rollbackProposed = true
        return false
      }
    })

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  fun `test rollback create new branch should delete branch`() {
    git.onCheckoutNewBranch { if (it == second) GitCommandResult.error(UNKNOWN_ERROR_TEXT) else null }

    checkoutNewBranch("feature", object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        return true
      }
    })

    assertCurrentBranch("master")
    assertBranchDeleted(last, "feature")
  }

  fun `test deny rollback create new branch should leave new branch`() {
    git.onCheckoutNewBranch { if (it == second) GitCommandResult.error(UNKNOWN_ERROR_TEXT) else null }

    checkoutNewBranch("feature", object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        return false
      }
    })

    assertCurrentBranch(first, "feature")
    assertCurrentBranch(second, "master")
    assertCurrentBranch(last, "master")
  }

  fun `test checkout without problems`() {
    branchWithCommit(myRepositories, "feature")

    checkoutBranch("feature", TestUiHandler(project))

    assertCurrentBranch("feature")
    assertEquals("Notification about successful branch checkout is incorrect", "Checked out " + code("feature"),
                 vcsNotifier.lastNotification.content)
  }

  fun `test checkout with unmerged files in first repo should show notification`() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(first)

    var notificationShown = false
    checkoutBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesNotification(operationName: String, repositories: Collection<GitRepository>) {
        notificationShown = true
      }
    })

    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  fun `test checkout with unmerged file in second repo should propose to rollback`() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(second)

    var rollbackProposed = false
    checkoutBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed = true
        return false
      }
    })

    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  fun `test rollback checkout should return to previous branch`() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(second)

    checkoutBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String) = true
    })

    assertCurrentBranch("master")
  }

  fun `test deny rollback checkout should do nothing`() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(second)

    checkoutBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String) = false
    })

    assertCurrentBranch(first, "feature")
    assertCurrentBranch(second, "master")
    assertCurrentBranch(last, "master")
  }

  fun `test checkout revision checkout branch with complete success`() {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("feature", TestUiHandler(project))

    assertDetachedState("feature")
    assertSuccessfulNotification("Checked out ${code("feature")}")
  }

  fun `test checkout revision checkout ref with complete success`() {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("feature~1", TestUiHandler(project))

    assertDetachedState("master")
    assertSuccessfulNotification("Checked out ${code("feature~1")}")
  }

  fun `test checkout revision checkout ref with complete failure`() {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("unknown_ref", TestUiHandler(project))

    assertCurrentBranch("master")
    assertCurrentRevision("master")
    assertErrorNotification("Could not checkout unknown_ref",
                            "Revision not found in community, contrib and ${project.stateStore.projectBasePath.fileName}")
  }

  fun `test checkout revision checkout ref with partial success`() {
    branchWithCommit(listOf(first, second), "feature")

    checkoutRevision("feature", TestUiHandler(project))

    assertCurrentBranch(last, "master")
    assertDetachedState(first, "feature")
    assertDetachedState(second, "feature")

    assertSuccessfulNotification("Checked out ${code("feature")} in community and contrib<br/>" +
                                 "Revision not found in ${project.stateStore.projectBasePath.fileName}", actions = listOf("Rollback"))
  }

  fun `test checkout with untracked files overwritten by checkout in first repo should show notification`() {
    `test untracked files overwritten by in first repo`("checkout", 1)
  }

  fun `test checkout with several untracked files overwritten by checkout in first repo should show notification`() {
    // note that in old Git versions only one file is listed in the error.
    `test untracked files overwritten by in first repo`("checkout", 3)
  }

  fun `test merge with untracked files overwritten by checkout in first repo should show notification`() {
    `test untracked files overwritten by in first repo`("merge", 1)
  }

  private fun `test untracked files overwritten by in first repo`(operation: String, untrackedFiles: Int) {
    branchWithCommit(myRepositories, "feature")

    val files = mutableListOf<String>()
    (0 until untrackedFiles).mapTo(files) { "untracked$it.txt" }
    untrackedFileOverwrittenBy(first, "feature", files)

    var notificationShown = false
    checkoutOrMerge(operation, "feature", object : TestUiHandler(project) {
      override fun showUntrackedFilesNotification(operationName: String,
                                                  root: VirtualFile,
                                                  relativePaths: Collection<String>) {
        notificationShown = true
      }
    })

    assertTrue("Untracked files notification was not shown", notificationShown)
  }

  fun `test checkout with untracked files overwritten by checkout in second repo should show rollback proposal with file list`() {
    `check checkout with untracked files overwritten by in second repo`("checkout")
  }

  fun `test merge with untracked files overwritten by checkout in second repo should show rollback proposal with file list`() {
    `check checkout with untracked files overwritten by in second repo`("merge")
  }

  private fun `check checkout with untracked files overwritten by in second repo`(operation: String) {
    branchWithCommit(myRepositories, "feature")


    val untracked = listOf("untracked.txt")
    untrackedFileOverwrittenBy(second, "feature", untracked)

    val untrackedPaths = mutableListOf<String>()
    checkoutOrMerge(operation, "feature", object : TestUiHandler(project) {
      override fun showUntrackedFilesDialogWithRollback(operationName: String,
                                                        rollbackProposal: String,
                                                        root: VirtualFile,
                                                        relativePaths: Collection<String>): Boolean {
        untrackedPaths.addAll(relativePaths)
        return false
      }
    })

    assertTrue("Untracked files dialog was not shown", untrackedPaths.isNotEmpty())
    assertEquals("Incorrect set of untracked files was shown in the dialog", untracked, untrackedPaths)
  }

  fun `test checkout with local changes overwritten by checkout should show smart checkout dialog`() {
    `check operation with local changes overwritten by should show smart checkout dialog`("checkout", 1)
  }

  fun `test checkout with several local changes overwritten by checkout should show smart checkout dialog`() {
    `check operation with local changes overwritten by should show smart checkout dialog`("checkout", 3)
  }

  fun `test merge with local changes overwritten by merge should show smart merge dialog`() {
    `check operation with local changes overwritten by should show smart checkout dialog`("merge", 1)
  }

  fun `test merge with several local changes overwritten by merge should show smart merge dialog`() {
    `check operation with local changes overwritten by should show smart checkout dialog`("merge", 3)
  }

  /**
   * IJPL-200234
   * In this scenario the error output is the following:
   * Your local changes to the following files would be overwritten by merge:
   * <2 whitespaces>dir/other test
   */
  fun `test merge with local changes printing paths in a single line`() {
    val repo = first
    val fileName = "test"
    val anotherFileName = "dir/other"
    val branchName = "feature"

    cd(repo)
    repo.file(fileName).create("line").addCommit("init")

    repo.git("checkout -b $branchName")
    repo.file(fileName).write("test").addCommit("alt-branch")
    repo.git("checkout master")
    repo.file(fileName).write("content").addCommit("back to master")
    repo.file(fileName).write("!!!").add()
    repo.file(anotherFileName).write("test").add()
    updateChangeListManager()

    val changedPaths = tryMergeAndGetChangedPaths(branchName, repo)
    assertSameElements(changedPaths, setOf(fileName, anotherFileName))
  }

  // IJPL-173728
  fun `test merge with trailing whitespace changes overwritten by checkout should show smart merge dialog`() {
    val repo = first
    val fileName = "test"
    val anotherFileName = "other"
    val branchName = "feature"

    cd(repo)
    repo.file(fileName).create("line").addCommit("init")
    repo.git("checkout -b $branchName")
    repo.file(fileName).write("   ").addCommit("more spaces")
    repo.git("checkout master")
    repo.file(fileName).write("       ").addCommit("even more spaces")
    repo.file(fileName).write(" ").add()
    repo.file(anotherFileName).create().add()
    updateChangeListManager()

    val changedPaths = tryMergeAndGetChangedPaths(branchName, repo)
    assertSameElements(changedPaths, setOf(fileName, anotherFileName))
  }

  private fun tryMergeAndGetChangedPaths(branchName: String, repo: GitRepository): MutableList<String> {
    val changedPaths = mutableListOf<String>()
    mergeBranch(branchName, object : TestUiHandler(this.project) {
      override fun showSmartOperationDialog(
        project: Project, changes: List<Change>,
        paths: Collection<String>,
        operation: String,
        forceButton: String?,
      ): GitSmartOperationDialog.Choice {
        changes.forEach {
          changedPaths.add(getRelativePath(repo.root.path, it.afterRevision!!.file.path, '/')!!)
        }
        return CANCEL
      }
    })
    return changedPaths
  }

  private fun `check operation with local changes overwritten by should show smart checkout dialog`(operation: String, numFiles: Int) {
    val repoWithLocalChangesProblem = first
    val expectedChanges = prepareLocalChangesOverwrittenBy(repoWithLocalChangesProblem, numFiles)

    val actualChanges = mutableListOf<Change>()
    checkoutOrMerge(operation, "feature", object : TestUiHandler(project) {
      override fun showSmartOperationDialog(project: Project,
                                            changes: List<Change>,
                                            paths: Collection<String>,
                                            operation: String,
                                            forceButton: String?): GitSmartOperationDialog.Choice {
        actualChanges.addAll(changes)
        return CANCEL
      }
    })

    assertFalse("Local changes were not shown in the dialog", actualChanges.isEmpty())
    if (newGitVersion()) {
      val actualPaths = actualChanges.map {
        FileUtil.getRelativePath(repoWithLocalChangesProblem.root.path, it.afterRevision!!.file.path, '/')!!
      }
      assertSameElements("Incorrect set of local changes was shown in the dialog", actualPaths, expectedChanges)
    }
  }

  fun `test agree to smart checkout should smart checkout`() {
    val localChanges = `agree to smart operation`("checkout")
    assertSuccessfulNotification("Checked out <code>feature</code>")

    assertCurrentBranch("feature")
    cd(last)
    val actual = cat(localChanges[0])
    val expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
                          LOCAL_CHANGES_OVERWRITTEN_BY.initial +
                          LOCAL_CHANGES_OVERWRITTEN_BY.masterLine
    assertContentIgnoreLineSeparators(expectedContent, actual)
  }

  fun `test agree to smart merge should smart merge`() {
    val localChanges = `agree to smart operation`("merge")
    assertSuccessfulNotification("Merged <code>feature</code> to <code>master</code>", actions = listOf("Delete feature"))

    cd(last)
    val actual = cat(localChanges.first())
    val expectedContent = LOCAL_CHANGES_OVERWRITTEN_BY.branchLine +
                          LOCAL_CHANGES_OVERWRITTEN_BY.initial +
                          LOCAL_CHANGES_OVERWRITTEN_BY.masterLine
    assertContentIgnoreLineSeparators(expectedContent, actual)
  }

  private fun `agree to smart operation`(operation: String): List<String> {
    val localChanges = prepareLocalChangesOverwrittenBy(last)
    checkoutOrMerge(operation, "feature", TestUiHandler(project))
    return localChanges
  }

  private fun prepareLocalChangesOverwrittenBy(repository: GitRepository, numFiles: Int = 1): List<String> {
    val localChanges = mutableListOf<String>()
    (0 until numFiles).mapTo(localChanges) { String.format("local%d.txt", it) }
    localChangesOverwrittenByWithoutConflict(repository, "feature", localChanges)
    updateChangeListManager()

    myRepositories
      .filter { it != repository }
      .forEach { branchWithCommit(it, "feature") }
    return localChanges
  }

  fun `test deny to smart checkout in first repo should show nothing`() {
    `check deny to smart operation in first repo should show nothing`("checkout")
  }

  fun `test deny to smart merge in first repo should show nothing`() {
    `check deny to smart operation in first repo should show nothing`("merge")
  }

  fun `test local changes would be overwritten in several repositories`() {
    val local1 = "local1.txt"
    localChangesOverwrittenByWithoutConflict(first, "feature", listOf(local1))

    // in addition to a local change preventing checkout...
    cd(second)
    val local2 = second.file("local2.txt")
    local2.create(LOCAL_CHANGES_OVERWRITTEN_BY.initial).addCommit("initial-local2")
    git("checkout -b feature")
    local2.prepend(LOCAL_CHANGES_OVERWRITTEN_BY.branchLine).addCommit("feature-local2")
    // ... make another file producing diff between master and feature (but not related to the 'local change would be overwritten' error)
    second.file("feature.txt").create("feature\n").addCommit("feature.txt")
    git("checkout master")
    local2.append(LOCAL_CHANGES_OVERWRITTEN_BY.masterLine)

    cd(last)
    git("branch feature")

    val file1 = File(first.root.path, local1)
    val file2 = local2.file
    val expectedLocalChanges = listOf(file1, file2).map { FileUtil.toSystemIndependentName(it.path) }

    updateChangeListManager()

    var smartOperationDialogTimes = 0
    val filesInDialog = mutableListOf<String>()
    checkoutOrMerge("checkout", "feature", object : TestUiHandler(project) {
      override fun showSmartOperationDialog(project: Project,
                                            changes: List<Change>,
                                            paths: Collection<String>,
                                            operation: String,
                                            forceButton: String?): GitSmartOperationDialog.Choice {
        smartOperationDialogTimes++
        filesInDialog.addAll(ChangesUtil.getPaths(changes).map { it.path })
        return SMART
      }
    })

    assertSameElements("Local changes would be overwritten by checkout are shown incorrectly", filesInDialog, expectedLocalChanges)
    assertEquals("Smart checkout dialog should be shown only once", 1, smartOperationDialogTimes)
  }

  private fun `check deny to smart operation in first repo should show nothing`(operation: String) {
    prepareLocalChangesOverwrittenBy(first)

    checkoutOrMerge(operation, "feature", object : TestUiHandler(project) {
      override fun showSmartOperationDialog(project: Project,
                                            changes: List<Change>,
                                            paths: Collection<String>,
                                            operation: String,
                                            forceButton: String?) = CANCEL
    })

    assertNull("Notification was unexpectedly shown:" + vcsNotifier.lastNotification, vcsNotifier.lastNotification)
    assertCurrentBranch("master")
  }

  fun `test deny to smart checkout in second repo should show rollback proposal`() {
    `check deny to smart operation in second repo should show rollback proposal`("checkout")
    assertCurrentBranch(first, "feature")
    assertCurrentBranch(second, "master")
    assertCurrentBranch(last, "master")
  }

  fun `test deny to smart merge in second repo should show rollback proposal`() {
    `check deny to smart operation in second repo should show rollback proposal`("merge")
  }

  private fun `check deny to smart operation in second repo should show rollback proposal`(operation: String) {
    prepareLocalChangesOverwrittenBy(second)

    val rollbackMsg = Ref.create<String>()
    checkoutOrMerge(operation, "feature", object : TestUiHandler(project) {
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

  fun `test force checkout in case of local changes that would be overwritten by checkout`() {
    // IDEA-99849
    prepareLocalChangesOverwrittenBy(last)

    val brancher = GitBranchWorker(project, git, object : TestUiHandler(project) {
      override fun showSmartOperationDialog(project: Project,
                                            changes: List<Change>,
                                            paths: Collection<String>,
                                            operation: String,
                                            forceButton: String?) = FORCE
    })
    brancher.checkoutNewBranchStartingFrom("new_branch", "feature", myRepositories)

    assertSuccessfulNotification("Checked out new branch <code>new_branch</code> from <code>feature</code>")
    assertCurrentBranch("new_branch")
  }

  fun `test rollback of checkout branch as new branch should delete branches`() {
    branchWithCommit(myRepositories, "feature")
    touch("feature.txt", "feature_content")
    git("add feature.txt")
    git("commit -m feature_changes")
    git("checkout master")

    unmergedFiles(second)

    var rollbackProposed = false
    val brancher = GitBranchWorker(project, git, object : TestUiHandler(project) {
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

  fun `test delete branch that is fully merged`() {
    val todelete = "todelete"
    for (repository in myRepositories) {
      repository.git("branch $todelete")
    }

    deleteBranch(todelete, TestUiHandler(project))

    `assert successful deleted branch notification`(todelete, false, RESTORE)
  }

  fun `test delete unmerged branch should restore on link click`() {
    prepareUnmergedBranch(first)

    first.deleteBranch("todelete")
    val notification = `assert successful deleted branch notification`("todelete", true, RESTORE, VIEW_COMMITS)
    val restoreAction = findAction(notification, RESTORE)

    vcsNotifier.cleanup()
    runInEdtAndWait { Notification.fire(notification, restoreAction, null) }
    assertBranchExists(first, "todelete")
    assertNoNotification()
  }

  fun `test restore branch deletion should restore tracking`() {
    prepareRemoteRepo(first)
    cd(first)
    val feature = "feature"
    git("checkout -b $feature")
    git("push -u origin $feature")
    git("checkout master")

    first.deleteBranch(feature)

    val notification = `assert successful deleted branch notification`(feature, false, RESTORE, DELETE_TRACKED_BRANCH)
    val restoreAction = findAction(notification, RESTORE)
    runInEdtAndWait { Notification.fire(notification, restoreAction, null) }
    assertBranchExists(first, feature)
    val trackInfo = getTrackInfoForBranch(first, first.branches.findLocalBranch(feature)!!)
    assertNotNull("Track info should be preserved", trackInfo)
    assertEquals("Tracked branch is incorrect", "origin/$feature", trackInfo!!.remoteBranch.nameForLocalOperations)
  }

  private fun findAction(notification: Notification,
                         actionTitle: String) = notification.actions.find { it.templatePresentation.text == actionTitle }!!

  fun `test ok in unmerged branch dialog should force delete branch`() {
    prepareUnmergedBranch(last)
    deleteBranch("todelete", object : TestUiHandler(project) {
      override fun showBranchIsNotFullyMergedDialog(project: Project,
                                                    history: Map<GitRepository, List<GitCommit>>,
                                                    baseBranches: Map<GitRepository, String>,
                                                    removedBranch: String) = true
    })
    assertBranchDeleted("todelete")
  }

  fun `test rollback delete branch should recreate branches`() {
    prepare_delete_branch_failure_in_2nd_repo()

    var rollbackMsg: String? = null
    deleteBranch("todelete", object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        rollbackMsg = message
        return true
      }
    })

    assertNotNull("Rollback messages was not shown", rollbackMsg)
    assertBranchExists(last, "todelete")
    assertBranchExists(first, "todelete")
    assertBranchExists(second, "todelete")
  }

  fun `test deny rollback delete branch should do nothing`() {
    prepare_delete_branch_failure_in_2nd_repo()

    var rollbackMsg: String? = null
    deleteBranch("todelete", object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        rollbackMsg = message
        return false
      }
    })

    assertNotNull("Rollback messages was not shown", rollbackMsg)
    assertBranchDeleted(first, "todelete")
    assertBranchExists(second, "todelete")
    assertBranchExists(last, "todelete")
  }

  fun `test delete branch merged to head but unmerged to upstream should mention this in notification`() {
    // inspired by IDEA-83604
    // for the sake of simplicity we deal with a single myCommunity repository for remote operations
    val feature = "feature"
    prepareRemoteRepo(first)
    cd(first)
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
    val brancher = GitBranchWorker(project, git, object : TestUiHandler(project) {
      override fun showBranchIsNotFullyMergedDialog(project: Project,
                                                    history: Map<GitRepository, List<GitCommit>>,
                                                    baseBranches: Map<GitRepository, String>,
                                                    removedBranch: String): Boolean {
        dialogShown = true
        return false
      }
    })

    brancher.deleteBranch(feature, listOf(first))
    val notification = `assert successful deleted branch notification`(feature, true, RESTORE, VIEW_COMMITS, DELETE_TRACKED_BRANCH)
    val viewAction = findAction(notification, VIEW_COMMITS)
    assertFalse("'Branch is not fully merged' dialog shouldn't be shown yet", dialogShown)
    runInEdtAndWait { Notification.fire(notification, viewAction, null) }
    assertTrue("'Branch is not fully merged' dialog was not shown", dialogShown)
  }

  private fun prepare_delete_branch_failure_in_2nd_repo() {
    for (repository in myRepositories) {
      repository.git("branch todelete")
    }
    git.onBranchDelete {
      if (second == it) GitCommandResult.error("Couldn't remove branch")
      else null
    }
  }

  fun `test simple merge without problems`() {
    branchWithCommit(myRepositories, "master2", "branch_file.txt", "branch content")

    mergeBranch("master2", TestUiHandler(project))

    assertSuccessfulNotification("Merged ${code("master2")} to ${code("master")}", actions = listOf("Delete master2"))

    assertFile(last, "branch_file.txt", "branch content")
    assertFile(first, "branch_file.txt", "branch content")
    assertFile(second, "branch_file.txt", "branch content")
  }

  fun `test delete branch proposes to delete its tracked branch`() {
    prepareRemoteRepo(first)
    cd(first)

    val todelete = "todelete"
    git("branch $todelete")
    git("push -u origin todelete")

    first.deleteBranch(todelete)

    `assert successful deleted branch notification`(todelete, false, RESTORE, DELETE_TRACKED_BRANCH)
  }

  fun `test delete branch doesn't propose to delete tracked branch, if it is also tracked by another local branch`() {
    prepareRemoteRepo(first)
    cd(first)

    val todelete = "todelete"
    git("branch $todelete")
    git("push -u origin todelete")
    git("branch another origin/todelete")

    first.deleteBranch(todelete)

    `assert successful deleted branch notification`(todelete, false, RESTORE)
  }

  fun `test delete branch doesn't propose to delete protected tracked branch`() {
    prepareRemoteRepo(first)
    cd(first)

    val todelete = "todelete"
    git("branch $todelete")
    git("push -u origin todelete")

    GitSharedSettings.getInstance(project).forcePushProhibitedPatterns = listOf("todelete")

    first.deleteBranch(todelete)

    `assert successful deleted branch notification`(todelete, false, RESTORE)
  }

  fun `test merge branch that is up to date`() {
    for (repository in myRepositories) {
      repository.git("branch master2")
    }

    mergeBranch("master2", TestUiHandler(project))

    assertSuccessfulNotification("Already up to date", actions = listOf("Delete master2"))
  }

  fun `test merge one simple and other up to date`() {
    branchWithCommit(first, "master2", "branch_file.txt", "branch content")
    last.git("branch master2")
    second.git("branch master2")

    mergeBranch("master2", TestUiHandler(project))

    assertNotNull("Success message wasn't shown", vcsNotifier.lastNotification)

    assertSuccessfulNotification("Merged " + code("master2") + " to " + code("master"), actions = listOf("Delete master2"))
    assertFile(first, "branch_file.txt", "branch content")
  }

  fun `test merge branch with the same name as tag`() {
    prepareLocalAndRemoteBranch("master2", track = false)

    first.git("tag master2")

    checkoutBranch("master2", TestUiHandler(project))
    cd(first)
    touch("file.txt", "content")
    first.add("file.txt")
    first.commit("master2 commit")

    checkoutBranch("master", TestUiHandler(project))

    mergeBranch("master2", TestUiHandler(project))

    assertNotNull("Success message wasn't shown", vcsNotifier.lastNotification)
    assertSuccessfulNotification("Merged ${code("master2")} to ${code("master")}", actions = listOf("Delete master2"))
  }

  fun `test merge with unmerged files in first repo should show notification`() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(first)

    var notificationShown = false
    mergeBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesNotification(operationName: String,
                                                 repositories: Collection<GitRepository>) {
        notificationShown = true
      }
    })
    assertTrue("Unmerged files notification was not shown", notificationShown)
  }

  fun `test merge with unmerged files in second repo should propose to rollback`() {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(second)

    var rollbackProposed = false
    mergeBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed = true
        return false
      }
    })
    assertTrue("Rollback was not proposed if unmerged files prevented checkout in the second repository", rollbackProposed)
  }

  fun `test rollback merge should reset merge`() {
    branchWithCommit(myRepositories, "feature")
    val ultimateTip = tip(last)
    unmergedFiles(second)

    mergeBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return true
      }
    })

    assertEquals("Merge in ultimate should have been reset", ultimateTip, tip(last))
  }

  fun `test deny rollback merge should leave as is`() {
    branchWithCommit(myRepositories, "feature")
    cd(first)
    val firstTipAfterMerge = git("rev-list -1 feature")
    unmergedFiles(second)

    mergeBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return false
      }
    })

    assertEquals("Merge in community should have been reset", firstTipAfterMerge, tip(first))
  }

  fun `test checkout in detached head`() {
    cd(first)
    touch("file.txt", "some content")
    first.add("file.txt")
    first.commit("msg")
    first.git("checkout HEAD^")

    checkoutBranch("master", TestUiHandler(project))
    assertCurrentBranch("master")
  }

  // inspired by IDEA-127472
  fun `test checkout to common branch when branches have diverged`() {
    branchWithCommit(last, "feature", "feature-file.txt", "feature_content", false)
    branchWithCommit(first, "newbranch", "newbranch-file.txt", "newbranch_content", false)
    checkoutBranch("master", TestUiHandler(project))
    assertCurrentBranch("master")
  }

  fun `test rollback checkout from diverged branches should return to proper branches`() {
    branchWithCommit(last, "feature", "feature-file.txt", "feature_content", false)
    branchWithCommit(first, "newbranch", "newbranch-file.txt", "newbranch_content", false)
    unmergedFiles(second)

    checkoutBranch("master", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return true
      }
    })

    assertCurrentBranch(last, "feature")
    assertCurrentBranch(first, "newbranch")
    assertCurrentBranch(second, "master")
  }

  fun `test delete remote branch`() {
    prepareLocalAndRemoteBranch("feature", track = false)

    deleteRemoteBranch("origin/feature", DeleteRemoteBranchDecision.DELETE)

    assertSuccessfulNotification("Deleted remote branch origin/feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchExists(it, "feature") }
  }

  fun `test delete remote branch with the same name as remote tag`() {
    prepareLocalAndRemoteBranch("feature", track = false)

    git("tag feature")
    git("push origin refs/tags/feature")

    deleteRemoteBranch("origin/feature", DeleteRemoteBranchDecision.DELETE)

    assertSuccessfulNotification("Deleted remote branch origin/feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchExists(it, "feature") }
  }

  fun `test delete remote branch should optionally delete the tracking branch as well`() {
    prepareLocalAndRemoteBranch("feature", track = true)

    deleteRemoteBranch("origin/feature", DeleteRemoteBranchDecision.DELETE_WITH_TRACKING)

    assertSuccessfulNotification("Deleted remote branch origin/feature", "Also deleted local branch: feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchDeleted(it, "feature") }
  }

  fun `test delete remote branch when its tracking local branch is partially checked out`() {
    prepareLocalAndRemoteBranch("feature", track = true)
    last.git("checkout feature")

    GitBranchWorker(project, git, object : TestUiHandler(project) {
      override fun confirmRemoteBranchDeletion(branchNames: List<String>,
                                               trackingBranches: Collection<String>,
                                               repositories: Collection<GitRepository>): DeleteRemoteBranchDecision {
        assertEmpty("No tracking branches should be proposed for deletion", trackingBranches)
        return DeleteRemoteBranchDecision.DELETE
      }
    }).deleteRemoteBranch("origin/feature", myRepositories)


    assertSuccessfulNotification("Deleted remote branch origin/feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchExists(it, "feature") }
  }

  fun `test rename branch unset upstream should remove upstream`() {
    val oldBranchName = "old-name"
    val newBranchName = "new-name"

    prepareLocalAndRemoteBranch(oldBranchName, track = true)

    val brancher = GitBranchWorker(project, git, TestUiHandler(project))
    brancher.renameBranchAndUnsetUpstream(oldBranchName, newBranchName, myRepositories)

    myRepositories.forEach { repo ->
      assertBranchDeleted(repo, oldBranchName)
      assertBranchExists(repo, newBranchName)

      val newTrackInfo = getTrackInfo(repo, newBranchName)
      assertNull("Renamed branch should lose its upstream", newTrackInfo)
    }

    assertSuccessfulNotification("Branch ${bold(code(oldBranchName))} was renamed to ${bold(code(newBranchName))}")
  }

  fun `test rename branch should keep upstream`() {
    val oldBranchName = "old-name"
    val newBranchName = "new-name"

    prepareLocalAndRemoteBranch(oldBranchName, track = true)

    myRepositories.forEach { it.update() }
    val upstreamBranches = GitUpstreamBranches(myRepositories, oldBranchName, git)

    val brancher = GitBranchWorker(project, git, TestUiHandler(project))
    brancher.renameBranch(oldBranchName, newBranchName, myRepositories)

    myRepositories.forEach { repo ->
      assertBranchDeleted(repo, oldBranchName)
      assertBranchExists(repo, newBranchName)

      val newTrackInfo = getTrackInfo(repo, newBranchName)
      assertEquals("Renamed branch should keep its upstream", upstreamBranches.get()[repo], newTrackInfo?.remoteBranch)
    }

    assertSuccessfulNotification("Branch ${bold(code(oldBranchName))} was renamed to ${bold(code(newBranchName))}")
  }

  fun `test failed rename branch unset upstream can be rolled back`() {
    val oldBranchName = "old-name"
    val newBranchName = "new-name"

    prepareLocalAndRemoteBranch(oldBranchName, track = true)

    myRepositories.forEach { it.update() }
    val upstreamBranches = GitUpstreamBranches(myRepositories, oldBranchName, git)

    second.branch(newBranchName) // To fail rename

    val brancher = GitBranchWorker(project, git, object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        return true
      }
    })
    brancher.renameBranchAndUnsetUpstream(oldBranchName, newBranchName, myRepositories)

    myRepositories.forEach { repo ->
      assertBranchExists(repo, oldBranchName)

      val newTrackInfo = getTrackInfo(repo, oldBranchName)
      assertEquals("Rolled back branch should restore its upstream", upstreamBranches.get()[repo], newTrackInfo?.remoteBranch)
    }
  }

  private fun prepareLocalAndRemoteBranch(name: String, track: Boolean) {
    val parentRoot = testNioRoot.resolve("parentRoot")
    Files.createDirectories(parentRoot)
    for (repository in myRepositories) {
      repository.git("branch $name")
      prepareRemoteRepo(repository, parentRoot.resolve("${repository.root.name}-parent.git"))
      repository.git("push ${if (track) "-u" else ""} origin $name")
    }
  }

  private fun `assert remote branch deleted`(repository: GitRepository, name: String) {
    val branch = repository.branches.findBranchByName(name)
    if (branch != null) {
      assertNull("Branch $name should be deleted in $repository but was found in the repo info." +
                 "native git branch list: \n${git("branch --list --all")}", branch)

    }
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
    val brancher = GitBranchWorker(project, git, uiHandler)
    brancher.checkoutNewBranch(name, myRepositories)
  }

  private fun checkoutBranch(name: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(project, git, uiHandler)
    brancher.checkout(name, false, myRepositories)
  }

  private fun checkoutRevision(reference: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(project, git, uiHandler)
    brancher.checkout(reference, true, myRepositories)
  }

  private fun mergeBranch(name: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(project, git, uiHandler)
    brancher.merge(GitLocalBranch(name), GitBrancher.DeleteOnMergeOption.PROPOSE, myRepositories)
  }

  private fun deleteBranch(name: String, uiHandler: GitBranchUiHandler) {
    val brancher = GitBranchWorker(project, git, uiHandler)
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
      repository.git("branch todelete")
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

  private fun deleteRemoteBranch(branchName: String, decision: DeleteRemoteBranchDecision) {
    GitBranchWorker(project, git, object : TestUiHandler(project) {
      override fun confirmRemoteBranchDeletion(branchNames: List<String>,
                                               trackingBranches: Collection<String>,
                                               repositories: Collection<GitRepository>): DeleteRemoteBranchDecision {
        return decision
      }
    })
      .deleteRemoteBranch(branchName, myRepositories)
  }

  private fun GitRepository.deleteBranch(branchName: String) {
    GitBranchWorker(project, git, TestUiHandler(project)).deleteBranch(branchName, listOf(this))
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

  open class TestUiHandler(private val project: Project) : GitBranchUiHandler {
    override fun getProgressIndicator() = EmptyProgressIndicator()

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

    override fun notifyError(title: String, message: String) {
      VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.BRANCH_OPERATION_ERROR, title, message)
    }

    override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
      throw UnsupportedOperationException("$title\n$message\n$rollbackProposal")
    }

    override fun showUnmergedFilesNotification(operationName: String, repositories: Collection<GitRepository>) {
      throw UnsupportedOperationException("$operationName\n$repositories")
    }

    override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
      throw UnsupportedOperationException("$operationName\n$rollbackProposal")
    }

    override fun showUntrackedFilesNotification(operationName: String, root: VirtualFile, relativePaths: Collection<String>) {
      throw UnsupportedOperationException("$operationName $root\n$relativePaths")
    }

    override fun showUntrackedFilesDialogWithRollback(operationName: String,
                                                      rollbackProposal: String,
                                                      root: VirtualFile,
                                                      relativePaths: Collection<String>): Boolean {
      throw UnsupportedOperationException("$operationName\n$rollbackProposal\n$root\n$relativePaths")
    }

    override fun confirmRemoteBranchDeletion(branchNames: List<String>,
                                             trackingBranches: Collection<String>,
                                             repositories: Collection<GitRepository>): DeleteRemoteBranchDecision {
      throw UnsupportedOperationException("$branchNames\n$trackingBranches\n$repositories")
    }
  }

  private fun code(s: String): String {
    return "<code>$s</code>"
  }

  private fun bold(s: String): String {
    return "<b>$s</b>"
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
    assertEquals("Repository should be in the detached HEAD state", Repository.State.DETACHED, repository.state)
  }

  private fun assertCurrentBranch(repository: GitRepository, name: String) {
    assertEquals("Current branch is incorrect in ${repository}", name, repository.currentBranchName)
  }

  private fun assertCurrentRevision(repository: GitRepository, reference: String) {
    val expectedRef = repository.git("rev-parse HEAD")
    val currentRef = repository.git("rev-parse $reference")

    assertEquals("Current revision is incorrect in ${repository}", expectedRef, currentRef)
  }

  private fun assertBranchDeleted(repo: GitRepository, branch: String) {
    assertFalse("Branch $branch should have been deleted from $repo", repo.git("branch").contains(branch))
  }

  private fun assertBranchExists(repo: GitRepository, branch: String) {
    assertTrue("Branch $branch should exist in $repo", branchExists(repo, branch))
  }

  private fun assertFile(repository: GitRepository, path: String, content: String) {
    cd(repository)
    assertEquals("Content doesn't match", content, cat(path))
  }

  private fun assertContentIgnoreLineSeparators(expected: String, actual: String) {
    val systemSeparator = LineSeparator.getSystemLineSeparator().separatorString
    val actualContent = StringUtil.convertLineSeparators(actual, systemSeparator)
    val expectedContent = StringUtil.convertLineSeparators(expected, systemSeparator)
    assertEquals("Content is incorrect", expectedContent.trim(), actualContent.trim())
  }
}
