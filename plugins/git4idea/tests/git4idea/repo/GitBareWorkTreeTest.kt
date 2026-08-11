// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.GitPlatformTestContext
import git4idea.test.cd
import git4idea.test.cloneRepo
import git4idea.test.initRepo
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
internal class GitBareWorkTreeTest : GitWorkTreeBaseTest() {

  override fun GitPlatformTestContext.initMainRepo(): Path {
    val sourceRepo = testNioRoot.resolve("source")
    Files.createDirectories(testNioRoot)
    initRepo(project, sourceRepo, true)

    val mainDir = testNioRoot.resolve("main.git")
    cloneRepo(project, sourceRepo.toString(), mainDir.toString(), true)
    return mainDir
  }

  // IDEA-151598
  @Test
  fun `test current revision`(): Unit = with(context) {
    cd(myRepo)
    val hash = tac("file.txt")
    myRepo.update()

    assertThat(myRepo.currentRevision).describedAs("Current revision identified incorrectly").isEqualTo(hash)
  }
}
