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
package git4idea.rebase

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.Messages
import git4idea.branch.GitRebaseParams
import git4idea.rebase.GitRebaseBaseTest.LocalChange
import git4idea.repo.GitRepository
import git4idea.test.GitExecutor.file
import git4idea.test.GitExecutor.git
import git4idea.test.RepoBuilder
import git4idea.test.UNKNOWN_ERROR_TEXT
import git4idea.test.build
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class GitSingleRepoRebaseTest : GitRebaseBaseTest() {

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

    assertEquals(2, conflicts, "Incorrect number of conflicting patches")
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

  fun `test rebase failed for unknown reason`() {
    myRepo.`diverge feature and master`()
    myFailingGit.setShouldFail { true }
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

    myFailingGit.setShouldFail { true }
    val continueRebase = GitRebaseParams("master").withMode(GitRebaseParams.Mode.CONTINUE)
    GitTestingRebaseProcess(continueRebase).rebase()

    `assert unknown error notification with link to abort`()
    myRepo.`assert feature not rebased on master`()
    myRepo.assertRebaseInProgress()
  }

  fun `test local changes auto-saved`() {
    myRepo.`diverge feature and master`()
    val localChange = LocalChange(myRepo, "new.txt").generate()

    rebaseOnMaster()

    assertSuccessfulNotification("Rebased feature on master")
    assertRebased(myRepo, "feature", "master")
    assertNoRebaseInProgress(myRepo)
    localChange.verify()
  }

  fun `test local changes are saved even if not detected initially`() {
    myRepo.`diverge feature and master`()
    val localChange = LocalChange(myRepo, "new.txt").generate()

    object : GitTestingRebaseProcess(GitRebaseParams("master")) {
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

    myFailingGit.setShouldFail { true }

    rebaseOnMaster()

    assertErrorNotification("Rebase Failed",
        """
        Rebase failed with error: $UNKNOWN_ERROR_TEXT<br/>
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
    myFailingGit.setShouldFail { true }

    rebaseOnMaster()

    `assert unknown error notification with link to stash`()
    myRepo.assertNoLocalChanges()
  }

  fun `test successful retry from notification on critical error restores local changes`() {
    myRepo.`diverge feature and master`()
    val localChange = LocalChange(myRepo, "new.txt", "content").generate()

    var attempt = 0
    myFailingGit.setShouldFail { attempt == 0 }

    val rebaseProcess = rebaseOnMaster()

    attempt++
    myVcsNotifier.lastNotification

    rebaseProcess.retry(false)

    assertNoRebaseInProgress(myRepo)
    myRepo.`assert feature rebased on master`()
    localChange.verify()
  }

  fun `test local changes are restored after successful abort`() {
    myRepo.`prepare simple conflict`()
    val localChange = LocalChange(myRepo, "new.txt", "content").generate()
    myVcsHelper.onMerge {}
    myDialogManager.onMessage { Messages.YES }

    val rebaseProcess = rebaseOnMaster()

    `assert conflict not resolved notification with link to stash`()

    rebaseProcess.abort(myRepo, emptyList(), EmptyProgressIndicator())

    assertNoRebaseInProgress(myRepo)
    myRepo.`assert feature not rebased on master`()
    localChange.verify()
  }

  fun `test local changes are not restored after failed abort`() {
    myRepo.`prepare simple conflict`()
    LocalChange(myRepo, "new.txt", "content").generate()
    myVcsHelper.onMerge {}
    myDialogManager.onMessage { Messages.YES }

    val rebaseProcess = rebaseOnMaster()

    `assert conflict not resolved notification with link to stash`()

    myFailingGit.setShouldFail { true }
    rebaseProcess.abort(myRepo, emptyList(), EmptyProgressIndicator())

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
  fun `test local changes should not be saved when continue rebase`() {
    myRepo.`prepare simple conflict`()
    `do nothing on merge`()

    rebaseOnMaster()
    myRepo.assertConflict("c.txt")

    //manually resolve conflicts
    resolveConflicts(myRepo)
    file("c.txt").append("more changes after resolving")
    // forget to git add afterwards

    val continueRebase = GitRebaseParams("master").withMode(GitRebaseParams.Mode.CONTINUE)
    GitTestingRebaseProcess(continueRebase).rebase()

    assertErrorNotification("Rebase Failed",
        """
        Rebase failed with error: c.txt: needs update
        You must edit all merge conflicts
        and then mark them as resolved using git add
        You can <a>retry</a> or <a>abort</a> rebase.
        """)

    myRepo.`assert feature not rebased on master`()
    myRepo.assertRebaseInProgress()
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

    val hash2skip = DvcsUtil.getShortHash(git("log -2 --pretty=%H").lines().get(1))

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

  private fun build(f: RepoBuilder.() -> Unit) {
    build(myRepo, f)
  }

  private fun `do nothing on merge`() {
    myVcsHelper.onMerge{}
  }

  private open inner class GitTestingRebaseProcess(params: GitRebaseParams) :
    GitRebaseProcess(myProject, listOf(myRepo), params, EmptyProgressIndicator()) {
    override fun getDirtyRoots(repositories: Collection<GitRepository>): Collection<GitRepository> {
      return if (myRepo.isDirty()) listOf(myRepo) else emptyList()
    }
  }

  private fun rebaseOnMaster() : GitRebaseProcess {
    val rebaseProcess = GitTestingRebaseProcess(GitRebaseParams("master"))
    rebaseProcess.rebase()
    return rebaseProcess
  }

  private fun `assert conflict not resolved notification`() {
    assertWarningNotification("Rebase Suspended",
        """
        You have to <a>resolve</a> the conflicts and <a>continue</a> rebase.<br/>
        If you want to start from the beginning, you can <a>abort</a> rebase.
        """)
  }

  private fun `assert conflict not resolved notification with link to stash`() {
    assertWarningNotification("Rebase Suspended",
        """
        You have to <a>resolve</a> the conflicts and <a>continue</a> rebase.<br/>
        If you want to start from the beginning, you can <a>abort</a> rebase.<br/>
        $LOCAL_CHANGES_WARNING
        """)
  }

  private fun `assert unknown error notification`() {
    assertErrorNotification("Rebase Failed",
        """
        Rebase failed with error: $UNKNOWN_ERROR_TEXT<br/>
        <a>Retry.</a>
        """)
  }

  private fun `assert unknown error notification with link to abort`() {
    assertErrorNotification("Rebase Failed",
        """
        Rebase failed with error: $UNKNOWN_ERROR_TEXT<br/>
        You can <a>retry</a> or <a>abort</a> rebase.
        """)
  }

  private fun `assert unknown error notification with link to stash`() {
    assertErrorNotification("Rebase Failed",
        """
        Rebase failed with error: $UNKNOWN_ERROR_TEXT<br/>
        <a>Retry.</a><br/>
        $LOCAL_CHANGES_WARNING
        """)
  }
}

