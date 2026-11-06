// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import git4idea.config.GitSaveChangesPolicy
import git4idea.prepareRecentGitProject
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.initRepo
import git4idea.test.withLoadedProject
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Tests that the fsmonitor daemon of a recent project the welcome screen loaded without a frame does not outlive the IDE.
 *
 * Such a project is never open, so the stop at exit does not find it among the open projects. [withLoadedProject] loads and disposes it
 * the way `RecentProjectsService` does.
 */
@TestApplication
internal class GitFsMonitorDaemonStopperTest {
  private val contextFixture = gitSingleRepoContextFixture(saveChangesPolicy = GitSaveChangesPolicy.STASH)
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `a Git action on a frameless project records its root for the stop at exit`() {
    with(context) {
      val recentProject = createRecentProject()

      runBlocking {
        withLoadedProject(recentProject) { recent -> prepareRecentGitProject(recent, recentProject) }
      }

      // The set lives as long as the application, so it can still hold the roots of other tests, whose directories are gone.
      val recentRoot = recentProject.toRealPath()
      assertThat(GitFsMonitorDaemonStopper.getInstance().getFramelessProjectRoots()).anyMatch { it.toNioPath() == recentRoot }
    }
  }

  @Test
  fun `the stop at exit stops the fsmonitor daemon of a frameless project`() {
    assumeTrue(SystemInfo.isMac || SystemInfo.isWindows, "Git has a built-in fsmonitor daemon only on macOS and Windows")
    with(context) {
      val recentProject = createRecentProject()
      git(null, "config core.fsmonitor true")
      try {
        runBlocking {
          withLoadedProject(recentProject) { recent ->
            prepareRecentGitProject(recent, recentProject)
            git(null, "status")
            assertThat(daemonStatus()).describedAs("git status starts the daemon").contains("is watching")
          }
        }

        runInEdtAndWait { GitFsMonitorProjectCloseListener().beforeAppWillBeClosed(isRestart = false) }

        assertThat(daemonStatus()).contains("is not watching")
      }
      finally {
        git(null, "fsmonitor--daemon stop", ignoreNonZeroExitCode = true)
      }
    }
  }

  /** Creates a standalone repository that plays the role of a closed recent project, and makes it the current directory. */
  private fun GitSingleRepoContext.createRecentProject(): Path {
    val recentProject = testNioRoot.resolve("recentProject")
    initRepo(project = null, repoRoot = recentProject, makeInitialCommit = true)
    cd(recentProject.toString())
    return recentProject
  }

  private fun daemonStatus(): String = git(null, "fsmonitor--daemon status", ignoreNonZeroExitCode = true)
}
