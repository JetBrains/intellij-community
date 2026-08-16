// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.junit5.EnableTracingFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.util.LineSeparator
import com.intellij.vcs.test.assertErrorNotification
import com.intellij.vcs.test.assertNoErrorNotification
import com.intellij.vcs.test.assertSuccessfulNotification
import com.intellij.vcs.test.updateChangeListManager
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitBranch
import git4idea.branch.GitBranchUiHandler
import git4idea.branch.GitBranchWorker
import git4idea.branch.GitRebaseParams
import git4idea.branch.GitRebaseParams.RebaseUpstream
import git4idea.config.GitSaveChangesPolicy
import git4idea.config.GitVersionSpecialty
import git4idea.i18n.GitBundle
import git4idea.rebase.interactive.dialog.GitInteractiveRebaseDialog
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.RepoBuilder
import git4idea.test.TestGitImpl
import git4idea.test.UNKNOWN_ERROR_TEXT
import git4idea.test.addCommit
import git4idea.test.assertLastMessage
import git4idea.test.build
import git4idea.test.commit
import git4idea.test.createRepository
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.makeCommit
import git4idea.test.resolveConflicts
import git4idea.test.runUnderProgress
import git4idea.test.withPartialTracker
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.File

@TestApplication
@EnableTracingFor(categories = ["#git4idea.rebase"])
internal class GitSingleRepoRebaseTest {
  private val saveChangesPolicy = GitSaveChangesPolicy.SHELVE
  private val contextFixture = gitPlatformContextFixture(saveChangesPolicy = saveChangesPolicy)
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private val disposableFixture = disposableFixture()

  private lateinit var repo: GitRepository

  @BeforeEach
  fun setUp() {
    with(context) {
      repo = createRepository(project, projectNioRoot, false)
      repo.hideIdeaProjectFilesFromGit()
      // as long as a directory's children have never been loaded,
      // loading them later populates the children list without producing VFileCreateEvents
      repo.root.children
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(testNioRoot)?.children
    }
  }

