// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.repo.Repository
import com.intellij.notification.Notification
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor.cat
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.LineSeparator
import git4idea.GitCommit
import git4idea.GitLocalBranch
import git4idea.GitNotificationIdsHolder
import git4idea.branch.GitBranchUiHandler.DeleteRemoteBranchDecision
import git4idea.branch.GitBranchUtil.getTrackInfo
import git4idea.branch.GitBranchUtil.getTrackInfoForBranch
import git4idea.branch.GitDeleteBranchOperation.DELETE_TRACKED_BRANCH
import git4idea.branch.GitDeleteBranchOperation.RESTORE
import git4idea.branch.GitDeleteBranchOperation.VIEW_COMMITS
import git4idea.branch.GitSmartOperationDialog.Choice.CANCEL
import git4idea.branch.GitSmartOperationDialog.Choice.FORCE
import git4idea.branch.GitSmartOperationDialog.Choice.SMART
import git4idea.commands.GitCommandResult
import git4idea.config.GitSharedSettings
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.GitScenarios.LOCAL_CHANGES_OVERWRITTEN_BY
import git4idea.test.GitScenarios.branchExists
import git4idea.test.GitScenarios.branchWithCommit
import git4idea.test.GitScenarios.localChangesOverwrittenByWithoutConflict
import git4idea.test.GitScenarios.unmergedFiles
import git4idea.test.GitScenarios.untrackedFileOverwrittenBy
import git4idea.test.UNKNOWN_ERROR_TEXT
import git4idea.test.createRepository
import git4idea.test.createBroRepo
import git4idea.test.gitPlatformContextFixture
import git4idea.test.prepareRemoteRepo
import git4idea.test.add
import git4idea.test.assertCurrentBranch
import git4idea.test.assertCurrentRevision
import git4idea.test.branch
import git4idea.test.cd
import git4idea.test.commit
import git4idea.test.file
import git4idea.test.git
import git4idea.test.tac
import git4idea.ui.branch.updateBranches
import git4idea.workingTrees.ensureWorkingTreesUpToDateForTests
import com.intellij.vcs.test.assertErrorNotification
import com.intellij.vcs.test.assertNoNotification
import com.intellij.vcs.test.assertSuccessfulNotification
import com.intellij.vcs.test.updateChangeListManager
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private const val BRANCH_FILE_TXT = "branch_file.txt"
private const val BRANCH_FILE_CONTENT = "branch content"

@TestApplication
class GitBranchWorkerTest {
  private lateinit var first: GitRepository
  private lateinit var second: GitRepository
  private lateinit var last: GitRepository
  private lateinit var myRepositories: List<GitRepository>

  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()
  private val project get() = context.project
  private val git get() = context.git
  private val vcsNotifier get() = context.vcsNotifier

  @BeforeEach
  fun setUp(): Unit = with(context) {

    cd(projectRoot)
    val community = mkdir("community")
    val contrib = mkdir("contrib")

    first = createRepository(project, community.toString())
    second = createRepository(project, contrib.toString())
    last = createRepository(project, projectPath)
    myRepositories = listOf(first, second, last)

    cd(projectRoot)
    touch(".gitignore", "community\ncontrib")
    git("add .gitignore")
    git("commit -m gitignore")
    last.update()
  }

  @Test
  fun `test create new branch without problems`(): Unit = with(context) {
    GitBranchWorker(this@GitBranchWorkerTest.project, this@GitBranchWorkerTest.git, TestUiHandler(project)).checkoutNewBranch("feature",
                                                                                                                              myRepositories)

    assertCurrentBranch("feature")
    assertSuccessfulNotification("Branch ${code("feature")} was created")
  }

  @Test
  fun `test create new branch without checkout not at HEAD`(): Unit = with(context) {
    val hashMap = myRepositories.associateWith { it.currentRevision!! }
    myRepositories.forEach { cd(it); it.tac("f.txt") }

    GitBranchWorker(project, git, TestUiHandler(project)).createBranch("feature", myRepositories.associateWith { "HEAD^" })

    assertCurrentBranch("master")
    myRepositories.forEach {
      val branch = it.branches.findLocalBranch("feature")
      assertThat(branch).describedAs("Branch not created in $it").isNotNull()
      assertThat(it.branches.getHash(branch!!)!!.asString()).describedAs("Branch feature created at wrong point").isEqualTo(hashMap[it])
    }
    assertSuccessfulNotification("Branch ${code("feature")} was created")
  }

  @Test
  fun `test if create new branch fails with error in first repo, then notification should be shown`(): Unit = with(context) {
    git.onCheckoutNewBranch { if (it == first) GitCommandResult.error(UNKNOWN_ERROR_TEXT) else null }

    GitBranchWorker(this@GitBranchWorkerTest.project, this@GitBranchWorkerTest.git, TestUiHandler(project)).checkoutNewBranch("feature",
                                                                                                                              myRepositories)

    assertErrorNotification("Could not create new branch feature", "unknown error")
  }

