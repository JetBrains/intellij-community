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
import com.intellij.util.LineSeparator
import git4idea.branch.GitBranchUiHandler
import git4idea.branch.GitBranchWorker
import git4idea.branch.GitRebaseParams
import git4idea.repo.GitRepository
import git4idea.test.GitExecutor.file
import git4idea.test.GitExecutor.git
import git4idea.test.RepoBuilder
import git4idea.test.UNKNOWN_ERROR_TEXT
import git4idea.test.build
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import kotlin.properties.Delegates
import kotlin.test.assertFailsWith

class GitSingleRepoRebaseTest : GitRebaseBaseTest() {

  protected var myRepo: GitRepository by Delegates.notNull()

  override fun setUp() {
    super.setUp()
    myRepo = createRepository(myProjectPath)
  }

  fun `test simple case`() {
    myRepo.`diverge feature and master`()

    rebaseOnMaster()

    assertSuccessfulNotification("Rebased feature on master")
    myRepo.`assert feature rebased on master`()
    assertNoRebaseInProgress(myRepo)
  }

  fun `test up-to-date`() {
    myRepo.`place feature above master`()

    rebaseOnMaster()

    assertSuccessfulNotification("feature is up-to-date with master")
    myRepo.`assert feature rebased on master`()
    assertNoRebaseInProgress(myRepo)
  }

  fun test_ff() {
    myRepo.`place feature below master`()

    rebaseOnMaster()

    assertSuccessfulNotification("Fast-forwarded feature to master")
    myRepo.`assert feature rebased on master`()
    assertNoRebaseInProgress(myRepo)
  }

  fun `test conflict resolver is shown`() {
    myRepo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()

    assertTrue(myVcsHelper.mergeDialogWasShown())
  }

  fun `test fail on 2nd commit should show notification with proposal to abort`() {
    myRepo.`make rebase fail on 2nd commit`()

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
    myVcsHelper.onMerge {
        conflicts++
        myRepo.assertConflict("c.txt")
        resolveConflicts(myRepo)
    }

    rebaseOnMaster()

    assertEquals("Incorrect number of conflicting patches", 2, conflicts)
    myRepo.`assert feature rebased on master`()
    assertSuccessfulNotification("Rebased feature on master")
  }

  fun `test continue rebase after resolving all conflicts`() {
    myRepo.`prepare simple conflict`()

    myVcsHelper.onMerge {
        resolveConflicts(myRepo)
    }

    rebaseOnMaster()
    assertSuccessfulNotification("Rebased feature on master")
    myRepo.`assert feature rebased on master`()
    assertNoRebaseInProgress(myRepo)
  }

  fun `test warning notification if conflicts were not resolved`() {
    myRepo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()
    `assert conflict not resolved notification`()
    myRepo.assertRebaseInProgress()
  }

  fun `test skip if user decides to skip`() {
    myRepo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()

    GitRebaseUtils.skipRebase(myProject)

    assertSuccessfulNotification("Rebased feature on master")
    myRepo.`assert feature rebased on master`()
    assertNoRebaseInProgress(myRepo)
  }

  fun `test rebase failed for unknown reason`() {
    myRepo.`diverge feature and master`()
    myGit.setShouldRebaseFail { true }
    rebaseOnMaster()
    `assert unknown error notification`()
  }

  fun `test propose to abort when rebase failed after continue`() {
    myRepo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()

    myRepo.`assert feature not rebased on master`()
    myRepo.assertRebaseInProgress()
    resolveConflicts(myRepo)

    myGit.setShouldRebaseFail { true }

    GitRebaseUtils.continueRebase(myProject)

    `assert unknown error notification with link to abort`(true)
    myRepo.`assert feature not rebased on master`()
    myRepo.assertRebaseInProgress()
  }

