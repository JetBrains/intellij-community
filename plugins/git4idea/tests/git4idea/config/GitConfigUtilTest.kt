// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitUtil
import git4idea.test.GitPlatformTestContext
import git4idea.test.gitInit
import git4idea.test.gitPlatformContextFixture
import git4idea.test.isolateGitConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.StandardOpenOption

@TestApplication
internal class GitConfigUtilTest {
  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()

  @BeforeEach
  fun setUp(): Unit = with(context) {
    isolateGitConfig()
    createTestRepository()
    cd(projectPath)
  }

  private fun GitPlatformTestContext.createTestRepository() {
    Files.createDirectories(projectNioRoot)
    cd(projectNioRoot)
    gitInit(project)
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectNioRoot.resolve(GitUtil.DOT_GIT))!!
  }

  @Test
  fun `test getValues reads all values for same key ans store them in insertion order`(): Unit = with(context) {
    writeConfig("""
      [user]
        name = Alice
        name = Bob
        email = alice@example.com
      [user]
        name = Carl
        email = carl@example.com
      """.trimIndent())

    val values = GitConfigUtil.getValues(project, projectNioRoot, null)
    val resultUserName = values["user.name"]
    assertThat(resultUserName).containsExactly("Alice", "Bob", "Carl")

    val resultUserEmail = values["user.email"]
    assertThat(resultUserEmail).containsExactly("alice@example.com", "carl@example.com")
  }

  @Test
  fun `test order of entries in git config values corresponds to the insertion order`(): Unit = with(context) {
    writeConfig("""
        [url "https://gitlab.com/group/"]
          insteadOf = test1:
        [url "ssh://git@gitlab.com/group/"]
          pushInsteadOf = test2:
        [url "ssh://git@gitlab.com:group2/"]
          pushInsteadOf = test3:
      """.trimIndent())

    val values = GitConfigUtil.getValues(project, projectNioRoot, null)
    // `git init` writes `core.*` keys, and the IDE passes more keys on the command line.
    val orderedListOfKeys = values.keys.filter { it.startsWith("url.") }

    assertThat(orderedListOfKeys).containsExactly(
      "url.https://gitlab.com/group/.insteadof",
      "url.ssh://git@gitlab.com/group/.pushinsteadof",
      "url.ssh://git@gitlab.com:group2/.pushinsteadof",
    )

    assertThat(values["url.https://gitlab.com/group/.insteadof"]).containsExactly("test1:")
    assertThat(values["url.ssh://git@gitlab.com/group/.pushinsteadof"]).containsExactly("test2:")
    assertThat(values["url.ssh://git@gitlab.com:group2/.pushinsteadof"]).containsExactly("test3:")
  }

  private fun GitPlatformTestContext.writeConfig(content: String) {
    val file = projectNioRoot.resolve(".git").resolve("config")
    Files.writeString(file, content + "\n", StandardOpenOption.APPEND)
    assertThat(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)).isNotNull()
  }
}