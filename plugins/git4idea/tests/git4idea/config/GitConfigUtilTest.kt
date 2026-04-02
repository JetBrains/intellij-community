// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.GitUtil
import git4idea.test.GitPlatformTest
import git4idea.test.gitInit
import java.nio.file.Files
import java.nio.file.StandardOpenOption

internal class GitConfigUtilTest : GitPlatformTest() {

  override fun setUp() {
    super.setUp()
    createTestRepository()
    cd(projectPath)
  }

  private fun createTestRepository() {
    Files.createDirectories(projectNioRoot)
    cd(projectNioRoot.toString())
    gitInit(project)
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectNioRoot.resolve(GitUtil.DOT_GIT))!!
  }

  fun `test getValues reads all values for same key ans store them in insertion order`() {
    writeConfig("""
      [user]
        name = Alice
        name = Bob
        email = alice@example.com
      [user]
        name = Carl
        email = carl@example.com
      """.trimIndent())

    val values = GitConfigUtil.getValues(myProject, projectNioRoot, null)
    val resultUserName = values["user.name"]
    assertNotNull(resultUserName)
    assertContainsOrdered(resultUserName!!, "Alice", "Bob", "Carl")

    val resultUserEmail = values["user.email"]
    assertNotNull(resultUserEmail)
    assertContainsOrdered(resultUserEmail!!, "alice@example.com", "carl@example.com")
  }

  fun `test order of entries in git config values corresponds to the insertion order`() {
    writeConfig("""
        [url "https://gitlab.com/group/"]
          insteadOf = test1:
        [url "ssh://git@gitlab.com/group/"]
          pushInsteadOf = test2:
        [url "ssh://git@gitlab.com:group2/"]
          pushInsteadOf = test3:
      """.trimIndent())

    val values = GitConfigUtil.getValues(myProject, projectNioRoot, null)
    val orderedListOfKeys = values.map { it.key }.toList()

    assertContainsOrdered(orderedListOfKeys,
                          "url.https://gitlab.com/group/.insteadof",
                          "url.ssh://git@gitlab.com/group/.pushinsteadof",
                          "url.ssh://git@gitlab.com:group2/.pushinsteadof")

    assertEquals(values["url.https://gitlab.com/group/.insteadof"], listOf("test1:"))
    assertEquals(values["url.ssh://git@gitlab.com/group/.pushinsteadof"], listOf("test2:"))
    assertEquals(values["url.ssh://git@gitlab.com:group2/.pushinsteadof"], listOf("test3:"))
  }

  private fun writeConfig(content: String) {
    val file = projectNioRoot.resolve(".git").resolve("config")
    Files.writeString(file, content + "\n", StandardOpenOption.APPEND)
    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file))
  }
}