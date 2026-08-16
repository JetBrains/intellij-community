// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.dvcs.repo.Repository
import com.intellij.notification.Notification
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.vcs.test.assertErrorNotification
import com.intellij.vcs.test.assertSuccessfulNotification
import com.intellij.vcs.test.assertWarningNotification
import git4idea.GitUtil
import git4idea.branch.GitRebaseParams
import git4idea.config.GitSaveChangesPolicy
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.UNKNOWN_ERROR_TEXT
import git4idea.test.build
import git4idea.test.cd
import git4idea.test.file
import git4idea.test.git
import git4idea.test.resolveConflicts
import git4idea.test.runUnderProgress
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories


/**
 * The project root is also the repository root in the rebase tests, so shelving the local changes before a rebase
 * creates `<project>/.idea/shelf/...` inside the working tree. That would make `git status` non-empty and break
 * [assertNoLocalChanges] and [assertConflict]. Hide the IDE project directory from Git instead — `.git/info/exclude`
 * is not part of the working tree, so it does not show up as a change itself.
 */
internal fun GitRepository.hideIdeaProjectFilesFromGit() {
  val exclude = root.toNioPath().resolve(Path.of(".git", "info", "exclude"))
  exclude.parent.createDirectories()
  exclude.appendText("${System.lineSeparator()}/.idea/${System.lineSeparator()}")
}

internal fun localChangesWarning(saveChangesPolicy: GitSaveChangesPolicy): String {
  val saved = saveChangesPolicy.name.lowercase(Locale.getDefault()).let { save ->
    if (save.endsWith("e")) "${save}d" else "${save}ed"
  }
  return "Local changes were $saved before rebase."
}

// ---------------------------------------------------------------------------------------------------------------
// History shapes
// ---------------------------------------------------------------------------------------------------------------