  @Test
  fun `test simple case`(): Unit = with(context) {
    repo.`diverge feature and master`()

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  @Test
  fun `test up-to-date`(): Unit = with(context) {
    repo.`place feature above master`()

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  @Test
  fun test_ff(): Unit = with(context) {
    repo.`place feature below master`()

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  @Test
  fun `test conflict resolver is shown`(): Unit = with(context) {
    repo.`prepare simple conflict`()
    vcsHelper.onMerge {}

    ensureUpToDateAndRebaseOnMaster()

    assertThat(vcsHelper.mergeDialogWasShown()).describedAs("Merge dialog was not shown").isTrue()
  }

  @Test
  fun `test fail on 2nd commit should show notification with proposal to abort`(): Unit = with(context) {
    `make rebase fail on 2nd commit`(repo)

    ensureUpToDateAndRebaseOnMaster()

    `assert unknown error notification with link to abort`()
  }

  @Test
  fun `test multiple conflicts`(): Unit = with(context) {
    build {
      master {
        0("c.txt")
        1("c.txt")
      }
      feature(0) {
        2("c.txt")
        3("c.txt")
      }
    }

    var conflicts = 0
    vcsHelper.onMerge {
      conflicts++
      repo.assertConflict("c.txt")
      repo.resolveConflicts()
    }
    keepCommitMessageAfterConflict()

    ensureUpToDateAndRebaseOnMaster()

    assertThat(conflicts).describedAs("Incorrect number of conflicting patches").isEqualTo(2)
    repo.`assert feature rebased on master`()
    assertSuccessfulRebaseNotification("Rebased feature on master")
  }

  @Test
  fun `test continue rebase after resolving all conflicts`(): Unit = with(context) {
    repo.`prepare simple conflict`()

    vcsHelper.onMerge {
      repo.resolveConflicts()
    }
    keepCommitMessageAfterConflict()

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  @Test
  fun `test warning notification if conflicts were not resolved`(): Unit = with(context) {
    repo.`prepare simple conflict`()
    vcsHelper.onMerge {}

    ensureUpToDateAndRebaseOnMaster()

    `assert conflict not resolved notification`()
    repo.assertRebaseInProgress()
  }

  @Test
  fun `test skip if user decides to skip`(): Unit = with(context) {
    repo.`prepare simple conflict`()
    vcsHelper.onMerge {}

    ensureUpToDateAndRebaseOnMaster()

    GitRebaseUtils.skipRebase(project)

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  @Test
  fun `test rebase failed for unknown reason`(): Unit = with(context) {
    repo.`diverge feature and master`()
    git.setShouldRebaseFail { true }

    ensureUpToDateAndRebaseOnMaster()

    `assert unknown error notification`()
  }

  @Test
  fun `test propose to abort when rebase failed after continue`(): Unit = with(context) {
    repo.`prepare simple conflict`()
    vcsHelper.onMerge {}

    ensureUpToDateAndRebaseOnMaster()

    repo.`assert feature not rebased on master`()
    repo.assertRebaseInProgress()
    repo.resolveConflicts()

    git.setShouldRebaseFail { true }

    GitRebaseUtils.continueRebase(project)

    `assert unknown error notification with link to abort`(true)
    repo.`assert feature not rebased on master`()
    repo.assertRebaseInProgress()
  }

  @Test
  fun `test local changes auto-saved initially`(): Unit = with(context) {
    repo.`diverge feature and master`()
    val localChange = LocalChange(repo, "new.txt").generate()

    refresh()
    updateChangeListManager()

    object : GitTestingRebaseProcess(project, simpleParams("master"), repo) {
      override fun getDirtyRoots(repositories: Collection<GitRepository>): Collection<GitRepository> {
        return listOf(repo)
      }
    }.rebase()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    assertRebased(repo, "feature", "master")
    assertNoRebaseInProgress(repo)
    localChange.verify()
  }

  @Test
  fun `test local changes are saved even if not detected initially`(): Unit = with(context) {
    repo.`diverge feature and master`()
    val localChange = LocalChange(repo, "new.txt").generate()

    refresh()
    updateChangeListManager()

    object : GitTestingRebaseProcess(project, simpleParams("master"), repo) {
      override fun getDirtyRoots(repositories: Collection<GitRepository>): Collection<GitRepository> {
        return emptyList()
      }
    }.rebase()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    assertRebased(repo, "feature", "master")
    assertNoRebaseInProgress(repo)
    localChange.verify()
  }

  @Test
  fun `test local changes are not restored in case of error even if nothing was rebased`(): Unit = with(context) {
    repo.`diverge feature and master`()
    LocalChange(repo, "new.txt", "content").generate()

    git.setShouldRebaseFail { true }

    ensureUpToDateAndRebaseOnMaster()

    assertErrorNotification("Rebase failed",
                            """
                            $UNKNOWN_ERROR_TEXT<br/>
                            ${localChangesWarning(saveChangesPolicy)}
                            """)
    assertNoRebaseInProgress(repo)
    repo.assertNoLocalChanges()
    assertThat(file("new.txt").exists()).isFalse()
  }

  @Test
  fun `test critical error should show notification and not restore local changes`(): Unit = with(context) {
    repo.`diverge feature and master`()
    LocalChange(repo, "new.txt", "content").generate()
    git.setShouldRebaseFail { true }

    ensureUpToDateAndRebaseOnMaster()

    `assert unknown error notification with link to stash`(saveChangesPolicy)
    repo.assertNoLocalChanges()
  }

  @Test
  fun `test successful retry from notification on critical error restores local changes`(): Unit = with(context) {
    repo.`diverge feature and master`()
    val localChange = LocalChange(repo, "new.txt", "content").generate()

    var attempt = 0
    git.setShouldRebaseFail { attempt == 0 }

    ensureUpToDateAndRebaseOnMaster()

    attempt++
    vcsNotifier.lastNotification

    GitRebaseUtils.continueRebase(project)

    assertNoRebaseInProgress(repo)
    repo.`assert feature rebased on master`()
    localChange.verify()
  }

  @Test
  fun `test local changes are restored after successful abort`(): Unit = with(context) {
    repo.`prepare simple conflict`()
    val localChange = LocalChange(repo, "new.txt", "content").generate()
    vcsHelper.onMerge {}
    dialogManager.onMessage { Messages.YES }

    ensureUpToDateAndRebaseOnMaster()

    `assert conflict not resolved notification with link to stash`(saveChangesPolicy)

    GitRebaseUtils.abort(project, EmptyProgressIndicator())

    assertNoRebaseInProgress(repo)
    repo.`assert feature not rebased on master`()
    localChange.verify()
  }

  @Test
  fun `test local changelists are restored after successful abort`(): Unit = with(context) {
    TestLoggerFactory.enableDebugLogging(disposableFixture.get(),
                                         com.intellij.openapi.vcs.impl.LineStatusTrackerManager::class.java,
                                         com.intellij.openapi.vcs.changes.ChangeListWorker::class.java)

    touch("file.txt", "1\n2\n3\n4\n5\n")
    touch("file1.txt", "content")
    touch("file2.txt", "content")
    touch("file3.txt", "content")
    repo.addCommit("initial")

    repo.`prepare simple conflict`()

    val testChangelist1 = changeListManager.addChangeList("TEST_1", null)
    val testChangelist2 = changeListManager.addChangeList("TEST_2", null)

    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(child("file.txt"))!!
    withPartialTracker(file, "1A\n2\n3A\n4\n5A\n") { _, tracker ->
      val ranges = tracker.getRanges()!!
      assertThat(ranges).hasSize(3)
      tracker.moveToChangelist(ranges[1], testChangelist1)
      tracker.moveToChangelist(ranges[2], testChangelist2)
    }

    overwrite("file1.txt", "new content")
    overwrite("file2.txt", "new content")
    overwrite("file3.txt", "new content")
    VfsUtil.markDirtyAndRefresh(false, false, true, repo.root)
    changeListManager.ensureUpToDate()

    moveFileChangeToChangelist("file2.txt", testChangelist1)
    moveFileChangeToChangelist("file3.txt", testChangelist2)

    vcsHelper.onMerge {}
    dialogManager.onMessage { message ->
      assertThat(message).contains("Abort rebase in")
      Messages.YES
    }

    ensureUpToDateAndRebaseOnMaster()

    `assert conflict not resolved notification with link to stash`(saveChangesPolicy)

    GitRebaseUtils.abort(project, EmptyProgressIndicator())

    updateChangeListManager()

    assertNoRebaseInProgress(repo)
    repo.`assert feature not rebased on master`()
    assertSuccessfulNotification("Abort rebase succeeded")

    val changelists = changeListManager.changeLists
    assertThat(changelists).hasSize(3)

    val errorMessage = changelists.joinToString(separator = "\n") { "${it.name} - ${it.changes}" }
    for (changeList in changelists) {
      assertThat(changeList.changes).describedAs(errorMessage).hasSize(2)
    }
  }

  @Test
  fun `test local changelists are restored after successful rebase`(): Unit = with(context) {
    touch("file.txt", "1\n2\n3\n4\n5\n")
    touch("file1.txt", "content")
    touch("file2.txt", "content")
    touch("file3.txt", "content")
    repo.addCommit("initial")

    repo.`diverge feature and master`()

    val testChangelist1 = changeListManager.addChangeList("TEST_1", null)
    val testChangelist2 = changeListManager.addChangeList("TEST_2", null)

    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(child("file.txt"))!!
    withPartialTracker(file, "1A\n2\n3A\n4\n5A\n") { _, tracker ->
      val ranges = tracker.getRanges()!!
      assertThat(ranges).hasSize(3)
      tracker.moveToChangelist(ranges[1], testChangelist1)
      tracker.moveToChangelist(ranges[2], testChangelist2)
    }

    overwrite("file1.txt", "new content")
    overwrite("file2.txt", "new content")
    overwrite("file3.txt", "new content")
    VfsUtil.markDirtyAndRefresh(false, false, true, repo.root)
    changeListManager.ensureUpToDate()
    moveFileChangeToChangelist("file2.txt", testChangelist1)
    moveFileChangeToChangelist("file3.txt", testChangelist2)

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    assertNoRebaseInProgress(repo)
    repo.`assert feature rebased on master`()

    val changelists = changeListManager.changeLists
    assertThat(changelists).hasSize(3)
    for (changeList in changelists) {
      assertThat(changeList.changes).describedAs("${changeList.name} - ${changeList.changes}").hasSize(2)
    }
  }

  @Test
  fun `test local changelists are restored after successful rebase with resolved conflict`(): Unit = with(context) {
    touch("file.txt", "1\n2\n3\n4\n5\n")
    touch("file1.txt", "content")
    touch("file2.txt", "content")
    touch("file3.txt", "content")
    repo.addCommit("initial")

    repo.`prepare simple conflict`()

    val testChangelist1 = changeListManager.addChangeList("TEST_1", null)
    val testChangelist2 = changeListManager.addChangeList("TEST_2", null)

    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(child("file.txt"))!!
    withPartialTracker(file, "1A\n2\n3A\n4\n5A\n") { _, tracker ->
      val ranges = tracker.getRanges()!!
      assertThat(ranges).hasSize(3)
      tracker.moveToChangelist(ranges[1], testChangelist1)
      tracker.moveToChangelist(ranges[2], testChangelist2)
    }

    overwrite("file1.txt", "new content")
    overwrite("file2.txt", "new content")
    overwrite("file3.txt", "new content")
    VfsUtil.markDirtyAndRefresh(false, false, true, repo.root)
    changeListManager.ensureUpToDate()
    moveFileChangeToChangelist("file2.txt", testChangelist1)
    moveFileChangeToChangelist("file3.txt", testChangelist2)

    vcsHelper.onMerge {
      repo.resolveConflicts()
    }
    keepCommitMessageAfterConflict()

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)

    val changelists = changeListManager.changeLists
    assertThat(changelists).hasSize(3)
    for (changeList in changelists) {
      assertThat(changeList.changes).describedAs("${changeList.name} - ${changeList.changes}").hasSize(2)
    }
  }

  @Test
  fun `test local changes are not restored after failed abort`(): Unit = with(context) {
    repo.`prepare simple conflict`()
    LocalChange(repo, "new.txt", "content").generate()
    vcsHelper.onMerge {}
    dialogManager.onMessage { Messages.YES }

    ensureUpToDateAndRebaseOnMaster()

    `assert conflict not resolved notification with link to stash`(saveChangesPolicy)

    git.setShouldRebaseFail { true }
    GitRebaseUtils.abort(project, EmptyProgressIndicator())

    repo.assertRebaseInProgress()
    repo.`assert feature not rebased on master`()
    repo.assertConflict("c.txt")
    assertErrorNotification(GitBundle.message("rebase.abort.notification.failed.title"),
                            """
                            unknown error<br/>
                            ${localChangesWarning(saveChangesPolicy)}
                            """)
  }

  // git rebase --continue should be either called from a commit dialog, either from the GitRebaseProcess.
  // both should prepare the working tree themselves by adding all necessary changes to the index.
  @Test
  fun `test local changes in the conflicting file should not prevent rebase`(): Unit = with(context) {
    repo.`prepare simple conflict`()
    vcsHelper.onMerge {}

    ensureUpToDateAndRebaseOnMaster()
    repo.assertConflict("c.txt")

    //manually resolve conflicts
    repo.resolveConflicts()
    val appended = "more changes after resolving"
    file("c.txt").append(appended)
    // forget to git add afterwards

    refresh()
    updateChangeListManager()

    git.setInteractiveRebaseEditor(TestGitImpl.InteractiveRebaseEditor(null) { "message!" })
    GitRebaseUtils.continueRebase(project)

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)

    repo.assertNoLocalChanges()
    assertThat(file("c.txt").read()).endsWith(appended)
  }

  @Test
  fun `test local changes in some other file should lead to error on continue rebase`(): Unit = with(context) {
    build {
      master {
        0("d.txt")
        1("c.txt")
        2("c.txt")
      }
      feature(1) {
        3("c.txt")
      }
    }

    vcsHelper.onMerge {}

    ensureUpToDateAndRebaseOnMaster()
    repo.assertConflict("c.txt")

    //manually resolve conflicts
    repo.resolveConflicts()
    // add more changes to some other file

    file("d.txt").append("more changes after resolving")

    GitRebaseUtils.continueRebase(project)

    `assert error about unstaged file before continue rebase`()
    repo.assertRebaseInProgress()
  }

  @Test
  fun `test unstaged changes while stopped for editing stage and retry`(): Unit = with(context) {
    val fileA = file("a.txt")
    val fileB = file("b.txt")
    build {
      master {
        0()
        1()
      }
      feature(1) {
        fileA.write("hello").add()
        fileB.write("hello").add()
        repo.commit("feature")
      }
    }

    git.setInteractiveRebaseEditor(TestGitImpl.InteractiveRebaseEditor(
      { it.lines().joinToString(LineSeparator.getSystemLineSeparator().separatorString) { s -> s.replace("pick", "edit") } },
      { "message" }))

    refresh()
    updateChangeListManager()
    rebaseInteractively()

    assertSuccessfulNotification("Rebase stopped for editing", "")
    val editedContent = "more changes after resolving"
    fileA.append(editedContent)
    fileB.delete()
    refresh()
    updateChangeListManager()

    runBlocking {
      GitRebaseStagingAreaHelper.tryStageChangesInTrackedFilesAndRetryInBackground(repo) { fail("Error shouln't be shown") }.join()
    }

    assertNoRebaseInProgress(repo)

    repo.assertNoLocalChanges()
    assertThat(fileA.read()).endsWith(editedContent)
    assertThat(fileB.exists()).isFalse()
  }

  @Test
  fun `test unresolved conflict should lead to conflict resolver with continue rebase`(): Unit = with(context) {
    repo.`prepare simple conflict`()
    vcsHelper.onMerge {}
    keepCommitMessageAfterConflict()

    ensureUpToDateAndRebaseOnMaster()
    repo.assertConflict("c.txt")

    vcsHelper.onMerge {
      repo.resolveConflicts()
    }
    GitRebaseUtils.continueRebase(project)

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  @Test
  fun `test skipped commit`(): Unit = with(context) {
    build {
      master {
        0("c.txt", "base")
        1("c.txt", "\nmaster")
      }
      feature(0) {
        2("c.txt", "feature", "commit to be skipped")
        3()
      }
    }

    vcsHelper.onMerge {
      file("c.txt").write("base\nmaster")
      repo.resolveConflicts()
    }

    ensureUpToDateAndRebaseOnMaster()

    assertRebased(repo, "feature", "master")
    assertNoRebaseInProgress(repo)

    assertSuccessfulRebaseNotification("Rebased feature on master")
  }

  @Test
  fun `test interactive rebase stopped for editing with continue`(): Unit = with(context) {
    build {
      master {
        0()
        1()
      }
      feature(1) {
        2()
        3()
      }
    }

    git.setInteractiveRebaseEditor(TestGitImpl.InteractiveRebaseEditor({
      it.lines().mapIndexed { i, s ->
        if (i != 0) s
        else s.replace("pick", "edit")
      }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
    }, null))

    refresh()
    updateChangeListManager()

    rebaseInteractively()

    assertSuccessfulNotification("Rebase stopped for editing", "")
    val successfulNotification = vcsNotifier.lastNotification
    assertThat(repositoryManager.ongoingRebaseSpec!!.ongoingRebase)
      .describedAs("The repository must be in the 'SUSPENDED' state")
      .isEqualTo(repo)

    GitRebaseUtils.continueRebase(project)
    assertSuccessfulRebaseNotification("Rebased feature on master")

    // IJPL-73963
    assertThat(successfulNotification.isExpired).isTrue()

    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  @Test
  fun `test interactive rebase stopped for editing with abort`(): Unit = with(context) {
    build {
      master {
        0()
        1()
      }
      feature(1) {
        2()
        3()
      }
    }

    git.setInteractiveRebaseEditor(TestGitImpl.InteractiveRebaseEditor({
      it.lines().mapIndexed { i, s ->
        if (i != 0) s
        else s.replace("pick", "edit")
      }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
    }, null))

    refresh()
    updateChangeListManager()

    rebaseInteractively()

    assertSuccessfulNotification("Rebase stopped for editing", "")
    val successfulNotification = vcsNotifier.lastNotification
    assertThat(repositoryManager.ongoingRebaseSpec!!.ongoingRebase)
      .describedAs("The repository must be in the 'SUSPENDED' state")
      .isEqualTo(repo)
    dialogManager.onMessage { Messages.YES }
    GitRebaseUtils.abort(project, EmptyProgressIndicator())
    assertSuccessfulNotification("Abort rebase succeeded")

    // IJPL-73963
    assertThat(successfulNotification.isExpired).isTrue()

    assertNoRebaseInProgress(repo)
  }

  // IDEA-140568
  @Test
  fun `test git help comments are ignored when parsing interactive rebase`(): Unit = with(context) {
    makeCommit("initial.txt")
    repo.update()
    val initialMessage = "Wrong message"
    val commit = file("a").create("initial").addCommit(initialMessage).details()

    git("config core.commentChar $")

    var receivedEntries: List<GitRebaseEntry>? = null
    val rebaseEditor = GitAutomaticRebaseEditor(project, commit.root,
                                                entriesEditor = { list ->
                                                  receivedEntries = list
                                                  list
                                                },
                                                plainTextEditor = { it })

    refresh()
    updateChangeListManager()
    GitTestingRebaseProcess(project, GitRebaseParams.editCommits(vcs.version, "HEAD^", rebaseEditor, false), repo).rebase()

    assertThat(receivedEntries).describedAs("Didn't get any rebase entries").isNotNull()
    assertThat(receivedEntries!!.map { it.action })
      .describedAs("Rebase entries parsed incorrectly")
      .isEqualTo(listOf(GitRebaseEntry.Action.PICK))
  }

  // IDEA-176455
  @Test
  fun `test reword during interactive rebase writes commit message correctly`(): Unit = with(context) {
    // IDEA-182044
    assumeTrue(GitVersionSpecialty.KNOWS_CORE_COMMENT_CHAR.existsIn(vcs.version)) {
      "Not testing: not possible to fix in Git prior to 1.8.2: ${vcs.version}"
    }

    makeCommit("initial.txt")
    repo.update()
    val initialMessage = "Wrong message"
    file("a").create("initial").addCommit(initialMessage).details()

    val newMessage = """
      Subject

      #body starting with a hash
      """.trimIndent()

    git.setInteractiveRebaseEditor(TestGitImpl.InteractiveRebaseEditor({
      it.lines().mapIndexed { i, s ->
        if (i != 0) s
        else s.replace("pick", "reword")
      }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
    }, null))

    var receivedMessage: String? = null
    dialogManager.onDialog(GitUnstructuredEditor::class.java) {
      receivedMessage = it.text
      val field = GitUnstructuredEditor::class.java.getDeclaredField("myTextEditor")
      field.isAccessible = true
      val commitMessage = field.get(it) as CommitMessage
      commitMessage.text = newMessage
      0
    }

    refresh()
    updateChangeListManager()

    rebaseInteractively("HEAD^")

    assertThat(receivedMessage).describedAs("Initial message is incorrect").isEqualTo(initialMessage)
    assertLastMessage(newMessage)
  }

  @Test
  fun `test cancel in interactive rebase should show no error notification`(): Unit = with(context) {
    repo.`diverge feature and master`()

    dialogManager.onDialog(GitInteractiveRebaseDialog::class.java) {
      DialogWrapper.CANCEL_EXIT_CODE
    }

    rebaseInteractively()

    assertNoErrorNotification()
    assertNoRebaseInProgress(repo)
    repo.`assert feature not rebased on master`()
  }

  @Test
  fun `test cancel in noop case should show no error notification`(): Unit = with(context) {
    build {
      master {
        0()
        1()
      }
      feature(0) {}
    }

    dialogManager.onMessage { Messages.CANCEL }

    rebaseInteractively()

    assertNoErrorNotification()
    assertNoRebaseInProgress(repo)
    repo.`assert feature not rebased on master`()
  }

  @Test
  fun `test rebase on branch with the same name as tag`(): Unit = with(context) {
    build {
      master {
        0("1.txt")
        git("tag master")
        1("2.txt")
      }
      feature(0) {}
    }

    val uiHandler = Mockito.mock(GitBranchUiHandler::class.java)
    `when`(uiHandler.progressIndicator).thenReturn(EmptyProgressIndicator())

    GitBranchWorker(project, git, uiHandler).rebase(listOf(repo), "master")
    assertNoErrorNotification()
    assertThat(File(repo.root.path, "2.txt")).doesNotExist()

    GitBranchWorker(project, git, uiHandler).rebase(listOf(repo), GitBranch.REFS_HEADS_PREFIX + "master")
    assertNoErrorNotification()
    assertThat(File(repo.root.path, "2.txt")).exists()
  }

  @Test
  fun `test checkout with rebase`(): Unit = with(context) {
    repo.`diverge feature and master`()
    checkCheckoutAndRebase {
      "Checked out feature and rebased it on master"
    }
  }

  // IJPL-156329
  // We are not expecting "error: there was a problem with the editor ..." in "Rebase failed" pop-up
  @Test
  fun `test VcsException is handled without showing native git editor error`(): Unit = with(context) {
    build {
      0()
      1()
      2()
    }
    refresh()
    updateChangeListManager()

    dialogManager.onDialog(GitInteractiveRebaseDialog::class.java) {
      DialogWrapper.OK_EXIT_CODE
    }

    val errorMessage = "test exception message!!!"
    git.setInteractiveRebaseEditor(TestGitImpl.InteractiveRebaseEditor({ throw VcsException(errorMessage) }, null))

    rebaseInteractively()

    assertErrorNotification("Rebase failed", errorMessage)
  }

  // IJPL-73981: Verify that --autosquash orders squash! and fixup! commits after their target
  @Test
  fun `test autosquash orders fixup and squash commits`(): Unit = with(context) {
    build {
      master {
        0("base.txt", "base")
      }
      feature(0) {
        1("b.txt", "B", "Commit B")
        2("a.txt", "A", "Commit A")
        3("b.txt", "\nfix", "fixup! Commit B")
        4("b.txt", "\nsq", "squash! Commit B")
      }
    }
    refresh()
    updateChangeListManager()

    var capturedTodo: String? = null
    git.setInteractiveRebaseEditor(
      TestGitImpl.InteractiveRebaseEditor(
        { text ->
          capturedTodo = text
          text
        },
        { it }
      )
    )

    val params = GitRebaseParams(vcs.version,
                                 null,
                                 null,
                                 RebaseUpstream.fromRefString("master"),
                                 setOf(GitRebaseOption.INTERACTIVE, GitRebaseOption.AUTOSQUASH))
    GitTestingRebaseProcess(project, params, repo).rebase()
    val capturedTodoList = capturedTodo ?: error("Interactive rebase todo list was not captured")
    val lines = capturedTodoList.parseRebaseTodoList()

    val indexB = lines.indexOfFirst { it.startsWith("pick ") && it.contains("Commit B") }
    val indexA = lines.indexOfFirst { it.startsWith("pick ") && it.contains("Commit A") }
    val indexSquash = lines.indexOfFirst { (it.startsWith("squash ") || it.startsWith("s ")) && it.contains("squash! Commit B") }
    val indexFixup = lines.indexOfFirst { (it.startsWith("fixup ") || it.startsWith("f ")) && it.contains("fixup! Commit B") }

    assertThat(indexB).describedAs("'pick Commit B' should be present in rebase todo").isNotNegative()
    assertThat(indexA).describedAs("'pick Commit A' should be present in rebase todo").isNotNegative()
    assertThat(indexSquash).describedAs("'squash squash! Commit B' should be present in rebase todo").isNotNegative()
    assertThat(indexFixup).describedAs("'fixup fixup! Commit B' should be present in rebase todo").isNotNegative()

    // Both 'squash!' and 'fixup!' commits should be placed immediately after the target commit
    assertThat(lines.size).describedAs("Rebase todo should contain at least two lines after 'pick Commit B'").isGreaterThan(indexB + 2)
    assertThat(indexSquash).describedAs("squash should immediately follow 'pick Commit B'").isIn(indexB + 1, indexB + 2)
    assertThat(indexFixup).describedAs("fixup should immediately follow 'pick Commit B'").isIn(indexB + 1, indexB + 2)

    val commandsAfterB = setOf(lines[indexB + 1].substringBefore(" "), lines[indexB + 2].substringBefore(" "))
    assertThat(commandsAfterB)
      .describedAs("Expected the two lines after 'pick Commit B' to be 'squash' and 'fixup' (in any order)")
      .isIn(setOf("squash", "fixup"), setOf("s", "f"))

    // Verify Commit A appears after the squash/fixup commits
    assertThat(indexA).describedAs("'pick Commit A' should appear after the squash/fixup commits for Commit B").isGreaterThan(indexB + 2)
  }

  private fun GitPlatformTestContext.moveFileChangeToChangelist(
    fileName: String,
    changeList: LocalChangeList,
  ) {
    val change = changeListManager.getChange(VcsUtil.getFilePath(repo.root, fileName))
    assertThat(change).describedAs("Change for $fileName").isNotNull()
    changeListManager.moveChangesTo(changeList, change)
  }

  private fun String.parseRebaseTodoList(): List<String> {
    return lines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
  }

  private fun GitPlatformTestContext.rebaseInteractively(revision: String = "master") {
    GitTestingRebaseProcess(project, GitRebaseParams(vcs.version, null, null, revision, true, false), repo).rebase()
  }

  private fun GitPlatformTestContext.checkCheckoutAndRebase(expectedNotification: () -> String) {
    repo.git("checkout master")
    refresh()
    updateChangeListManager()

    runUnderProgress { indicator ->
      val uiHandler = Mockito.mock(GitBranchUiHandler::class.java)
      `when`(uiHandler.progressIndicator).thenReturn(indicator)
      GitBranchWorker(project, git, uiHandler).rebaseOnCurrent(listOf(repo), "feature")
    }

    assertSuccessfulRebaseNotification(expectedNotification())
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  private fun build(f: RepoBuilder.() -> Unit) {
    build(repo, f)
  }

  private fun GitPlatformTestContext.ensureUpToDateAndRebaseOnMaster() {
    refresh()
    updateChangeListManager()

    GitTestingRebaseProcess(project, simpleParams("master"), repo).rebase()
  }

  private fun GitPlatformTestContext.simpleParams(newBase: String): GitRebaseParams {
    return GitRebaseParams(vcs.version, newBase)
  }

  private fun file(path: String) = repo.file(path)
  private fun GitPlatformTestContext.refresh() = VfsUtil.markDirtyAndRefresh(false, true, false, testNioRoot)
}
