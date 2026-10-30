// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.testFramework.junit5.TestApplication
import git4idea.config.GitSaveChangesPolicy
import git4idea.repo.GitRepositoryManager
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.initRepo
import git4idea.test.withLoadedProject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The fixture project plays the role of the recent project that `RecentProjectsService` loads and holds without a frame.
 */
@TestApplication
internal class GitRecentProjectBrancherTest {
  private val contextFixture = gitSingleRepoContextFixture(saveChangesPolicy = GitSaveChangesPolicy.STASH)
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `canManageBranches is true for a git directory and false otherwise`() {
    val brancher = GitRecentProjectBrancher()
    assertThat(brancher.canManageBranches(context.projectNioRoot)).isTrue()
    assertThat(brancher.canManageBranches(context.testNioRoot.resolve("not-a-repo"))).isFalse()
  }

  /**
   * The branches popup is built from the repository models, which reach the popup over RPC. This checks that they are filled for a recent
   * project that was loaded but never opened, which is the only kind of project the welcome screen shows the popup for.
   */
  @Test
  fun `branches load for the popup of a recent project that is not open`() {
    with(context) {
      // A standalone repository that plays the role of a closed recent project.
      val recentProject = testNioRoot.resolve("recentProject")
      initRepo(project = null, repoRoot = recentProject, makeInitialCommit = true)
      cd(recentProject.toString())
      // Not `context.git`, which always runs in the fixture repository.
      git(null, "branch feature-z")

      val branches = runBlocking {
        withLoadedProject(recentProject) { recent ->
          GitRecentProjectBrancher().loadBranchRepositories(recent, recentProject)
            .flatMap { it.state.localBranches }
            .map { it.name }
        }
      }

      assertThat(branches).describedAs("The popup must show the branches of the closed recent project").contains("feature-z")
    }
  }

  /**
   * The Git staging area of the commit window lists the untracked files of the repository. Their holder starts after the initial VFS
   * refresh, and a project that is not open runs no such refresh by itself.
   */
  @Test
  fun `untracked files load for a recent project that is not open`() {
    with(context) {
      val recentProject = testNioRoot.resolve("recentProject")
      initRepo(project = null, repoRoot = recentProject, makeInitialCommit = true)
      recentProject.resolve("untracked.txt").writeText("untracked")

      val untrackedFiles = runBlocking {
        withLoadedProject(recentProject) { recent ->
          GitRecentProjectBrancher().loadBranchRepositories(recent, recentProject)
          val holder = GitRepositoryManager.getInstance(recent).repositories.single().untrackedFilesHolder
          withTimeout(10.seconds) {
            while (!holder.isInitialized) delay(50.milliseconds)
          }
          holder.untrackedFilePaths.map { it.name }
        }
      }

      assertThat(untrackedFiles).describedAs("The staging area must list the untracked files of the closed recent project")
        .contains("untracked.txt")
    }
  }
}