  fun `test local changes auto-saved initially`() {
    myRepo.`diverge feature and master`()
    val localChange = LocalChange(myRepo, "new.txt").generate()

    object : GitTestingRebaseProcess(myProject, simpleParams("master"), myRepo) {
      override fun getDirtyRoots(repositories: Collection<GitRepository>): Collection<GitRepository> {
        return listOf(myRepo)
      }
    }.rebase()

    assertSuccessfulNotification("Rebased feature on master")
    assertRebased(myRepo, "feature", "master")
    assertNoRebaseInProgress(myRepo)
    localChange.verify()
  }

  fun `test local changes are saved even if not detected initially`() {
    myRepo.`diverge feature and master`()
    val localChange = LocalChange(myRepo, "new.txt").generate()

    object : GitTestingRebaseProcess(myProject, simpleParams("master"), myRepo) {
      override fun getDirtyRoots(repositories: Collection<GitRepository>): Collection<GitRepository> {
        return emptyList()
      }
    }.rebase()

    assertSuccessfulNotification("Rebased feature on master")
    assertRebased(myRepo, "feature", "master")
    assertNoRebaseInProgress(myRepo)
    localChange.verify()
  }

  fun `test local changes are not restored in case of error even if nothing was rebased`() {
    myRepo.`diverge feature and master`()
    LocalChange(myRepo, "new.txt", "content").generate()

    myGit.setShouldRebaseFail { true }

    rebaseOnMaster()

    assertErrorNotification("Rebase Failed",
        """
        $UNKNOWN_ERROR_TEXT<br/>
        <a>Retry.</a><br/>
        Note that some local changes were <a>stashed</a> before rebase.
        """)
    assertNoRebaseInProgress(myRepo)
    myRepo.assertNoLocalChanges()
    assertFalse(file("new.txt").exists())
  }

  fun `test critical error should show notification and not restore local changes`() {
    myRepo.`diverge feature and master`()
    LocalChange(myRepo, "new.txt", "content").generate()
    myGit.setShouldRebaseFail { true }

    rebaseOnMaster()

    `assert unknown error notification with link to stash`()
    myRepo.assertNoLocalChanges()
  }

  fun `test successful retry from notification on critical error restores local changes`() {
    myRepo.`diverge feature and master`()
    val localChange = LocalChange(myRepo, "new.txt", "content").generate()

    var attempt = 0
    myGit.setShouldRebaseFail { attempt == 0 }

    rebaseOnMaster()

    attempt++
    myVcsNotifier.lastNotification

    GitRebaseUtils.continueRebase(myProject)

    assertNoRebaseInProgress(myRepo)
    myRepo.`assert feature rebased on master`()
    localChange.verify()
  }

  fun `test local changes are restored after successful abort`() {
    myRepo.`prepare simple conflict`()
    val localChange = LocalChange(myRepo, "new.txt", "content").generate()
    myVcsHelper.onMerge {}
    myDialogManager.onMessage { Messages.YES }

    rebaseOnMaster()

    `assert conflict not resolved notification with link to stash`()

    GitRebaseUtils.abort(myProject, EmptyProgressIndicator())

    assertNoRebaseInProgress(myRepo)
    myRepo.`assert feature not rebased on master`()
    localChange.verify()
  }

  fun `test local changes are not restored after failed abort`() {
    myRepo.`prepare simple conflict`()
    LocalChange(myRepo, "new.txt", "content").generate()
    myVcsHelper.onMerge {}
    myDialogManager.onMessage { Messages.YES }

    rebaseOnMaster()

    `assert conflict not resolved notification with link to stash`()

    myGit.setShouldRebaseFail { true }
    GitRebaseUtils.abort(myProject, EmptyProgressIndicator())

    myRepo.assertRebaseInProgress()
    myRepo.`assert feature not rebased on master`()
    myRepo.assertConflict("c.txt")
    assertErrorNotification("Rebase Abort Failed",
        """
        unknown error<br/>
        $LOCAL_CHANGES_WARNING
        """)
  }

