// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.vfs

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import git4idea.config.GitSaveChangesPolicy
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.initRepo
import git4idea.test.withLoadedProject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

@TestApplication
internal class GitIndexFileSystemRefresherTest {
  private val contextFixture = gitSingleRepoContextFixture(saveChangesPolicy = GitSaveChangesPolicy.STASH)
  private val context: GitSingleRepoContext get() = contextFixture.get()

  /** [withLoadedProject] disposes the project the way the welcome screen does, with no close event. */
  @Test
  fun `the refresher of a project disposed without a close event does not keep the project`() {
    with(context) {
      val recentProject = testNioRoot.resolve("recentProject")
      initRepo(project = null, repoRoot = recentProject, makeInitialCommit = true)

      val disposedProject = runBlocking {
        withLoadedProject(recentProject) { recent ->
          GitIndexFileSystemRefresher.getInstance(recent)
          recent
        }
      }

      runInEdtAndWait { LeakHunter.checkLeak(Disposer.getTree(), Project::class.java) { it === disposedProject } }
    }
  }
}