  @Test
  fun `test if create new branch fails with error in second repo, then we should propose to rollback`(): Unit = with(context) {
    git.onCheckoutNewBranch { if (it == second) GitCommandResult.error(UNKNOWN_ERROR_TEXT) else null }

    var rollbackProposed = false
    GitBranchWorker(this@GitBranchWorkerTest.project, this@GitBranchWorkerTest.git, object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        rollbackProposed = true
        return false
      }
    }).checkoutNewBranch("feature", myRepositories)

    assertThat(rollbackProposed).describedAs("Rollback was not proposed if unmerged files prevented checkout in the second repository")
      .isTrue()
  }

  @Test
  fun `test rollback create new branch should delete branch`(): Unit = with(context) {
    git.onCheckoutNewBranch { if (it == second) GitCommandResult.error(UNKNOWN_ERROR_TEXT) else null }

    GitBranchWorker(this@GitBranchWorkerTest.project, this@GitBranchWorkerTest.git, object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        return true
      }
    }).checkoutNewBranch("feature", myRepositories)

    assertCurrentBranch("master")
    assertBranchDeleted(last, "feature")
  }

  @Test
  fun `test deny rollback create new branch should leave new branch`(): Unit = with(context) {
    git.onCheckoutNewBranch { if (it == second) GitCommandResult.error(UNKNOWN_ERROR_TEXT) else null }

    GitBranchWorker(this@GitBranchWorkerTest.project, this@GitBranchWorkerTest.git, object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        return false
      }
    }).checkoutNewBranch("feature", myRepositories)

    first.assertCurrentBranch("feature")
    second.assertCurrentBranch("master")
    last.assertCurrentBranch("master")
  }

  @Test
  fun `test checkout without problems`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")

    checkoutBranch("feature", TestUiHandler(project))

    assertCurrentBranch("feature")
    assertThat(vcsNotifier.lastNotification.content).describedAs("Notification about successful branch checkout is incorrect")
      .isEqualTo("Checked out " + code("feature"))
  }

  @Test
  fun `test checkout with unmerged files in first repo should show notification`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(first)

    var notificationShown = false
    checkoutBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesNotification(operationName: String, repositories: Collection<GitRepository>) {
        notificationShown = true
      }
    })

    assertThat(notificationShown).describedAs("Unmerged files notification was not shown").isTrue()
  }

  @Test
  fun `test checkout with unmerged file in second repo should propose to rollback`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(second)

    var rollbackProposed = false
    checkoutBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed = true
        return false
      }
    })

    assertThat(rollbackProposed).describedAs("Rollback was not proposed if unmerged files prevented checkout in the second repository")
      .isTrue()
  }

  @Test
  fun `test rollback checkout should return to previous branch`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(second)

    checkoutBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String) = true
    })

    assertCurrentBranch("master")
  }

  @Test
  fun `test deny rollback checkout should do nothing`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(second)

    checkoutBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String) = false
    })

    first.assertCurrentBranch("feature")
    second.assertCurrentBranch("master")
    last.assertCurrentBranch("master")
  }

  @Test
  fun `test checkout revision checkout branch with complete success`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("feature", TestUiHandler(project))

    assertDetachedState("feature")
    assertSuccessfulNotification("Checked out ${code("feature")}")
  }

  @Test
  fun `test checkout revision checkout ref with complete success`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("feature~1", TestUiHandler(project))

    assertDetachedState("master")
    assertSuccessfulNotification("Checked out ${code("feature~1")}")
  }

  @Test
  fun `test checkout revision checkout ref with complete failure`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")

    checkoutRevision("unknown_ref", TestUiHandler(project))

    assertCurrentBranch("master")
    for (repository in myRepositories) {
      repository.assertCurrentRevision("master")
    }
    assertErrorNotification("Could not checkout unknown_ref",
                            "Revision not found in community, contrib and ${project.stateStore.projectBasePath.fileName}")
  }

  @Test
  fun `test checkout revision checkout ref with partial success`(): Unit = with(context) {
    branchWithCommit(listOf(first, second), "feature")

    checkoutRevision("feature", TestUiHandler(project))

    last.assertCurrentBranch("master")
    assertDetachedState(first, "feature")
    assertDetachedState(second, "feature")

    assertSuccessfulNotification("Checked out ${code("feature")} in community and contrib<br/>" +
                                 "Revision not found in ${project.stateStore.projectBasePath.fileName}", actions = listOf("Rollback"))
  }

  @Test
  fun `test checkout with untracked files overwritten by checkout in first repo should show notification`() {
    `test untracked files overwritten by in first repo`("checkout", 1)
  }

  @Test
  fun `test checkout with several untracked files overwritten by checkout in first repo should show notification`() {
    // note that in old Git versions only one file is listed in the error.
    `test untracked files overwritten by in first repo`("checkout", 3)
  }

  @Test
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
      override fun showUntrackedFilesNotification(
        operationName: String,
        root: VirtualFile,
        relativePaths: Collection<String>,
      ) {
        notificationShown = true
      }
    })

    assertThat(notificationShown).describedAs("Untracked files notification was not shown").isTrue()
  }

  @Test
  fun `test checkout with untracked files overwritten by checkout in second repo should show rollback proposal with file list`() {
    `check checkout with untracked files overwritten by in second repo`("checkout")
  }

  @Test
  fun `test merge with untracked files overwritten by checkout in second repo should show rollback proposal with file list`() {
    `check checkout with untracked files overwritten by in second repo`("merge")
  }

  private fun `check checkout with untracked files overwritten by in second repo`(operation: String) {
    branchWithCommit(myRepositories, "feature")


    val untracked = listOf("untracked.txt")
    untrackedFileOverwrittenBy(second, "feature", untracked)

    val untrackedPaths = mutableListOf<String>()
    checkoutOrMerge(operation, "feature", object : TestUiHandler(project) {
      override fun showUntrackedFilesDialogWithRollback(
        operationName: String,
        rollbackProposal: String,
        root: VirtualFile,
        relativePaths: Collection<String>,
      ): Boolean {
        untrackedPaths.addAll(relativePaths)
        return false
      }
    })

    assertThat(untrackedPaths.isNotEmpty()).describedAs("Untracked files dialog was not shown").isTrue()
    assertThat(untrackedPaths).describedAs("Incorrect set of untracked files was shown in the dialog").isEqualTo(untracked)
  }

  @Test
  fun `test checkout with local changes overwritten by checkout should show smart checkout dialog`() {
    `check operation with local changes overwritten by should show smart checkout dialog`("checkout", 1)
  }

  @Test
  fun `test checkout with several local changes overwritten by checkout should show smart checkout dialog`() {
    `check operation with local changes overwritten by should show smart checkout dialog`("checkout", 3)
  }

  @Test
  fun `test merge with local changes overwritten by merge should show smart merge dialog`() {
    `check operation with local changes overwritten by should show smart checkout dialog`("merge", 1)
  }

  @Test
  fun `test merge with several local changes overwritten by merge should show smart merge dialog`() {
    `check operation with local changes overwritten by should show smart checkout dialog`("merge", 3)
  }

  /**
   * IJPL-200234
   * In this scenario the error output is the following:
   * Your local changes to the following files would be overwritten by merge:
   * <2 whitespaces>dir/other test
   */
  @Test
  fun `test merge with local changes printing paths in a single line`(): Unit = with(context) {
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
    assertThat(changedPaths).containsExactlyInAnyOrderElementsOf(setOf(fileName, anotherFileName))
  }

  // IJPL-173728
  @Test
  fun `test merge with trailing whitespace changes overwritten by checkout should show smart merge dialog`(): Unit = with(context) {
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
    assertThat(changedPaths).containsExactlyInAnyOrderElementsOf(setOf(fileName, anotherFileName))
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
          changedPaths.add(getAfterRevisionRelativePath(repo, it))
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
      override fun showSmartOperationDialog(
        project: Project,
        changes: List<Change>,
        paths: Collection<String>,
        operation: String,
        forceButton: String?,
      ): GitSmartOperationDialog.Choice {
        actualChanges.addAll(changes)
        return CANCEL
      }
    })

    assertThat(actualChanges.isEmpty()).describedAs("Local changes were not shown in the dialog").isFalse()
    if (newGitVersion()) {
      val actualPaths = actualChanges.map {
        getAfterRevisionRelativePath(repoWithLocalChangesProblem, it)
      }
      assertThat(actualPaths)
        .describedAs("Incorrect set of local changes was shown in the dialog")
        .containsExactlyInAnyOrderElementsOf(expectedChanges)
    }
  }

  private fun getAfterRevisionRelativePath(repo: GitRepository, change: Change): String =
    repo.root.toNioPath().relativize(Path.of(change.afterRevision!!.file.path)).invariantSeparatorsPathString

  @Test
  fun `test agree to smart checkout should smart checkout`(): Unit = with(context) {
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

  @Test
  fun `test agree to smart merge should smart merge`(): Unit = with(context) {
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

  private fun prepareLocalChangesOverwrittenBy(repository: GitRepository, numFiles: Int = 1): List<String> = with(context) {
    val localChanges = mutableListOf<String>()
    (0 until numFiles).mapTo(localChanges) { String.format("local%d.txt", it) }
    localChangesOverwrittenByWithoutConflict(repository, "feature", localChanges)
    updateChangeListManager()

    myRepositories
      .filter { it != repository }
      .forEach { branchWithCommit(it, "feature") }
    return localChanges
  }

  @Test
  fun `test deny to smart checkout in first repo should show nothing`() {
    `check deny to smart operation in first repo should show nothing`("checkout")
  }

  @Test
  fun `test deny to smart merge in first repo should show nothing`() {
    `check deny to smart operation in first repo should show nothing`("merge")
  }

  @Test
  fun `test local changes would be overwritten in several repositories`(): Unit = with(context) {
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
      override fun showSmartOperationDialog(
        project: Project,
        changes: List<Change>,
        paths: Collection<String>,
        operation: String,
        forceButton: String?,
      ): GitSmartOperationDialog.Choice {
        smartOperationDialogTimes++
        filesInDialog.addAll(ChangesUtil.getPaths(changes).map { it.path })
        return SMART
      }
    })

    assertThat(filesInDialog)
      .describedAs("Local changes would be overwritten by checkout are shown incorrectly")
      .containsExactlyInAnyOrderElementsOf(expectedLocalChanges)
    assertThat(smartOperationDialogTimes).describedAs("Smart checkout dialog should be shown only once").isEqualTo(1)
  }

  private fun `check deny to smart operation in first repo should show nothing`(operation: String) {
    prepareLocalChangesOverwrittenBy(first)

    checkoutOrMerge(operation, "feature", object : TestUiHandler(project) {
      override fun showSmartOperationDialog(
        project: Project,
        changes: List<Change>,
        paths: Collection<String>,
        operation: String,
        forceButton: String?,
      ) = CANCEL
    })

    assertThat(vcsNotifier.lastNotification).describedAs("Notification was unexpectedly shown:" + vcsNotifier.lastNotification).isNull()
    assertCurrentBranch("master")
  }

  @Test
  fun `test deny to smart checkout in second repo should show rollback proposal`() {
    `check deny to smart operation in second repo should show rollback proposal`("checkout")
    first.assertCurrentBranch("feature")
    second.assertCurrentBranch("master")
    last.assertCurrentBranch("master")
  }

  @Test
  fun `test deny to smart merge in second repo should show rollback proposal`() {
    `check deny to smart operation in second repo should show rollback proposal`("merge")
  }

  private fun `check deny to smart operation in second repo should show rollback proposal`(operation: String) {
    prepareLocalChangesOverwrittenBy(second)

    val rollbackMsg = Ref.create<String>()
    checkoutOrMerge(operation, "feature", object : TestUiHandler(project) {
      override fun showSmartOperationDialog(
        project: Project,
        changes: List<Change>,
        paths: Collection<String>,
        operation: String,
        forceButton: String?,
      ) = CANCEL

      override fun notifyErrorWithRollbackProposal(
        title: String,
        message: String,
        rollbackProposal: String,
      ): Boolean {
        rollbackMsg.set(message)
        return false
      }
    })

    assertThat(rollbackMsg.get()).describedAs("Rollback proposal was not shown").isNotNull()
  }

  @Test
  fun `test force checkout in case of local changes that would be overwritten by checkout`(): Unit = with(context) {
    // IDEA-99849
    prepareLocalChangesOverwrittenBy(last)

    val brancher = GitBranchWorker(project, git, object : TestUiHandler(project) {
      override fun showSmartOperationDialog(
        project: Project,
        changes: List<Change>,
        paths: Collection<String>,
        operation: String,
        forceButton: String?,
      ) = FORCE
    })
    brancher.checkoutNewBranchStartingFrom("new_branch", "feature", myRepositories)

    assertSuccessfulNotification("Checked out new branch <code>new_branch</code> from <code>feature</code>")
    assertCurrentBranch("new_branch")
  }

  @Test
  fun `test rollback of checkout branch as new branch should delete branches`(): Unit = with(context) {
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

    assertThat(rollbackProposed).describedAs("Rollback was not proposed if unmerged files prevented checkout in the second repository")
      .isTrue()
    assertCurrentBranch("master")
    myRepositories.forEach { assertBranchDeleted(it, "newBranch") }
  }

  @Test
  fun `test delete branch that is fully merged`(): Unit = with(context) {
    val todelete = "todelete"
    for (repository in myRepositories) {
      repository.git("branch $todelete")
    }

    GitBranchWorker(this@GitBranchWorkerTest.project, this@GitBranchWorkerTest.git, TestUiHandler(project)).deleteBranch(todelete,
                                                                                                                         myRepositories)

    `assert successful deleted branch notification`(todelete, false, RESTORE)
  }

  @Test
  fun `test delete unmerged branch should restore on link click`(): Unit = with(context) {
    prepareUnmergedBranch(first)

    first.deleteBranch("todelete")
    val notification = `assert successful deleted branch notification`("todelete", true, RESTORE, VIEW_COMMITS)
    val restoreAction = findAction(notification, RESTORE)

    vcsNotifier.cleanup()
    runInEdtAndWait { Notification.fire(notification, restoreAction, null) }
    assertBranchExists(first, "todelete")
    assertNoNotification()
  }

  @Test
  fun `test restore branch deletion should restore tracking`(): Unit = with(context) {
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
    assertThat(trackInfo).describedAs("Track info should be preserved").isNotNull()
    assertThat(trackInfo!!.remoteBranch.nameForLocalOperations).describedAs("Tracked branch is incorrect").isEqualTo("origin/$feature")
  }

  private fun findAction(
    notification: Notification,
    actionTitle: String,
  ) = notification.actions.find { it.templatePresentation.text == actionTitle }!!

  @Test
  fun `test ok in unmerged branch dialog should force delete branch`(): Unit = with(context) {
    prepareUnmergedBranch(last)
    GitBranchWorker(this@GitBranchWorkerTest.project, this@GitBranchWorkerTest.git, object : TestUiHandler(project) {
      override fun showBranchIsNotFullyMergedDialog(
        project: Project,
        history: Map<GitRepository, List<GitCommit>>,
        baseBranches: Map<GitRepository, String>,
        removedBranch: String,
      ) = true
    }).deleteBranch("todelete", myRepositories)
    for (repository in myRepositories) {
      assertBranchDeleted(repository, "todelete")
    }
  }

  @Test
  fun `test rollback delete branch should recreate branches`(): Unit = with(context) {
    prepare_delete_branch_failure_in_2nd_repo()

    var rollbackMsg: String? = null
    GitBranchWorker(this@GitBranchWorkerTest.project, this@GitBranchWorkerTest.git, object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        rollbackMsg = message
        return true
      }
    }).deleteBranch("todelete", myRepositories)

    assertThat(rollbackMsg).describedAs("Rollback messages was not shown").isNotNull()
    assertBranchExists(last, "todelete")
    assertBranchExists(first, "todelete")
    assertBranchExists(second, "todelete")
  }

  @Test
  fun `test deny rollback delete branch should do nothing`(): Unit = with(context) {
    prepare_delete_branch_failure_in_2nd_repo()

    var rollbackMsg: String? = null
    GitBranchWorker(this@GitBranchWorkerTest.project, this@GitBranchWorkerTest.git, object : TestUiHandler(project) {
      override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        rollbackMsg = message
        return false
      }
    }).deleteBranch("todelete", myRepositories)

    assertThat(rollbackMsg).describedAs("Rollback messages was not shown").isNotNull()
    assertBranchDeleted(first, "todelete")
    assertBranchExists(second, "todelete")
    assertBranchExists(last, "todelete")
  }

  @Test
  fun `test delete branch merged to head but unmerged to upstream should mention this in notification`(): Unit = with(context) {
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
      override fun showBranchIsNotFullyMergedDialog(
        project: Project,
        history: Map<GitRepository, List<GitCommit>>,
        baseBranches: Map<GitRepository, String>,
        removedBranch: String,
      ): Boolean {
        dialogShown = true
        return false
      }
    })

    brancher.deleteBranch(feature, listOf(first))
    val notification = `assert successful deleted branch notification`(feature, true, RESTORE, VIEW_COMMITS, DELETE_TRACKED_BRANCH)
    val viewAction = findAction(notification, VIEW_COMMITS)
    assertThat(dialogShown).describedAs("'Branch is not fully merged' dialog shouldn't be shown yet").isFalse()
    runInEdtAndWait { Notification.fire(notification, viewAction, null) }
    assertThat(dialogShown).describedAs("'Branch is not fully merged' dialog was not shown").isTrue()
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

  @Test
  fun `test simple merge without problems`(): Unit = with(context) {
    branchWithCommit(myRepositories, "master2", BRANCH_FILE_TXT, BRANCH_FILE_CONTENT)

    mergeBranch("master2", TestUiHandler(project))

    assertSuccessfulNotification("Merged ${code("master2")} to ${code("master")}", actions = listOf("Delete master2"))

    assertBranchFileContentCorrect(last)
    assertBranchFileContentCorrect(first)
    assertBranchFileContentCorrect(second)
  }

  @Test
  fun `test delete branch proposes to delete its tracked branch`(): Unit = with(context) {
    prepareRemoteRepo(first)
    cd(first)

    val todelete = "todelete"
    git("branch $todelete")
    git("push -u origin todelete")

    first.deleteBranch(todelete)

    `assert successful deleted branch notification`(todelete, false, RESTORE, DELETE_TRACKED_BRANCH)
  }

  @Test
  fun `test delete branch doesn't propose to delete tracked branch, if it is also tracked by another local branch`(): Unit = with(context) {
    prepareRemoteRepo(first)
    cd(first)

    val todelete = "todelete"
    git("branch $todelete")
    git("push -u origin todelete")
    git("branch another origin/todelete")

    first.deleteBranch(todelete)

    `assert successful deleted branch notification`(todelete, false, RESTORE)
  }

  @Test
  fun `test delete branch doesn't propose to delete protected tracked branch`(): Unit = with(context) {
    prepareRemoteRepo(first)
    cd(first)

    val todelete = "todelete"
    git("branch $todelete")
    git("push -u origin todelete")

    GitSharedSettings.getInstance(project).forcePushProhibitedPatterns = listOf("todelete")

    first.deleteBranch(todelete)

    `assert successful deleted branch notification`(todelete, false, RESTORE)
  }

  @Test
  fun `test merge branch that is up to date`(): Unit = with(context) {
    for (repository in myRepositories) {
      repository.git("branch master2")
    }

    mergeBranch("master2", TestUiHandler(project))

    assertSuccessfulNotification("Already up to date", actions = listOf("Delete master2"))
  }

  @Test
  fun `test merge one simple and other up to date`(): Unit = with(context) {
    branchWithCommit(first, "master2", BRANCH_FILE_TXT, BRANCH_FILE_CONTENT)
    last.git("branch master2")
    second.git("branch master2")

    mergeBranch("master2", TestUiHandler(project))

    assertThat(vcsNotifier.lastNotification).describedAs("Success message wasn't shown").isNotNull()

    assertSuccessfulNotification("Merged " + code("master2") + " to " + code("master"), actions = listOf("Delete master2"))
    assertBranchFileContentCorrect(first)
  }

  @Test
  fun `test merge branch with the same name as tag`(): Unit = with(context) {
    prepareLocalAndRemoteBranch("master2", track = false)

    first.git("tag master2")

    checkoutBranch("master2", TestUiHandler(project))
    cd(first)
    touch("file.txt", "content")
    first.add("file.txt")
    first.commit("master2 commit")

    checkoutBranch("master", TestUiHandler(project))

    mergeBranch("master2", TestUiHandler(project))

    assertThat(vcsNotifier.lastNotification).describedAs("Success message wasn't shown").isNotNull()
    assertSuccessfulNotification("Merged ${code("master2")} to ${code("master")}", actions = listOf("Delete master2"))
  }

  @Test
  fun `test merge with unmerged files in first repo should show notification`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(first)

    var notificationShown = false
    mergeBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesNotification(
        operationName: String,
        repositories: Collection<GitRepository>,
      ) {
        notificationShown = true
      }
    })
    assertThat(notificationShown).describedAs("Unmerged files notification was not shown").isTrue()
  }

  @Test
  fun `test merge with unmerged files in second repo should propose to rollback`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")
    unmergedFiles(second)

    var rollbackProposed = false
    mergeBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        rollbackProposed = true
        return false
      }
    })
    assertThat(rollbackProposed).describedAs("Rollback was not proposed if unmerged files prevented checkout in the second repository")
      .isTrue()
  }

  @Test
  fun `test rollback merge should reset merge`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")
    val ultimateTip = tip(last)
    unmergedFiles(second)

    mergeBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return true
      }
    })

    assertThat(tip(last)).describedAs("Merge in ultimate should have been reset").isEqualTo(ultimateTip)
  }

  @Test
  fun `test deny rollback merge should leave as is`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")
    cd(first)
    val firstTipAfterMerge = git("rev-list -1 feature")
    unmergedFiles(second)

    mergeBranch("feature", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return false
      }
    })

    assertThat(tip(first)).describedAs("Merge in community should have been reset").isEqualTo(firstTipAfterMerge)
  }

  @Test
  fun `test checkout branch already checked out in another worktree should confirm and retry`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")

    val worktreePath = FileUtil.toSystemIndependentName(testNioRoot.resolve("first-worktree").toString())
    first.git("worktree add $worktreePath feature")
    first.git("checkout master")

    var dialogShown = false
    checkoutBranch("feature", object : TestUiHandler(project) {
      override fun showCheckoutBranchInOtherWorktreeDialog(
        branchName: String,
        worktreePath: String?,
      ): GitBranchUiHandler.CheckoutInOtherWorktreeDecision {
        dialogShown = true
        return GitBranchUiHandler.CheckoutInOtherWorktreeDecision.CHECKOUT_ANYWAY
      }
    })

    assertThat(dialogShown).describedAs("Confirmation dialog for checking out a branch already checked out in another worktree was not shown")
      .isTrue()
    assertCurrentBranch("feature")
  }

  @Test
  fun `test cancel checkout branch already checked out in another worktree should show fatal error`(): Unit = with(context) {
    branchWithCommit(myRepositories, "feature")

    val worktreePath = FileUtil.toSystemIndependentName(testNioRoot.resolve("first-worktree").toString())
    first.git("worktree add $worktreePath feature")
    first.git("checkout master")

    checkoutBranch("feature", object : TestUiHandler(project) {
      override fun showCheckoutBranchInOtherWorktreeDialog(
        branchName: String,
        worktreePath: String?,
      ): GitBranchUiHandler.CheckoutInOtherWorktreeDecision =
        GitBranchUiHandler.CheckoutInOtherWorktreeDecision.CANCEL
    })

    first.assertCurrentBranch("master")
    assertThat(vcsNotifier.lastNotification).describedAs("Fatal error notification was not shown").isNotNull()
  }

  @Test
  fun `test update branch checked out in another worktree should fast-forward it there`(): Unit = with(context) {
    branchWithCommit(first, "feature")

    val parentRepo = prepareRemoteRepo(first)
    first.git("push -u origin feature")
    first.update()

    val worktreePath = testNioRoot.resolve("first-worktree")
    first.git("worktree add ${FileUtil.toSystemIndependentName(worktreePath.toString())} feature")
    first.ensureWorkingTreesUpToDateForTests()

    val broRepo = createBroRepo("bro", parentRepo)
    cd(broRepo)
    git("checkout feature")
    val newHead = tac("new_on_remote.txt")
    git("push")

    runBlocking {
      updateBranches(project, listOf(first), listOf("feature")).join()
    }

    cd(worktreePath)
    assertThat(git("rev-list -1 feature")).describedAs("Branch was not fast-forwarded in the other worktree").isEqualTo(newHead)
  }

  @Test
  fun `test update diverged branch checked out in another worktree should show error notification`(): Unit = with(context) {
    branchWithCommit(first, "feature")

    val parentRepo = prepareRemoteRepo(first)
    first.git("push -u origin feature")
    first.update()

    val worktreePath = testNioRoot.resolve("first-worktree")
    first.git("worktree add ${FileUtil.toSystemIndependentName(worktreePath.toString())} feature")
    first.ensureWorkingTreesUpToDateForTests()

    val broRepo = createBroRepo("bro", parentRepo)
    cd(broRepo)
    git("checkout feature")
    tac("new_on_remote.txt")
    git("push")

    cd(worktreePath)
    tac("diverged_locally.txt")

    runBlocking {
      updateBranches(project, listOf(first), listOf("feature")).join()
    }

    val notification = vcsNotifier.lastNotification
    assertThat(notification).describedAs("Error notification for a failed other-worktree update was not shown").isNotNull()
    val openWorktreeAction = notification!!.actions.find {
      it.templatePresentation.text == GitBundle.message("action.open.worktree.for.a.branch.text")
    }
    assertThat(openWorktreeAction).describedAs("'Open Worktree' action was not present on the failure notification").isNotNull()
  }

  @Test
  fun `test checkout in detached head`(): Unit = with(context) {
    cd(first)
    touch("file.txt", "some content")
    first.add("file.txt")
    first.commit("msg")
    first.git("checkout HEAD^")

    checkoutBranch("master", TestUiHandler(project))
    assertCurrentBranch("master")
  }

  // inspired by IDEA-127472
  @Test
  fun `test checkout to common branch when branches have diverged`(): Unit = with(context) {
    branchWithCommit(last, "feature", "feature-file.txt", "feature_content", false)
    branchWithCommit(first, "newbranch", "newbranch-file.txt", "newbranch_content", false)
    checkoutBranch("master", TestUiHandler(project))
    assertCurrentBranch("master")
  }

  @Test
  fun `test rollback checkout from diverged branches should return to proper branches`(): Unit = with(context) {
    branchWithCommit(last, "feature", "feature-file.txt", "feature_content", false)
    branchWithCommit(first, "newbranch", "newbranch-file.txt", "newbranch_content", false)
    unmergedFiles(second)

    checkoutBranch("master", object : TestUiHandler(project) {
      override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        return true
      }
    })

    last.assertCurrentBranch("feature")
    first.assertCurrentBranch("newbranch")
    second.assertCurrentBranch("master")
  }

  @Test
  fun `test delete remote branch`(): Unit = with(context) {
    prepareLocalAndRemoteBranch("feature", track = false)

    deleteRemoteBranch("origin/feature", DeleteRemoteBranchDecision.DELETE)

    assertSuccessfulNotification("Deleted remote branch origin/feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchExists(it, "feature") }
  }

  @Test
  fun `test delete remote branch with the same name as remote tag`(): Unit = with(context) {
    prepareLocalAndRemoteBranch("feature", track = false)

    git("tag feature")
    git("push origin refs/tags/feature")

    deleteRemoteBranch("origin/feature", DeleteRemoteBranchDecision.DELETE)

    assertSuccessfulNotification("Deleted remote branch origin/feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchExists(it, "feature") }
  }

  @Test
  fun `test delete remote branch should optionally delete the tracking branch as well`(): Unit = with(context) {
    prepareLocalAndRemoteBranch("feature", track = true)

    deleteRemoteBranch("origin/feature", DeleteRemoteBranchDecision.DELETE_WITH_TRACKING)

    assertSuccessfulNotification("Deleted remote branch origin/feature", "Also deleted local branch: feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchDeleted(it, "feature") }
  }

  @Test
  fun `test delete remote branch when its tracking local branch is partially checked out`(): Unit = with(context) {
    prepareLocalAndRemoteBranch("feature", track = true)
    last.git("checkout feature")

    GitBranchWorker(project, git, object : TestUiHandler(project) {
      override fun confirmRemoteBranchDeletion(
        branchNames: List<String>,
        trackingBranches: Collection<String>,
        repositories: Collection<GitRepository>,
      ): DeleteRemoteBranchDecision {
        assertThat(trackingBranches).describedAs("No tracking branches should be proposed for deletion").isEmpty()
        return DeleteRemoteBranchDecision.DELETE
      }
    }).deleteRemoteBranch("origin/feature", myRepositories)


    assertSuccessfulNotification("Deleted remote branch origin/feature")
    myRepositories.forEach { `assert remote branch deleted`(it, "origin/feature") }
    myRepositories.forEach { assertBranchExists(it, "feature") }
  }

  @Test
  fun `test rename branch unset upstream should remove upstream`(): Unit = with(context) {
    val oldBranchName = "old-name"
    val newBranchName = "new-name"

    prepareLocalAndRemoteBranch(oldBranchName, track = true)

    val brancher = GitBranchWorker(project, git, TestUiHandler(project))
    brancher.renameBranchAndUnsetUpstream(oldBranchName, newBranchName, myRepositories)

    myRepositories.forEach { repo ->
      assertBranchDeleted(repo, oldBranchName)
      assertBranchExists(repo, newBranchName)

      val newTrackInfo = getTrackInfo(repo, newBranchName)
      assertThat(newTrackInfo).describedAs("Renamed branch should lose its upstream").isNull()
    }

    assertSuccessfulNotification("Branch ${bold(code(oldBranchName))} was renamed to ${bold(code(newBranchName))}")
  }

  @Test
  fun `test rename branch should keep upstream`(): Unit = with(context) {
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
      assertThat(newTrackInfo?.remoteBranch).describedAs("Renamed branch should keep its upstream").isEqualTo(upstreamBranches.get()[repo])
    }

    assertSuccessfulNotification("Branch ${bold(code(oldBranchName))} was renamed to ${bold(code(newBranchName))}")
  }

  @Test
  fun `test failed rename branch unset upstream can be rolled back`(): Unit = with(context) {
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
      assertThat(newTrackInfo?.remoteBranch).describedAs("Rolled back branch should restore its upstream")
        .isEqualTo(upstreamBranches.get()[repo])
    }
  }

  private fun prepareLocalAndRemoteBranch(name: String, track: Boolean): Unit = with(context) {
    val parentRoot = testNioRoot.resolve("parentRoot")
    Files.createDirectories(parentRoot)
    for (repository in myRepositories) {
      repository.git("branch $name")
      prepareRemoteRepo(project, testNioRoot, repository, parentRoot.resolve("${repository.root.name}-parent.git"))
      repository.git("push ${if (track) "-u" else ""} origin $name")
    }
  }

  private fun `assert remote branch deleted`(repository: GitRepository, name: String): Unit = with(context) {
    val branch = repository.branches.findBranchByName(name)
    if (branch != null) {
      assertThat(branch).describedAs("Branch $name should be deleted in $repository but was found in the repo info." +
                                     "native git branch list: \n${git("branch --list --all")}").isNull()

    }
  }

  private fun assertDetachedState(reference: String) {
    for (repository in myRepositories) {
      assertDetachedState(repository, reference)
    }
  }

  private fun assertCurrentBranch(name: String) {
    for (repository in myRepositories) {
      repository.assertCurrentBranch(name)
    }
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

  private fun checkoutOrMerge(operation: String, name: String, uiHandler: GitBranchUiHandler) {
    if (operation == "checkout") {
      checkoutBranch(name, uiHandler)
    }
    else {
      mergeBranch(name, uiHandler)
    }
  }

  private fun prepareUnmergedBranch(unmergedRepo: GitRepository): Unit = with(context) {
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
                                                              vararg actions: String): Notification = with(context) {
    val title = """<b>Deleted Branch:</b> $branchName"""
    val warning = if (unmergedWarning) "<br/>Unmerged commits were discarded" else ""
    val notification = assertSuccessfulNotification("$title$warning")
    assertThat(notification.actions.map { it.templatePresentation.text }).describedAs("Notification actions are incorrect")
      .containsExactly(*actions)
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

    override fun showCheckoutBranchInOtherWorktreeDialog(branchName: String, worktreePath: String?): GitBranchUiHandler.CheckoutInOtherWorktreeDecision {
      throw UnsupportedOperationException("$branchName\n$worktreePath")
    }
  }

  private fun code(s: String): String {
    return "<code>$s</code>"
  }

  private fun bold(s: String): String {
    return "<b>$s</b>"
  }

  private fun newGitVersion(): Boolean = with(context) {
    return !GitVersionSpecialty.OLD_STYLE_OF_UNTRACKED_AND_LOCAL_CHANGES_WOULD_BE_OVERWRITTEN.existsIn(GitVersion.parse(git("version")))
  }

  private fun tip(repo: GitRepository): String = with(context) {
    cd(repo)
    return git("rev-list -1 HEAD")
  }

  private fun assertDetachedState(repository: GitRepository, reference: String) {
    repository.assertCurrentRevision(reference)
    assertThat(repository.state).describedAs("Repository should be in the detached HEAD state").isEqualTo(Repository.State.DETACHED)
  }

  private fun assertBranchDeleted(repo: GitRepository, branch: String) {
    assertThat(repo.git("branch").contains(branch)).describedAs("Branch $branch should have been deleted from $repo").isFalse()
  }

  private fun assertBranchExists(repo: GitRepository, branch: String) {
    assertThat(branchExists(repo, branch)).describedAs("Branch $branch should exist in $repo").isTrue()
  }

  private fun assertBranchFileContentCorrect(repository: GitRepository) {
    cd(repository)
    assertThat(cat(BRANCH_FILE_TXT))
      .describedAs("Branch content doesn't match in repository ${repository.root}")
      .isEqualTo(BRANCH_FILE_CONTENT)
  }

  private fun assertContentIgnoreLineSeparators(expected: String, actual: String) {
    val systemSeparator = LineSeparator.getSystemLineSeparator().separatorString
    val actualContent = StringUtil.convertLineSeparators(actual, systemSeparator)
    val expectedContent = StringUtil.convertLineSeparators(expected, systemSeparator)
    assertThat(actualContent.trim()).describedAs("Content is incorrect").isEqualTo(expectedContent.trim())
  }
}
