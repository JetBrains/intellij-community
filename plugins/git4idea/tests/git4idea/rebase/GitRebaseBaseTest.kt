// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.dvcs.repo.Repository
import com.intellij.notification.Notification
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.OpenProjectTaskBuilder
import git4idea.GitUtil
import git4idea.branch.GitRebaseParams
import git4idea.config.GitVersion
import git4idea.repo.GitRepository
import git4idea.test.*
import java.nio.file.Path
import java.nio.file.Paths

abstract class GitRebaseBaseTest : GitPlatformTest() {
  override fun getOpenProjectOptions(): OpenProjectTaskBuilder {
    return super.getOpenProjectOptions().componentStoreLoadingEnabled(false)
  }

  // on rebase shelf file is created in .idea/shelf/Uncommitted_changes_before_rebase_[Default_Changelist] and it leads to test failures because of assertNoLocalChanges
  override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
    return Paths.get(FileUtil.getTempDirectory(), "p.ipr")
  }

  private val saved = getDefaultSaveChangesPolicy().name.toLowerCase().let { save ->
    if (save.endsWith("e")) "${save}d" else "${save}ed"
  }

  protected val LOCAL_CHANGES_WARNING: String = "Local changes were ${saved} before rebase."

  override fun createRepository(rootDir: String) = createRepository(project, Paths.get(rootDir), false)

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

  private fun GitRepository.`make rebase fail after resolving conflicts`() {
    vcsHelper.onMerge {
      this.resolveConflicts()
      git.setShouldRebaseFail { true }
    }
  }

  protected fun assertSuccessfulRebaseNotification(message: String) : Notification {
    return assertSuccessfulNotification("Rebase successful", message)
  }

  protected fun GitRepository.`assert feature rebased on master`() {
    assertRebased(this, "feature", "master")
  }

  protected fun GitRepository.`assert feature not rebased on master`() {
    assertNotRebased("feature", "master", this)
  }

  protected fun assertRebased(repository: GitRepository, feature: String, master: String) {
    cd(repository)
    assertEquals("$feature is not rebased on $master!", git("rev-parse $master"), git("merge-base $feature $master"))
  }

  protected fun assertNotRebased(feature: String, master: String, repository: GitRepository) {
    cd(repository)
    assertFalse("$feature is unexpectedly rebased on $master" + GitUtil.mention(repository),
                git("rev-parse $master") == git("merge-base $feature $master"))
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
    return ("UU $file") == gitStatus()
  }

  protected fun GitRepository.assertConflict(file: String) {
    assertTrue("Conflict was expected for " + file + ", but git status doesn't show it: \n${gitStatus()}",
               hasConflict(file))
  }

  protected fun `assert conflict not resolved notification`() {
    assertWarningNotification("Rebase stopped due to conflicts",
        """
        """)
  }

  protected fun `assert conflict not resolved notification with link to stash`() {
    assertWarningNotification("Rebase stopped due to conflicts",
        """
        $LOCAL_CHANGES_WARNING
        """)
  }

  protected fun `assert unknown error notification`() {
    assertErrorNotification("Rebase failed",
        """
        $UNKNOWN_ERROR_TEXT<br/>
        """)
  }

  protected fun `assert unknown error notification with link to abort`(afterContinue : Boolean = false) {
    val expectedTitle = if (afterContinue) "Continue rebase failed" else "Rebase failed"
    assertErrorNotification(expectedTitle,
        """
        $UNKNOWN_ERROR_TEXT<br/>
        """)
  }

  protected fun `assert unknown error notification with link to stash`() {
    assertErrorNotification("Rebase failed",
        """
        $UNKNOWN_ERROR_TEXT<br/>
        $LOCAL_CHANGES_WARNING
        """)
  }

  protected fun `assert error about unstaged file before continue rebase`() {
    assertErrorNotification("Continue rebase failed",
                            "There are unstaged changes in tracked files preventing rebase from continuing",
                            actions = listOf("Stage and Retry", "Show Files", "Abort"))
  }

  protected fun keepCommitMessageAfterConflict() {
    dialogManager.onDialog(GitUnstructuredEditor::class.java) {
      DialogWrapper.OK_EXIT_CODE
    }
  }

  inner class LocalChange(val repository: GitRepository, private val filePath: String, val content: String = "Some content") {
    fun generate() : LocalChange {
      cd(repository)
      val file = repository.file(filePath).create(content)
      file.add()
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.file)
      return this
    }

    fun verify() {
      assertNewFile(repository, filePath, content)
    }

    private fun assertNewFile(repository: GitRepository, file: String, content: String) {
      cd(repository)
      assertEquals("Incorrect git status output", "A  $file", git("status --porcelain"))
      assertEquals("Incorrect content of the file [$file]", content, Executor.cat(file))
    }
  }

  protected open class GitTestingRebaseProcess(project: Project, params: GitRebaseParams, val repositories: Collection<GitRepository>) :
    GitRebaseProcess(project, GitRebaseSpec.forNewRebase(project, params, repositories, EmptyProgressIndicator()), null) {

    constructor(project: Project, params: GitRebaseParams, repository: GitRepository) : this(project, params, listOf(repository))

    override fun getDirtyRoots(repositories: Collection<GitRepository>): Collection<GitRepository> {
      return repositories.filter { it.isDirty() }
    }

    private fun GitRepository.isDirty(): Boolean {
      return !gitStatus().isEmpty()
    }
  }
}

private fun GitRepository.gitStatus() = this.git("status --porcelain").trim()