  // git rebase --continue should be either called from a commit dialog, either from the GitRebaseProcess.
  // both should prepare the working tree themselves by adding all necessary changes to the index.
  fun `test local changes in the conflicting file should lead to error on continue rebase`() {
    myRepo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()
    myRepo.assertConflict("c.txt")

    //manually resolve conflicts
    resolveConflicts(myRepo)
    file("c.txt").append("more changes after resolving")
    // forget to git add afterwards

    GitRebaseUtils.continueRebase(myProject)

    `assert error about unstaged file before continue rebase`("c.txt")
    myRepo.`assert feature not rebased on master`()
    myRepo.assertRebaseInProgress()
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
    myRepo.assertConflict("c.txt")

    //manually resolve conflicts
    resolveConflicts(myRepo)
    // add more changes to some other file

    file("d.txt").append("more changes after resolving")

    GitRebaseUtils.continueRebase(myProject)

    `assert error about unstaged file before continue rebase`("d.txt")

    myRepo.`assert feature not rebased on master`()
    myRepo.assertRebaseInProgress()
  }

  fun `test unresolved conflict should lead to conflict resolver with continue rebase`() {
    myRepo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()
    myRepo.assertConflict("c.txt")

    myVcsHelper.onMerge {
      resolveConflicts(myRepo)
    }
    GitRebaseUtils.continueRebase(myProject)

    assertSuccessfulNotification("Rebased feature on master")
    myRepo.`assert feature rebased on master`()
    assertNoRebaseInProgress(myRepo)
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

    myVcsHelper.onMerge {
      file("c.txt").write("base\nmaster")
      resolveConflicts(myRepo)
    }

    rebaseOnMaster()

    assertRebased(myRepo, "feature", "master")
    assertNoRebaseInProgress(myRepo)

    assertSuccessfulNotification(
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


    myGit.setInteractiveRebaseEditor {
      it.lines().mapIndexed { i, s ->
        if (i != 0) s
        else s.replace("pick", "edit")
      }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
    }

    rebaseInteractively()

    assertSuccessfulNotification("Rebase Stopped for Editing", "Once you are satisfied with your changes you may <a href='continue'>continue</a>")
    assertEquals("The repository must be in the 'SUSPENDED' state", myRepo, myGitRepositoryManager.ongoingRebaseSpec!!.ongoingRebase)

    GitRebaseUtils.continueRebase(myProject)

    assertSuccessfulNotification("Rebased feature on master")
    myRepo.`assert feature rebased on master`()
    assertNoRebaseInProgress(myRepo)
  }

  fun `test cancel in interactive rebase should show no error notification`() {
    myRepo.`diverge feature and master`()

    myDialogManager.onDialog(GitRebaseEditor::class.java) { DialogWrapper.CANCEL_EXIT_CODE }
    assertFailsWith(ProcessCanceledException::class) { rebaseInteractively() }

    assertNoNotification()
    assertNoRebaseInProgress(myRepo)
    myRepo.`assert feature not rebased on master`()
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
    assertNoRebaseInProgress(myRepo)
    myRepo.`assert feature not rebased on master`()
  }

  private fun rebaseInteractively() {
    GitTestingRebaseProcess(myProject, GitRebaseParams(null, null, "master", true, false), myRepo).rebase()
  }

  fun `test checkout with rebase`() {
    myRepo.`diverge feature and master`()
    git(myRepo, "checkout master")

    val uiHandler = Mockito.mock(GitBranchUiHandler::class.java)
    `when`(uiHandler.progressIndicator).thenReturn(EmptyProgressIndicator())
    GitBranchWorker(myProject, myPlatformFacade, myGit, uiHandler).rebaseOnCurrent(listOf(myRepo), "feature")

    assertSuccessfulNotification("Checked out feature and rebased it on master")
    myRepo.`assert feature rebased on master`()
    assertNoRebaseInProgress(myRepo)
  }

  private fun build(f: RepoBuilder.() -> Unit) {
    build(myRepo, f)
  }

  private fun rebaseOnMaster() {
    GitTestingRebaseProcess(myProject, simpleParams("master"), myRepo).rebase()
  }

  private fun simpleParams(newBase: String): GitRebaseParams {
    return GitRebaseParams(newBase)
  }
}