internal fun GitRepository.`diverge feature and master`() {
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

internal fun GitRepository.`place feature above master`() {
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

internal fun GitRepository.`place feature below master`() {
  build(this) {
    master {
      0()
      1()
    }
    feature(0) {
    }
  }
}

internal fun GitRepository.`place feature on master`() {
  build(this) {
    master {
      0()
      1()
    }
    feature {}
  }
}

internal fun GitRepository.`prepare simple conflict`() {
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

internal fun GitPlatformTestContext.`make rebase fail on 2nd commit`(repository: GitRepository) {
  build(repository) {
    master {
      0()
      1("m.txt")
    }
    feature(0) {
      2()
      3("m.txt")
    }
  }
  `make rebase fail after resolving conflicts`(repository)
}

private fun GitPlatformTestContext.`make rebase fail after resolving conflicts`(repository: GitRepository) {
  vcsHelper.onMerge {
    repository.resolveConflicts()
    git.setShouldRebaseFail { true }
  }
}

// ---------------------------------------------------------------------------------------------------------------
// Assertions
// ---------------------------------------------------------------------------------------------------------------

internal fun GitPlatformTestContext.assertSuccessfulRebaseNotification(message: String): Notification {
  return assertSuccessfulNotification("Rebase successful", message)
}

internal fun GitRepository.`assert feature rebased on master`() {
  assertRebased(this, "feature", "master")
}

internal fun GitRepository.`assert feature not rebased on master`() {
  assertNotRebased("feature", "master", this)
}

internal fun assertRebased(repository: GitRepository, feature: String, master: String) {
  assertThat(repository.git("merge-base $feature $master"))
    .describedAs("$feature is not rebased on $master!")
    .isEqualTo(repository.git("rev-parse $master"))
}

internal fun assertNotRebased(feature: String, master: String, repository: GitRepository) {
  assertThat(repository.git("merge-base $feature $master"))
    .describedAs("$feature is unexpectedly rebased on $master" + GitUtil.mention(repository))
    .isNotEqualTo(repository.git("rev-parse $master"))
}

internal fun assertNoRebaseInProgress(repository: GitRepository) {
  assertThat(repository.state).isEqualTo(Repository.State.NORMAL)
}

internal fun assertNoRebaseInProgress(repositories: Collection<GitRepository>) {
  for (repository in repositories) {
    assertNoRebaseInProgress(repository)
  }
}

internal fun GitRepository.assertRebaseInProgress() {
  assertThat(state).isEqualTo(Repository.State.REBASING)
}

internal fun GitRepository.assertNoLocalChanges() {
  assertThat(gitStatus()).describedAs("There should be no local changes!").isEmpty()
}

internal fun GitRepository.hasConflict(file: String): Boolean {
  return ("UU $file") == gitStatus()
}

internal fun GitRepository.assertConflict(file: String) {
  assertThat(hasConflict(file))
    .describedAs("Conflict was expected for $file, but git status doesn't show it: \n${gitStatus()}")
    .isTrue()
}

internal fun GitPlatformTestContext.`assert conflict not resolved notification`() {
  assertWarningNotification("Rebase stopped due to conflicts",
                            """
                            """)
}

internal fun GitPlatformTestContext.`assert conflict not resolved notification with link to stash`(saveChangesPolicy: GitSaveChangesPolicy) {
  assertWarningNotification("Rebase stopped due to conflicts",
                            """
                            ${localChangesWarning(saveChangesPolicy)}
                            """)
}

internal fun GitPlatformTestContext.`assert unknown error notification`() {
  assertErrorNotification("Rebase failed",
                          """
                          $UNKNOWN_ERROR_TEXT<br/>
                          """)
}

internal fun GitPlatformTestContext.`assert unknown error notification with link to abort`(afterContinue: Boolean = false) {
  val expectedTitle = if (afterContinue) "Continue rebase failed" else "Rebase failed"
  assertErrorNotification(expectedTitle,
                          """
                          $UNKNOWN_ERROR_TEXT<br/>
                          """)
}

internal fun GitPlatformTestContext.`assert unknown error notification with link to stash`(saveChangesPolicy: GitSaveChangesPolicy) {
  assertErrorNotification("Rebase failed",
                          """
                          $UNKNOWN_ERROR_TEXT<br/>
                          ${localChangesWarning(saveChangesPolicy)}
                          """)
}

internal fun GitPlatformTestContext.`assert error about unstaged file before continue rebase`() {
  assertErrorNotification("Continue rebase failed",
                          "There are unstaged changes in tracked files preventing rebase from continuing",
                          actions = listOf("Stage and Retry", "Show Files", "Abort"))
}

internal fun GitPlatformTestContext.keepCommitMessageAfterConflict() {
  dialogManager.onDialog(GitUnstructuredEditor::class.java) {
    DialogWrapper.OK_EXIT_CODE
  }
}

// ---------------------------------------------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------------------------------------------

internal class LocalChange(
  val repository: GitRepository,
  private val filePath: String,
  val content: String = "Some content",
) {
  fun generate(): LocalChange {
    cd(repository)
    val file = repository.file(filePath).create(content)
    file.add()
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.file)
    return this
  }

  fun verify() {
    cd(repository)
    assertThat(repository.git("status --porcelain")).describedAs("Incorrect git status output").isEqualTo("A  $filePath")
    assertThat(repository.file(filePath).read()).describedAs("Incorrect content of the file [$filePath]").isEqualTo(content)
  }
}

internal open class GitTestingRebaseProcess(
  private val project: Project,
  private val params: GitRebaseParams,
  private val repositories: Collection<GitRepository>,
) {
  constructor(project: Project, params: GitRebaseParams, repository: GitRepository) :
    this(project, params, listOf(repository))

  fun rebase() {
    runUnderProgress { indicator ->
      val spec = GitRebaseSpec.forNewRebase(project, params, repositories, indicator)
      val process = object : GitRebaseProcess(project, spec, null) {
        override fun getDirtyRoots(repos: Collection<GitRepository>): Collection<GitRepository> {
          return this@GitTestingRebaseProcess.getDirtyRoots(repos)
        }
      }
      process.rebase()
    }
  }

  protected open fun getDirtyRoots(repositories: Collection<GitRepository>): Collection<GitRepository> {
    return repositories.filter { it.isDirty() }
  }

  private fun GitRepository.isDirty(): Boolean {
    return gitStatus().isNotEmpty()
  }
}

private fun GitRepository.gitStatus() = this.git("status --porcelain").trim()
