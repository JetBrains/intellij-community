// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.cd
import git4idea.test.cloneRepo
import git4idea.test.gitPlatformContextFixture
import git4idea.test.initRepo
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

@TestApplication
internal class GitBareWorkTreeTest {

  private val contextFixture = gitPlatformContextFixture().gitWorkTreeFixture {
    val sourceRepo = testNioRoot.resolve("source")
    Files.createDirectories(testNioRoot)
    initRepo(project, sourceRepo, true)

    val mainDir = testNioRoot.resolve("main.git")
    cloneRepo(project, sourceRepo.toString(), mainDir.toString(), true)
    mainDir
  }
  private val context: GitWorkTreeContext get() = contextFixture.get()

  // IDEA-151598
  @Test
  fun `test current revision`(): Unit = with(context) {
    cd(repo)
    val hash = tac("file.txt")
    repo.update()

    assertThat(repo.currentRevision).describedAs("Current revision identified incorrectly").isEqualTo(hash)
  }
}
