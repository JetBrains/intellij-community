// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.testFramework.junit5.TestApplication
import git4idea.commands.Git
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.makeCommit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

@TestApplication
internal class GitCloneLongPathsTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `test clone repo with long paths`(): Unit = with(context) {
    git("config core.longpaths true")
    val path = Path("a".repeat(100), "b".repeat(100), "c".repeat(100), "test.txt") // 260+
    makeCommit(path.pathString)

    val cloned = projectNioRoot.resolve("cloned")
    val cloneResult = Git.getInstance().clone(project,
                                              cloned.parent,
                                              "file://${repo.root.path}",
                                              cloned.name)

    assertThat(cloneResult.success()).isTrue()
  }
}
