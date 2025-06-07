// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.overwrite
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.util.LineSeparator
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitBranch
import git4idea.branch.GitBranchUiHandler
import git4idea.branch.GitBranchWorker
import git4idea.branch.GitRebaseParams
import git4idea.config.GitVersionSpecialty
import git4idea.i18n.GitBundle
import git4idea.rebase.interactive.dialog.GitInteractiveRebaseDialog
import git4idea.repo.GitRepository
import git4idea.test.*
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.File

class GitSingleRepoRebaseTest : GitRebaseBaseTest() {

  private lateinit var repo: GitRepository

  override fun setUp() {
    super.setUp()
    repo = createRepository(projectPath)
  }

  fun `test simple case`() {
    repo.`diverge feature and master`()

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  fun `test up-to-date`() {
    repo.`place feature above master`()

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  fun test_ff() {
    repo.`place feature below master`()

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  fun `test conflict resolver is shown`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

    ensureUpToDateAndRebaseOnMaster()

    `assert merge dialog was shown`()
  }

  fun `test fail on 2nd commit should show notification with proposal to abort`() {
    repo.`make rebase fail on 2nd commit`()

    ensureUpToDateAndRebaseOnMaster()

    `assert unknown error notification with link to abort`()
  }

  fun `test multiple conflicts`() {
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

    assertEquals("Incorrect number of conflicting patches", 2, conflicts)
    repo.`assert feature rebased on master`()
    assertSuccessfulRebaseNotification("Rebased feature on master")
  }

  fun `test continue rebase after resolving all conflicts`() {
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

  fun `test warning notification if conflicts were not resolved`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

    ensureUpToDateAndRebaseOnMaster()

    `assert conflict not resolved notification`()
    repo.assertRebaseInProgress()
  }

  fun `test skip if user decides to skip`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

    ensureUpToDateAndRebaseOnMaster()

    GitRebaseUtils.skipRebase(project)

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  fun `test rebase failed for unknown reason`() {
    repo.`diverge feature and master`()
    git.setShouldRebaseFail { true }

    ensureUpToDateAndRebaseOnMaster()

    `assert unknown error notification`()
  }

  fun `test propose to abort when rebase failed after continue`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

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

  fun `test local changes auto-saved initially`() {
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

  fun `test local changes are saved even if not detected initially`() {
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

  fun `test local changes are not restored in case of error even if nothing was rebased`() {
    repo.`diverge feature and master`()
    LocalChange(repo, "new.txt", "content").generate()

    git.setShouldRebaseFail { true }

    ensureUpToDateAndRebaseOnMaster()

    assertErrorNotification("Rebase failed",
        """
        $UNKNOWN_ERROR_TEXT<br/>
        $LOCAL_CHANGES_WARNING
        """)
    assertNoRebaseInProgress(repo)
    repo.assertNoLocalChanges()
    assertFalse(file("new.txt").exists())
  }

  fun `test critical error should show notification and not restore local changes`() {
    repo.`diverge feature and master`()
    LocalChange(repo, "new.txt", "content").generate()
    git.setShouldRebaseFail { true }

    ensureUpToDateAndRebaseOnMaster()

    `assert unknown error notification with link to stash`()
    repo.assertNoLocalChanges()
  }

  fun `test successful retry from notification on critical error restores local changes`() {
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

  fun `test local changes are restored after successful abort`() {
    repo.`prepare simple conflict`()
    val localChange = LocalChange(repo, "new.txt", "content").generate()
    `do nothing on merge`()
    dialogManager.onMessage { Messages.YES }

    ensureUpToDateAndRebaseOnMaster()

    `assert conflict not resolved notification with link to stash`()

    GitRebaseUtils.abort(project, EmptyProgressIndicator())

    assertNoRebaseInProgress(repo)
    repo.`assert feature not rebased on master`()
    localChange.verify()
  }

  fun `test local changelists are restored after successful abort`() {
    TestLoggerFactory.enableDebugLogging(testRootDisposable,
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

    val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(Executor.child("file.txt"))!!
    withPartialTracker(file, "1A\n2\n3A\n4\n5A\n") { document, tracker ->
      val ranges = tracker.getRanges()!!
      TestCase.assertEquals(3, ranges.size)
      tracker.moveToChangelist(ranges[1], testChangelist1)
      tracker.moveToChangelist(ranges[2], testChangelist2)
    }

    overwrite("file1.txt", "new content")
    overwrite("file2.txt", "new content")
    overwrite("file3.txt", "new content")
    VfsUtil.markDirtyAndRefresh(false, false, true, repo.root)
    changeListManager.ensureUpToDate()
    changeListManager.moveChangesTo(testChangelist1, changeListManager.getChange(VcsUtil.getFilePath(repo.root, "file2.txt"))!!)
    changeListManager.moveChangesTo(testChangelist2, changeListManager.getChange(VcsUtil.getFilePath(repo.root, "file3.txt"))!!)

    `do nothing on merge`()
    dialogManager.onMessage { message ->
      TestCase.assertTrue(message.contains("Abort rebase in"))
      Messages.YES
    }

    ensureUpToDateAndRebaseOnMaster()

    `assert conflict not resolved notification with link to stash`()

    GitRebaseUtils.abort(project, EmptyProgressIndicator())

    updateChangeListManager()

    assertNoRebaseInProgress(repo)
    repo.`assert feature not rebased on master`()
    assertSuccessfulNotification("Abort rebase succeeded")

    val changelists = changeListManager.changeLists
    assertEquals(3, changelists.size)

    val errorMessage = changelists.joinToString(separator = "\n") { "${it.name} - ${it.changes}" }
    for (changeList in changelists) {
      assertTrue(errorMessage, changeList.changes.size == 2)
    }
  }

  fun `test local changelists are restored after successful rebase`() {
    touch("file.txt", "1\n2\n3\n4\n5\n")
    touch("file1.txt", "content")
    touch("file2.txt", "content")
    touch("file3.txt", "content")
    repo.addCommit("initial")

    repo.`diverge feature and master`()

    val testChangelist1 = changeListManager.addChangeList("TEST_1", null)
    val testChangelist2 = changeListManager.addChangeList("TEST_2", null)

    val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(Executor.child("file.txt"))!!
    withPartialTracker(file, "1A\n2\n3A\n4\n5A\n") { document, tracker ->
      val ranges = tracker.getRanges()!!
      TestCase.assertEquals(3, ranges.size)
      tracker.moveToChangelist(ranges[1], testChangelist1)
      tracker.moveToChangelist(ranges[2], testChangelist2)
    }

    overwrite("file1.txt", "new content")
    overwrite("file2.txt", "new content")
    overwrite("file3.txt", "new content")
    VfsUtil.markDirtyAndRefresh(false, false, true, repo.root)
    changeListManager.ensureUpToDate()
    changeListManager.moveChangesTo(testChangelist1, changeListManager.getChange(VcsUtil.getFilePath(repo.root, "file2.txt"))!!)
    changeListManager.moveChangesTo(testChangelist2, changeListManager.getChange(VcsUtil.getFilePath(repo.root, "file3.txt"))!!)

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    assertNoRebaseInProgress(repo)
    repo.`assert feature rebased on master`()

    val changelists = changeListManager.changeLists
    assertEquals(3, changelists.size)
    for (changeList in changelists) {
      assertTrue("${changeList.name} - ${changeList.changes}", changeList.changes.size == 2)
    }
  }

  fun `test local changelists are restored after successful rebase with resolved conflict`() {
    touch("file.txt", "1\n2\n3\n4\n5\n")
    touch("file1.txt", "content")
    touch("file2.txt", "content")
    touch("file3.txt", "content")
    repo.addCommit("initial")

    repo.`prepare simple conflict`()

    val testChangelist1 = changeListManager.addChangeList("TEST_1", null)
    val testChangelist2 = changeListManager.addChangeList("TEST_2", null)

    val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(Executor.child("file.txt"))!!
    withPartialTracker(file, "1A\n2\n3A\n4\n5A\n") { document, tracker ->
      val ranges = tracker.getRanges()!!
      TestCase.assertEquals(3, ranges.size)
      tracker.moveToChangelist(ranges[1], testChangelist1)
      tracker.moveToChangelist(ranges[2], testChangelist2)
    }

    overwrite("file1.txt", "new content")
    overwrite("file2.txt", "new content")
    overwrite("file3.txt", "new content")
    VfsUtil.markDirtyAndRefresh(false, false, true, repo.root)
    changeListManager.ensureUpToDate()
    changeListManager.moveChangesTo(testChangelist1, changeListManager.getChange(VcsUtil.getFilePath(repo.root, "file2.txt"))!!)
    changeListManager.moveChangesTo(testChangelist2, changeListManager.getChange(VcsUtil.getFilePath(repo.root, "file3.txt"))!!)

    vcsHelper.onMerge {
      repo.resolveConflicts()
    }
    keepCommitMessageAfterConflict()

    ensureUpToDateAndRebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)

    val changelists = changeListManager.changeLists
    assertEquals(3, changelists.size)
    for (changeList in changelists) {
      assertTrue("${changeList.name} - ${changeList.changes}", changeList.changes.size == 2)
    }
  }

  fun `test local changes are not restored after failed abort`() {
    repo.`prepare simple conflict`()
    LocalChange(repo, "new.txt", "content").generate()
    `do nothing on merge`()
    dialogManager.onMessage { Messages.YES }

    ensureUpToDateAndRebaseOnMaster()

    `assert conflict not resolved notification with link to stash`()

    git.setShouldRebaseFail { true }
    GitRebaseUtils.abort(project, EmptyProgressIndicator())

    repo.assertRebaseInProgress()
    repo.`assert feature not rebased on master`()
    repo.assertConflict("c.txt")
    assertErrorNotification(GitBundle.message("rebase.abort.notification.failed.title"),
                            """
                            unknown error<br/>
                            $LOCAL_CHANGES_WARNING
                            """)
  }

  // git rebase --continue should be either called from a commit dialog, either from the GitRebaseProcess.
  // both should prepare the working tree themselves by adding all necessary changes to the index.
  fun `test local changes in the conflicting file should not prevent rebase`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

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
    assertTrue(file("c.txt").read().endsWith(appended))
  }

  fun `test local changes in some other file should lead to error on continue rebase`() {
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

    `do nothing on merge`()

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

  fun `test unstaged changes while stopped for editing stage and retry`() {
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

    git.setInteractiveRebaseEditor(TestGitImpl.InteractiveRebaseEditor({ it.lines().joinToString(LineSeparator.getSystemLineSeparator().separatorString) { s -> s.replace("pick", "edit") } }, { "message" }))

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
    assertTrue(fileA.read().endsWith(editedContent))
    assertFalse(fileB.exists())
  }

  fun `test unresolved conflict should lead to conflict resolver with continue rebase`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()
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

  fun `test skipped commit`() {
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

  fun `test interactive rebase stopped for editing`() {
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

    git.setInteractiveRebaseEditor (TestGitImpl.InteractiveRebaseEditor({
      it.lines().mapIndexed { i, s ->
        if (i != 0) s
        else s.replace("pick", "edit")
      }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
    }, null))

    refresh()
    updateChangeListManager()

    rebaseInteractively()

    assertSuccessfulNotification("Rebase stopped for editing", "")
    assertEquals("The repository must be in the 'SUSPENDED' state", repo, repositoryManager.ongoingRebaseSpec!!.ongoingRebase)

    GitRebaseUtils.continueRebase(project)

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

    // IDEA-140568
  fun `test git help comments are ignored when parsing interactive rebase`() {
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

    assertNotNull("Didn't get any rebase entries", receivedEntries)
    assertEquals("Rebase entries parsed incorrectly", listOf(GitRebaseEntry.Action.PICK), receivedEntries!!.map { it.action })
  }

  // IDEA-176455
  fun `test reword during interactive rebase writes commit message correctly`() {
    Assume.assumeTrue("Not testing: not possible to fix in Git prior to 1.8.2: ${vcs.version}",
                      GitVersionSpecialty.KNOWS_CORE_COMMENT_CHAR.existsIn(vcs.version)) // IDEA-182044

    makeCommit("initial.txt")
    repo.update()
    val initialMessage = "Wrong message"
    file("a").create("initial").addCommit(initialMessage).details()

    val newMessage = """
      Subject

      #body starting with a hash
      """.trimIndent()

    git.setInteractiveRebaseEditor (TestGitImpl.InteractiveRebaseEditor({
      it.lines().mapIndexed { i, s ->
        if (i != 0) s
        else s.replace("pick", "reword")
      }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
    }, null))

    var receivedMessage : String? = null
    dialogManager.onDialog(GitUnstructuredEditor::class.java) {
      receivedMessage = it.text
      val field = GitUnstructuredEditor::class.java.getDeclaredField("myTextEditor")
      field.isAccessible = true
      val commitMessage = field.get (it) as CommitMessage
      commitMessage.text = newMessage
      0
    }

    refresh()
    updateChangeListManager()

    rebaseInteractively("HEAD^")

    assertEquals("Initial message is incorrect", initialMessage, receivedMessage)
    assertLastMessage(newMessage)
  }

  fun `test cancel in interactive rebase should show no error notification`() {
    repo.`diverge feature and master`()

    dialogManager.onDialog(GitInteractiveRebaseDialog::class.java) {
      DialogWrapper.CANCEL_EXIT_CODE
    }

    rebaseInteractively()

    assertNoErrorNotification()
    assertNoRebaseInProgress(repo)
    repo.`assert feature not rebased on master`()
  }

  fun `test cancel in noop case should show no error notification`() {
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

  fun `test rebase on branch with the same name as tag`() {
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
    assertFalse(File(repo.root.path, "2.txt").exists())

    GitBranchWorker(project, git, uiHandler).rebase(listOf(repo), GitBranch.REFS_HEADS_PREFIX + "master")
    assertTrue(File(repo.root.path, "2.txt").exists())
  }

  private fun rebaseInteractively(revision: String = "master") {
    GitTestingRebaseProcess(project, GitRebaseParams(vcs.version, null, null, revision, true, false), repo).rebase()
  }

  fun `test checkout with rebase`() {
    repo.`diverge feature and master`()
    checkCheckoutAndRebase {
      "Checked out feature and rebased it on master"
    }
  }

  // IJPL-156329
  // We are not expecting "error: there was a problem with the editor ..." in "Rebase failed" pop-up
  fun `test VcsException is handled without showing native git editor error`() {
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

  private fun checkCheckoutAndRebase(expectedNotification: () -> String) {
    repo.git("checkout master")
    refresh()
    updateChangeListManager()

    val uiHandler = Mockito.mock(GitBranchUiHandler::class.java)
    `when`(uiHandler.progressIndicator).thenReturn(EmptyProgressIndicator())
    GitBranchWorker(project, git, uiHandler).rebaseOnCurrent(listOf(repo), "feature")

    assertSuccessfulRebaseNotification(expectedNotification())
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  private fun build(f: RepoBuilder.() -> Unit) {
    build(repo, f)
  }

  private fun ensureUpToDateAndRebaseOnMaster() {
    refresh()
    updateChangeListManager()

    GitTestingRebaseProcess(project, simpleParams("master"), repo).rebase()
  }

  private fun simpleParams(newBase: String): GitRebaseParams {
    return GitRebaseParams(vcs.version, newBase)
  }

  internal fun file(path: String) = repo.file(path)
}
