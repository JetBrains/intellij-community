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
import com.intellij.notification.Notification
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.Executor
import git4idea.GitUtil
import git4idea.branch.GitRebaseParams
import git4idea.config.GitVersion
import git4idea.repo.GitRepository
import git4idea.test.*

abstract class GitRebaseBaseTest : GitPlatformTest() {

  protected val LOCAL_CHANGES_WARNING : String = "Local changes were stashed before rebase."

  override fun createRepository(rootDir: String) = createRepository(project, rootDir, false)

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#git4idea.rebase")

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
    vcsHelper.onMerge {
      this.resolveConflicts()
      git.setShouldRebaseFail { true }
    }
  }

  protected fun assertSuccessfulRebaseNotification(message: String) : Notification {
    return assertSuccessfulNotification("Rebase Successful", message)
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
    return ("UU " + file).equals(gitStatus());
  }

  protected fun GitRepository.assertConflict(file: String) {
    assertTrue("Conflict was expected for " + file + ", but git status doesn't show it: \n${gitStatus()}",
               hasConflict(file))
  }

  protected fun `assert conflict not resolved notification`() {
    assertWarningNotification("Rebase Stopped Due to Conflicts",
        """
        """)
  }

  protected fun `assert conflict not resolved notification with link to stash`() {
    assertWarningNotification("Rebase Stopped Due to Conflicts",
        """
        $LOCAL_CHANGES_WARNING
        """)
  }

  protected fun `assert unknown error notification`() {
    assertErrorNotification("Rebase Failed",
        """
        $UNKNOWN_ERROR_TEXT<br/>
        """)
  }

  protected fun `assert unknown error notification with link to abort`(afterContinue : Boolean = false) {
    val expectedTitle = if (afterContinue) "Continue Rebase Failed" else "Rebase Failed";
    assertErrorNotification(expectedTitle,
        """
        $UNKNOWN_ERROR_TEXT<br/>
        """)
  }

  protected fun `assert unknown error notification with link to stash`() {
    assertErrorNotification("Rebase Failed",
        """
        $UNKNOWN_ERROR_TEXT<br/>
        $LOCAL_CHANGES_WARNING
        """)
  }

  protected fun `assert error about unstaged file before continue rebase`(file : String) {
    val fileLine = if (vcs.version.isLaterOrEqual(GitVersion(1, 7, 3, 0))) "$file: needs update" else ""
    assertErrorNotification("Continue Rebase Failed",
          """
          $fileLine
          You must edit all merge conflicts
          and then mark them as resolved using git add
          """)
  }

  inner class LocalChange(val repository: GitRepository, val filePath: String, val content: String = "Some content") {
    fun generate() : LocalChange {
      cd(repository)
      repository.file(filePath).create(content).add()
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

private fun GitRepository.gitStatus() = this.git("status --porcelain").trim()


