// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.testFramework.junit5.TestApplication
import git4idea.config.GitSaveChangesPolicy
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.cd
import git4idea.test.createBroRepo
import git4idea.test.createRepository
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.prepareRemoteRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
internal class GitRecentProjectUpdaterTest {
  private val contextFixture = gitPlatformContextFixture(saveChangesPolicy = GitSaveChangesPolicy.STASH)
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var repo: GitRepository
  private lateinit var parent: Path
  private lateinit var recentProject: Path

  @BeforeEach
  fun setUp() {
    with(context) {
      repo = createRepository(project, projectNioRoot, true)
      cd(projectPath)
      parent = prepareRemoteRepo(repo)
      git("push -u origin master")
      // A standalone clone that plays the role of a closed recent project.
      recentProject = createBroRepo("recentProject", parent)
    }
  }

  @Test
  fun `canUpdate is true for a git directory and false otherwise`() {
    val updater = GitRecentProjectUpdater()
    assertThat(updater.canUpdate(recentProject)).isTrue()
    assertThat(updater.canUpdate(context.testNioRoot.resolve("not-a-repo"))).isFalse()
  }

  @Test
  fun `update pulls new commits into a recent project without opening it`() {
    with(context) {
      // Advance origin/master from the fixture repository.
      cd(repo)
      repo.file("new.txt").create("hello").addCommit("remote commit")
      git("push origin master")

      cd(recentProject)
      val before = git("rev-parse HEAD")

      runBlocking {
        withLoadedProject(recentProject) { project ->
          // A headless test cannot show the update options dialog, so run the silent update path.
          ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE).value = false
          GitRecentProjectUpdater().update(project, recentProject)
        }
      }

      cd(recentProject)
      val after = git("rev-parse HEAD")
      assertThat(after).describedAs("The recent project HEAD must advance after the update").isNotEqualTo(before)
    }
  }

  /** Loads the project at [projectPath] without a frame, runs [action] on it, then disposes it without saving. */
  private suspend fun <T> withLoadedProject(projectPath: Path, action: suspend (Project) -> T): T {
    val projectManager = ProjectManagerEx.getInstanceEx()
    val project = projectManager.loadProject(projectPath)
    try {
      return action(project)
    }
    finally {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction { projectManager.forceCloseProject(project) }
      }
    }
  }
}
