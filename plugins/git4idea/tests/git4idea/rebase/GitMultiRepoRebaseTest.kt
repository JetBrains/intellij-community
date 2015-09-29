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

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.mkdir
import com.intellij.openapi.vcs.Executor.touch
import git4idea.branch.GitRebaseParams
import git4idea.rebase.GitRebaseBaseTest.LocalChange
import git4idea.repo.GitRepository
import git4idea.test.GitExecutor.git
import git4idea.test.GitTestUtil.cleanupForAssertion
import git4idea.test.UNKNOWN_ERROR_TEXT
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

public class GitMultiRepoRebaseTest : GitRebaseBaseTest() {

  private var myUltimate: GitRepository by Delegates.notNull()
  private var myCommunity: GitRepository by Delegates.notNull()
  private var myContrib: GitRepository by Delegates.notNull()
  private var myAllRepositories: List<GitRepository> by Delegates.notNull()

  override fun setUp() {
    super.setUp()

    Executor.cd(myProjectRoot)
    val community = mkdir("community")
    val contrib = mkdir("contrib")

    myUltimate = createRepository(myProjectPath)
    myCommunity = createRepository(community.path)
    myContrib = createRepository(contrib.path)
    myAllRepositories = listOf(myUltimate, myCommunity, myContrib)

    Executor.cd(myProjectRoot)
    touch(".gitignore", "community\ncontrib")
    git("add .gitignore")
    git("commit -m gitignore")
  }

  public fun `test all successful`() {
    myUltimate.`place feature above master`()
    myCommunity.`diverge feature and master`()
    myContrib.`place feature on master`()

    rebase("master")

    assertSuccessfulNotification("Rebased feature on master")
    assertAllRebased()
    assertNoRebaseInProgress(myAllRepositories)
  }

  public fun `test abort from critical error during rebasing 2nd root, before any commits were applied`() {
    val localChange = LocalChange(myUltimate, "new.txt", "Some content")
    val rebaseProcess = `fail with critical error while rebasing 2nd root`(localChange)

    assertErrorNotification("Rebase Failed",
        """
        Rebase failed with error in community: <br/>
        $UNKNOWN_ERROR_TEXT
        You can <a>retry</a> or <a>abort</a> rebase.
        $LOCAL_CHANGES_WARNING
        """)

    myUltimate.`assert feature rebased on master`()
    myCommunity.`assert feature not rebased on master`()
    myContrib.`assert feature not rebased on master`()
    assertNoRebaseInProgress(myAllRepositories)
    myUltimate.assertNoLocalChanges()

    var confirmation: String? = null
    myDialogManager.onMessage {
      confirmation = it
      Messages.YES;
    }

    rebaseProcess.abort(null, listOf(myUltimate), EmptyProgressIndicator())

    assertNotNull(confirmation, "Abort confirmation message was not shown")
    assertEquals(cleanupForAssertion("Do you want rollback the successful rebase in project?"),
                 cleanupForAssertion(confirmation!!),
                 "Incorrect confirmation message text");
    assertNoRebaseInProgress(myAllRepositories)
    myAllRepositories.forEach { it.`assert feature not rebased on master`() }

    localChange.verify()
  }

  public fun `test abort from critical error while rebasing 2nd root, after some commits were applied`() {
    val localChange = LocalChange(myUltimate, "new.txt", "Some content")
    val rebaseProcess = `fail with critical error while rebasing 2nd root after some commits are applied`(localChange)

    myVcsNotifier.lastNotification

    var confirmation: String? = null
    myDialogManager.onMessage {
      confirmation = it
      Messages.YES;
    }

    rebaseProcess.abort(myCommunity, listOf(myUltimate), EmptyProgressIndicator())

    assertNotNull(confirmation, "Abort confirmation message was not shown")
    assertEquals(cleanupForAssertion("Do you want just to abort rebase in community, or also rollback the successful rebase in project?"),
                 cleanupForAssertion(confirmation!!),
                 "Incorrect confirmation message text");
    assertNoRebaseInProgress(myAllRepositories)
    myAllRepositories.forEach { it.`assert feature not rebased on master`() }

    localChange.verify()
  }

  public fun `test conflicts in multiple repositories are resolved separately`() {
    myUltimate.`prepare simple conflict`()
    myCommunity.`prepare simple conflict`()
    myContrib.`diverge feature and master`()

    var conflictInUltimate = false
    var conflictInCommunity = false
    myVcsHelper.onMerge({
      if (!conflictInUltimate) {
        myUltimate.assertConflict("c.txt")
        assertNoRebaseInProgress(myCommunity)
        resolveConflicts(myUltimate)
        conflictInUltimate = true
      }
      else {
        assertFalse(conflictInCommunity)
        myCommunity.assertConflict("c.txt")
        assertNoRebaseInProgress(myUltimate)
        resolveConflicts(myCommunity)
        conflictInCommunity = true
      }
    })

    rebase("master")

    assertTrue(conflictInUltimate)
    assertTrue(conflictInCommunity)
    myAllRepositories.forEach {
      it.`assert feature rebased on master`()
      assertNoRebaseInProgress(it)
      it.assertNoLocalChanges()
    }
  }

  public fun `test retry doesn't touch successful repositories`() {
    val rebaseProcess = `fail with critical error while rebasing 2nd root`()

    rebaseProcess.retry(false)

    assertSuccessfulNotification("Rebased feature on master")
    assertAllRebased()
    assertNoRebaseInProgress(myAllRepositories)
  }

  private fun `fail with critical error while rebasing 2nd root`(localChange: LocalChange? = null): GitRebaseProcess {
    myAllRepositories.forEach { it.`diverge feature and master`() }
    localChange?.generate()

    myFailingGit.setShouldFail { it == myCommunity }
    val rebaseProcess = rebase("master")

    myFailingGit.setShouldFail { false }
    return rebaseProcess
  }

  private fun `fail with critical error while rebasing 2nd root after some commits are applied`(localChange: LocalChange? = null): GitRebaseProcess {
    myUltimate.`diverge feature and master`()
    myCommunity.`make rebase fail on 2nd commit`()
    myContrib.`diverge feature and master`()
    localChange?.generate()

    val rebaseProcess = rebase("master")

    myFailingGit.setShouldFail { false }
    return rebaseProcess
  }

  private fun rebase(onto: String): GitRebaseProcess {
    val rebaseProcess = GitRebaseProcess(myProject, myAllRepositories, GitRebaseParams(onto), EmptyProgressIndicator())
    rebaseProcess.rebase()
    return rebaseProcess
  }

  private fun assertAllRebased() {
    assertRebased(myUltimate, "feature", "master")
    assertRebased(myCommunity, "feature", "master")
    assertRebased(myContrib, "feature", "master")
  }
}
