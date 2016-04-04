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

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.Executor
import git4idea.GitUtil
import git4idea.branch.GitRebaseParams
import git4idea.repo.GitRepository
import git4idea.test.*
import git4idea.test.GitExecutor.cd
import git4idea.test.GitExecutor.git

abstract class GitRebaseBaseTest : GitPlatformTest() {

  protected val LOCAL_CHANGES_WARNING : String = "Note that some local changes were <a>stashed</a> before rebase."

  lateinit protected var myVcsHelper: MockVcsHelper

  override fun setUp() {
    super.setUp()

    myVcsHelper = GitTestUtil.overrideService(myProject, AbstractVcsHelper::class.java, MockVcsHelper::class.java)
  }

  override fun createRepository(rootDir: String) = GitTestUtil.createRepository(myProject, rootDir, false)

  override fun getDebugLogCategories() = listOf("#git4idea.rebase")

  protected fun GitRepository.`diverge feature and master`() {
    build(this) {
      master {
        0()
        1()
      }
      feature(0) {
        2()
      }
    }
  }

  protected fun GitRepository.`place feature above master`() {
    build(this) {
      master {
        0()
        1()
      }
      feature {
        2()
      }
    }
  }

  protected fun GitRepository.`place feature below master`() {
    build(this) {
      master {
        0()
        1()
      }
      feature(0) {
      }
    }
  }

  protected fun GitRepository.`place feature on master`() {
    build(this) {
      master {
        0()
        1()
      }
      feature {}
    }
  }

  protected fun GitRepository.`prepare simple conflict`() {
    build(this) {
      master {
        0("c.txt")
        1("c.txt")
      }
      feature(0) {
        2("c.txt")
      }
    }
  }

  protected fun GitRepository.`make rebase fail on 2nd commit`() {
    build(this) {
      master {
        0()
        1("m.txt")
      }
      feature(0) {
        2()
        3("m.txt")
      }
    }
    `make rebase fail after resolving conflicts`()
  }

  protected fun GitRepository.`make rebase fail after resolving conflicts`() {
    myVcsHelper.onMerge {
      resolveConflicts(this)
      myGit.setShouldRebaseFail { true }
    }
  }

  protected fun resolveConflicts(repository: GitRepository) {
    cd(repository)
    git("add -u .")
  }

  protected fun `do nothing on merge`() {
    myVcsHelper.onMerge{}
  }

  protected fun GitRepository.`assert feature rebased on master`() {
    assertRebased(this, "feature", "master")
  }

  protected fun GitRepository.`assert feature not rebased on master`() {
    assertNotRebased("feature", "master", this)
  }

  protected fun assertRebased(repository: GitRepository, feature: String, master: String) {
    cd(repository)
    assertEquals("$feature is not rebased on $master!", git("rev-parse " + master), git("merge-base $feature $master"))
  }

  protected fun assertNotRebased(feature: String, master: String, repository: GitRepository) {
    cd(repository)
    assertFalse("$feature is unexpectedly rebased on $master" + GitUtil.mention(repository),
                git("rev-parse " + master) == git("merge-base $feature $master"))
  }

  protected fun assertNoRebaseInProgress(repository: GitRepository) {
    assertEquals(Repository.State.NORMAL, repository.state)
  }

  protected fun assertNoRebaseInProgress(repositories: Collection<GitRepository>) {
    for (repository in repositories) {
      assertNoRebaseInProgress(repository)
    }
  }

  protected fun GitRepository.assertRebaseInProgress() {
    assertEquals(Repository.State.REBASING, this.state)
  }

  protected fun GitRepository.assertNoLocalChanges() {
    assertEquals("There should be no local changes!", "", gitStatus())
  }

  protected fun GitRepository.hasConflict(file: String) : Boolean {
    return ("UU " + file).equals(git(this, "status --porcelain"));
  }

  protected fun GitRepository.assertConflict(file: String) {
    assertTrue("Conflict was expected for " + file + ", but git status doesn't show it: \n${git(this, "status --porcelain")}",
               hasConflict(file))
  }

  protected fun `assert conflict not resolved notification`() {
    assertWarningNotification("Rebase Suspended",
        """
        You have to <a>resolve</a> the conflicts and <a>continue</a> rebase.<br/>
        If you want to start from the beginning, you can <a>abort</a> rebase.
        """)
  }

  protected fun `assert conflict not resolved notification with link to stash`() {
    assertWarningNotification("Rebase Suspended",
        """
        You have to <a>resolve</a> the conflicts and <a>continue</a> rebase.<br/>
        If you want to start from the beginning, you can <a>abort</a> rebase.<br/>
        $LOCAL_CHANGES_WARNING
        """)
  }

  protected fun `assert unknown error notification`() {
    assertErrorNotification("Rebase Failed",
        """
        $UNKNOWN_ERROR_TEXT<br/>
        <a>Retry.</a>
        """)
  }

  protected fun `assert unknown error notification with link to abort`(afterContinue : Boolean = false) {
    val expectedTitle = if (afterContinue) "Continue Rebase Failed" else "Rebase Failed";
    assertErrorNotification(expectedTitle,
        """
        $UNKNOWN_ERROR_TEXT<br/>
        You can <a>retry</a> or <a>abort</a> rebase.
        """)
  }

  protected fun `assert unknown error notification with link to stash`() {
    assertErrorNotification("Rebase Failed",
        """
        $UNKNOWN_ERROR_TEXT<br/>
        <a>Retry.</a><br/>
        $LOCAL_CHANGES_WARNING
        """)
  }

  protected fun `assert error about unstaged file before continue rebase`(file : String) {
    assertErrorNotification("Continue Rebase Failed",
        """
          $file: needs update
          You must edit all merge conflicts
          and then mark them as resolved using git add
          You can <a>retry</a> or <a>abort</a> rebase.
          """)
  }

  class LocalChange(val repository: GitRepository, val filePath: String, val content: String = "Some content") {
    fun generate() : LocalChange {
      cd(repository)
      GitExecutor.file(filePath).create(content).add()
      return this
    }

    fun verify() {
      assertNewFile(repository, filePath, content)
    }

    fun assertNewFile(repository: GitRepository, file: String, content: String) {
      cd(repository)
      assertEquals("Incorrect git status output", "A  " + file, git("status --porcelain"))
      assertEquals("Incorrect content of the file [$file]", content, Executor.cat(file))
    }
  }

  protected open class GitTestingRebaseProcess(project: Project, params: GitRebaseParams, val repositories: Collection<GitRepository>) :
    GitRebaseProcess(project, GitRebaseSpec.forNewRebase(project, params, repositories, EmptyProgressIndicator()), null) {

    constructor(project: Project, params: GitRebaseParams, repository: GitRepository) : this(project, params, listOf(repository))

    override fun getDirtyRoots(repositories: Collection<GitRepository>): Collection<GitRepository> {
      return repositories.filter { it.isDirty() }
    }

    protected fun GitRepository.isDirty(): Boolean {
      return !gitStatus().isEmpty();
    }
  }
}

private fun GitRepository.gitStatus() = git(this, "status --porcelain").trim()


