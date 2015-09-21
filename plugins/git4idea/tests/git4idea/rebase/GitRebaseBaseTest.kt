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

import com.intellij.dvcs.repo.Repository
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType.ERROR
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.Executor
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.repo.GitRepository
import git4idea.test.*
import git4idea.test.GitExecutor.cd
import git4idea.test.GitExecutor.git
import git4idea.test.GitTestUtil.assertNotification
import kotlin.test.assertEquals
import kotlin.test.assertFalse

public abstract class GitRebaseBaseTest : GitPlatformTest() {

  protected val LOCAL_CHANGES_WARNING : String = "Note that some local changes were <a>stashed</a> before rebase."

  lateinit protected var myVcsHelper: MockVcsHelper
  lateinit protected var myFailingGit: TestGitImpl

  override fun setUp() {
    super.setUp()

    myVcsHelper = GitTestUtil.overrideService(myProject, AbstractVcsHelper::class.java, MockVcsHelper::class.java)
    myFailingGit = GitTestUtil.overrideService(Git::class.java, TestGitImpl::class.java)
  }

  override fun tearDown() {
    try {
      myFailingGit.setShouldFail { false } // to do check for null
    }
    finally {
      super.tearDown()
    }
  }

  override fun createRepository(path: String) = GitTestUtil.createRepository(myProject, path, false)

  override fun getDebugLogCategories() = listOf("#" + GitRebaseProcess::class.java.name)

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

  protected fun assertSuccessfulNotification(message: String) {
    assertNotification(INFORMATION, "Rebase Successful", message, myVcsNotifier.lastNotification)
  }

  protected fun assertWarningNotification(title: String, message: String) {
    assertNotification(WARNING, title, message, myVcsNotifier.lastNotification)
  }

  protected fun assertErrorNotification(title: String, message: String) : Notification {
    val notification = myVcsNotifier.lastNotification
    assertNotification(ERROR, title, message, notification)
    return notification
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
      myFailingGit.setShouldFail { true }
    }
  }

  protected fun resolveConflicts(repository: GitRepository) {
    cd(repository)
    git("add -u .")
  }

  protected fun GitRepository.`assert feature rebased on master`() {
    assertRebased(this, "feature", "master")
  }

  protected fun GitRepository.`assert feature not rebased on master`() {
    assertNotRebased("feature", "master", this)
  }

  protected fun assertRebased(repository: GitRepository, feature: String, master: String) {
    cd(repository)
    assertEquals(git("rev-parse " + master), git("merge-base $feature $master"))
  }

  protected fun assertNotRebased(feature: String, master: String, repository: GitRepository) {
    cd(repository)
    assertFalse(git("rev-parse " + master) == git("merge-base $feature $master"),
                "$feature is unexpectedly rebased on $master" + GitUtil.mention(repository))
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
    assertEquals("", gitStatus(), "There should be no local changes!")
  }

  protected fun GitRepository.isDirty(): Boolean {
    return !gitStatus().isEmpty();
  }

  private fun GitRepository.gitStatus() = git(this, "status --porcelain").trim()

  protected fun GitRepository.assertConflict(file: String) {
    assertEquals("UU " + file, git(this, "status --porcelain"))
  }

  public class LocalChange(val repository: GitRepository, val filePath: String, val content: String = "Some content") {
    fun generate() : LocalChange {
      cd(repository)
      GitExecutor.file(filePath).create(content).add()
      return this
    }

    fun verify() {
      assertNewFile(repository, filePath, content)
    }

    public fun assertNewFile(repository: GitRepository, file: String, content: String) {
      cd(repository)
      assertEquals("A  " + file, git("status --porcelain"), "Incorrect git status output")
      assertEquals(content, Executor.cat(file), "Incorrect content of the file [$file]")
    }
  }
}
