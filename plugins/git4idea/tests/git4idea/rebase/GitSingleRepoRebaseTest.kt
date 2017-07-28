/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.rebase

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.util.Functions
import com.intellij.util.LineSeparator
import git4idea.GitUtil
import git4idea.branch.GitBranchUiHandler
import git4idea.branch.GitBranchWorker
import git4idea.branch.GitRebaseParams
import git4idea.repo.GitRepository
import git4idea.test.*
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import kotlin.properties.Delegates
import kotlin.reflect.jvm.javaField
import kotlin.test.assertFailsWith

class GitSingleRepoRebaseTest : GitRebaseBaseTest() {

  private var repo: GitRepository by Delegates.notNull()

  override fun setUp() {
    super.setUp()
    repo = createRepository(myProjectPath)
  }

  fun `test simple case`() {
    repo.`diverge feature and master`()

    rebaseOnMaster()

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  fun `test up-to-date`() {
    repo.`place feature above master`()

    rebaseOnMaster()

    assertSuccessfulRebaseNotification("feature is up-to-date with master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  fun test_ff() {
    repo.`place feature below master`()

    rebaseOnMaster()

    assertSuccessfulRebaseNotification("Fast-forwarded feature to master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  fun `test conflict resolver is shown`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()

    `assert merge dialog was shown`()
  }

  fun `test fail on 2nd commit should show notification with proposal to abort`() {
    repo.`make rebase fail on 2nd commit`()

    rebaseOnMaster()

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

    rebaseOnMaster()

    assertEquals("Incorrect number of conflicting patches", 2, conflicts)
    repo.`assert feature rebased on master`()
    assertSuccessfulRebaseNotification("Rebased feature on master")
  }

  fun `test continue rebase after resolving all conflicts`() {
    repo.`prepare simple conflict`()

    vcsHelper.onMerge {
        repo.resolveConflicts()
    }

    rebaseOnMaster()
    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  fun `test warning notification if conflicts were not resolved`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()
    `assert conflict not resolved notification`()
    repo.assertRebaseInProgress()
  }

  fun `test skip if user decides to skip`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()

    GitRebaseUtils.skipRebase(myProject)

    assertSuccessfulRebaseNotification("Rebased feature on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  fun `test rebase failed for unknown reason`() {
    repo.`diverge feature and master`()
    myGit.setShouldRebaseFail { true }
    rebaseOnMaster()
    `assert unknown error notification`()
  }

  fun `test propose to abort when rebase failed after continue`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()

    repo.`assert feature not rebased on master`()
    repo.assertRebaseInProgress()
    repo.resolveConflicts()

    myGit.setShouldRebaseFail { true }

    GitRebaseUtils.continueRebase(myProject)

    `assert unknown error notification with link to abort`(true)
    repo.`assert feature not rebased on master`()
    repo.assertRebaseInProgress()
  }

  fun `test local changes auto-saved initially`() {
    repo.`diverge feature and master`()
    val localChange = LocalChange(repo, "new.txt").generate()

    object : GitTestingRebaseProcess(myProject, simpleParams("master"), repo) {
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

    object : GitTestingRebaseProcess(myProject, simpleParams("master"), repo) {
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

    myGit.setShouldRebaseFail { true }

    rebaseOnMaster()

    assertErrorNotification("Rebase Failed",
        """
        $UNKNOWN_ERROR_TEXT<br/>
        <a>Retry.</a><br/>
        Note that some local changes were <a>stashed</a> before rebase.
        """)
    assertNoRebaseInProgress(repo)
    repo.assertNoLocalChanges()
    assertFalse(file("new.txt").exists())
  }

  fun `test critical error should show notification and not restore local changes`() {
    repo.`diverge feature and master`()
    LocalChange(repo, "new.txt", "content").generate()
    myGit.setShouldRebaseFail { true }

    rebaseOnMaster()

    `assert unknown error notification with link to stash`()
    repo.assertNoLocalChanges()
  }

  fun `test successful retry from notification on critical error restores local changes`() {
    repo.`diverge feature and master`()
    val localChange = LocalChange(repo, "new.txt", "content").generate()

    var attempt = 0
    myGit.setShouldRebaseFail { attempt == 0 }

    rebaseOnMaster()

    attempt++
    myVcsNotifier.lastNotification

    GitRebaseUtils.continueRebase(myProject)

    assertNoRebaseInProgress(repo)
    repo.`assert feature rebased on master`()
    localChange.verify()
  }

  fun `test local changes are restored after successful abort`() {
    repo.`prepare simple conflict`()
    val localChange = LocalChange(repo, "new.txt", "content").generate()
    `do nothing on merge`()
    myDialogManager.onMessage { Messages.YES }

    rebaseOnMaster()

    `assert conflict not resolved notification with link to stash`()

    GitRebaseUtils.abort(myProject, EmptyProgressIndicator())

    assertNoRebaseInProgress(repo)
    repo.`assert feature not rebased on master`()
    localChange.verify()
  }

  fun `test local changes are not restored after failed abort`() {
    repo.`prepare simple conflict`()
    LocalChange(repo, "new.txt", "content").generate()
    `do nothing on merge`()
    myDialogManager.onMessage { Messages.YES }

    rebaseOnMaster()

    `assert conflict not resolved notification with link to stash`()

    myGit.setShouldRebaseFail { true }
    GitRebaseUtils.abort(myProject, EmptyProgressIndicator())

    repo.assertRebaseInProgress()
    repo.`assert feature not rebased on master`()
    repo.assertConflict("c.txt")
    assertErrorNotification("Rebase Abort Failed",
        """
        unknown error<br/>
        $LOCAL_CHANGES_WARNING
        """)
  }

  // git rebase --continue should be either called from a commit dialog, either from the GitRebaseProcess.
  // both should prepare the working tree themselves by adding all necessary changes to the index.
  fun `test local changes in the conflicting file should lead to error on continue rebase`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()
    repo.assertConflict("c.txt")

    //manually resolve conflicts
    repo.resolveConflicts()
    file("c.txt").append("more changes after resolving")
    // forget to git add afterwards

    GitRebaseUtils.continueRebase(myProject)

    `assert error about unstaged file before continue rebase`("c.txt")
    repo.`assert feature not rebased on master`()
    repo.assertRebaseInProgress()
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

    rebaseOnMaster()
    repo.assertConflict("c.txt")

    //manually resolve conflicts
    repo.resolveConflicts()
    // add more changes to some other file

    file("d.txt").append("more changes after resolving")

    GitRebaseUtils.continueRebase(myProject)

    `assert error about unstaged file before continue rebase`("d.txt")

    repo.`assert feature not rebased on master`()
    repo.assertRebaseInProgress()
  }

  fun `test unresolved conflict should lead to conflict resolver with continue rebase`() {
    repo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()
    repo.assertConflict("c.txt")

    vcsHelper.onMerge {
      repo.resolveConflicts()
    }
    GitRebaseUtils.continueRebase(myProject)

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

    val hash2skip = DvcsUtil.getShortHash(git("log -2 --pretty=%H").lines()[1])

    vcsHelper.onMerge {
      file("c.txt").write("base\nmaster")
      repo.resolveConflicts()
    }

    rebaseOnMaster()

    assertRebased(repo, "feature", "master")
    assertNoRebaseInProgress(repo)

    assertSuccessfulRebaseNotification(
        """
        Rebased feature on master<br/>
        The following commit was skipped during rebase:<br/>
        <a>$hash2skip</a> commit to be skipped
        """)
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


    myGit.setInteractiveRebaseEditor (TestGitImpl.InteractiveRebaseEditor({
      it.lines().mapIndexed { i, s ->
        if (i != 0) s
        else s.replace("pick", "edit")
      }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
    }, null))

    rebaseInteractively()

    assertSuccessfulNotification("Rebase Stopped for Editing", "Once you are satisfied with your changes you may <a href='continue'>continue</a>")
    assertEquals("The repository must be in the 'SUSPENDED' state", repo, myGitRepositoryManager.ongoingRebaseSpec!!.ongoingRebase)

    GitRebaseUtils.continueRebase(myProject)

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
    GitTestingRebaseProcess(myProject, GitRebaseParams.editCommits("HEAD^", rebaseEditor, false), repo).rebase()

    assertNotNull("Didn't get any rebase entries", receivedEntries)
    assertEquals("Rebase entries parsed incorrectly", listOf(GitRebaseEntry.Action.pick), receivedEntries!!.map { it.action })
  }

  // IDEA-176455
  fun `test reword during interactive rebase writes commit message correctly`() {
    makeCommit("initial.txt")
    repo.update()
    val initialMessage = "Wrong message"
    file("a").create("initial").addCommit(initialMessage).details()

    val newMessage = """
      Subject

      #body starting with a hash
      """.trimIndent()

    myGit.setInteractiveRebaseEditor (TestGitImpl.InteractiveRebaseEditor({
      it.lines().mapIndexed { i, s ->
        if (i != 0) s
        else s.replace("pick", "reword")
      }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
    }, null))

    var receivedMessage : String? = null
    myDialogManager.onDialog(GitRebaseUnstructuredEditor::class.java, { it ->
      receivedMessage = it.text
      val field = GitRebaseUnstructuredEditor::class.java.getDeclaredField("myTextEditor")
      field.isAccessible = true
      val commitMessage = field.get (it) as CommitMessage
      commitMessage.setText(newMessage)
      0
    })

    rebaseInteractively("HEAD^")

    assertEquals("Initial message is incorrect", initialMessage, receivedMessage)
    assertEquals("Resulting message is incorrect", newMessage, git("log HEAD --no-walk --pretty=%B"))
  }

  fun `test cancel in interactive rebase should show no error notification`() {
    repo.`diverge feature and master`()

  myDialogManager.onDialog(GitRebaseEditor::class.java) { DialogWrapper.CANCEL_EXIT_CODE }
    assertFailsWith(ProcessCanceledException::class) { rebaseInteractively() }

    assertNoNotification()
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

    myDialogManager.onMessage { Messages.CANCEL }
    assertFailsWith(ProcessCanceledException::class) { rebaseInteractively() }

    assertNoNotification()
    assertNoRebaseInProgress(repo)
    repo.`assert feature not rebased on master`()
  }

  private fun rebaseInteractively(revision: String = "master") {
    GitTestingRebaseProcess(myProject, GitRebaseParams(null, null, revision, true, false), repo).rebase()
  }

  fun `test checkout with rebase`() {
    repo.`diverge feature and master`()
    git(repo, "checkout master")

    val uiHandler = Mockito.mock(GitBranchUiHandler::class.java)
    `when`(uiHandler.progressIndicator).thenReturn(EmptyProgressIndicator())
    GitBranchWorker(myProject, myGit, uiHandler).rebaseOnCurrent(listOf(repo), "feature")

    assertSuccessfulRebaseNotification("Checked out feature and rebased it on master")
    repo.`assert feature rebased on master`()
    assertNoRebaseInProgress(repo)
  }

  private fun build(f: RepoBuilder.() -> Unit) {
    build(repo, f)
  }

  private fun rebaseOnMaster() {
    GitTestingRebaseProcess(myProject, simpleParams("master"), repo).rebase()
  }

  private fun simpleParams(newBase: String): GitRebaseParams {
    return GitRebaseParams(newBase)
  }
}
