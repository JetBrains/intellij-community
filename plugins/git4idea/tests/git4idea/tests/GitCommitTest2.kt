// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.vcs.test.vcsPlatformFixture
import git4idea.checkin.GitCheckinExplicitMovementProvider
import git4idea.checkin.isCommitRenamesSeparately
import git4idea.config.GitSaveChangesPolicy
import git4idea.test.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

internal interface GitCommitContext : GitSingleRepoContext {
  val myMovementProvider: GitCheckinExplicitMovementProvider
}

private fun gitCommitFixture(useIndexInfoStagedChangesSaver: Boolean) =
  projectFixture(openAfterCreation = true).let { projectFixture ->
    projectFixture
      .vcsPlatformFixture()
      .gitPlatformFixture(projectFixture,
                          defaultSaveChangesPolicy = GitSaveChangesPolicy.SHELVE,
                          hasRemoteGitOperation = false) {
        isCommitRenamesSeparately = true
      }
      .gitSingleRepoFixture(makeInitialCommit = true)
      .gitCommitFixture(useIndexInfoStagedChangesSaver = useIndexInfoStagedChangesSaver)
  }

private fun TestFixture<GitSingleRepoContext>.gitCommitFixture(useIndexInfoStagedChangesSaver: Boolean): TestFixture<GitCommitContext> = testFixture {
  val singleRepoContext = init()
  val movementProvider = extensionPointFixture(GitCheckinExplicitMovementProvider.EP_NAME) {
    MyExplicitMovementProvider()
  }.init()
  registryKeyFixture("git.commit.staged.saver.use.index.info") { setValue(useIndexInfoStagedChangesSaver) }.init()
  val result = object : GitCommitContext, GitSingleRepoContext by singleRepoContext {
    override val myMovementProvider: MyExplicitMovementProvider = movementProvider
  }
  initialized(result) {}
}

internal class GitCommitTest {
  @TestApplicationWithEel
  @ParameterizedClass
  @DockerTest(image = "alpine/git")
  @Nested
  inner class GitCommitWithResetAddTest2(@Suppress("unused") eelHolder: EelHolder) : GitCommitTestBase2(gitCommitFixture(false))

  @TestApplicationWithEel
  @ParameterizedClass
  @DockerTest(image = "alpine/git")
  @Nested
  inner class GitCommitWithIndexInfoTest2(@Suppress("unused") eelHolder: EelHolder) : GitCommitTestBase2(gitCommitFixture(true))
}

internal abstract class GitCommitTestBase2(val gitCommitFixture: TestFixture<GitCommitContext>) {

  @Test
  fun `test commit staged rename`() = gitCommitFixture.get().run {
    tac("b.java")

    git("mv -f b.java c.java")

    val changes = assertChangesWithRefresh {
      rename("b.java", "c.java")
    }

    commit(changes)

    assertNoChanges()
    repo.assertCommitted {
      rename("b.java", "c.java")
    }
  }
}


private class MyExplicitMovementProvider : GitCheckinExplicitMovementProvider() {
  override fun isEnabled(project: Project): Boolean = true

  override fun getDescription(): String = "explicit movement in tests"

  override fun getCommitMessage(originalCommitMessage: String): String = description

  override fun collectExplicitMovements(
    project: Project,
    beforePaths: MutableList<FilePath>,
    afterPaths: MutableList<FilePath>,
  ): MutableCollection<Movement> {
    val beforeMap = beforePaths.filter { it.name.endsWith(".before") }.associateBy { it.name.removeSuffix(".before") }

    val afterMap = afterPaths.filter { it.name.endsWith(".after") }.associateBy { it.name.removeSuffix(".after") }

    val movedChanges = ArrayList<Movement>()
    for (key in (beforeMap.keys + afterMap.keys)) {
      val beforePath = beforeMap[key] ?: continue
      val afterPath = afterMap[key] ?: continue
      movedChanges.add(Movement(beforePath, afterPath))
    }

    return movedChanges
  }
}
