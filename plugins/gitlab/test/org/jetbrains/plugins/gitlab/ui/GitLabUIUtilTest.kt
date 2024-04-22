// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import git4idea.repo.GitRepository
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class GitLabUIUtilTest : LightPlatformTestCase() {
  private val gitRoot = "/tmp/git-repo"

  private lateinit var gitRepository: GitRepository
  private lateinit var gitRootVf: VirtualFile

  override fun setUp() {
    super.setUp()

    gitRootVf = mock {
      whenever(mock.toNioPath()).thenReturn(Path.of(gitRoot))
    }
    gitRepository = mock {
      whenever(mock.root).thenReturn(gitRootVf)
    }
  }

  // https://youtrack.jetbrains.com/issue/IJPL-148576
  fun `test link with query does not throw an exception`() {
    assertNoThrowable {
      GitLabUIUtil.convertToHtml(
        project, gitRepository, """
        [link](/some/invalid/file/path?query=123)
      """.trimIndent())
    }
  }

  fun `test simple file link gets file link prefix`() {
    val parsed = GitLabUIUtil.convertToHtml(
      project, gitRepository, """
        [link](bla.md)
      """.trimIndent())

    assertThat(parsed).contains("${GitLabUIUtil.OPEN_FILE_LINK_PREFIX}${gitRoot}/bla.md")
  }

  fun `test nested file link gets file link prefix`() {
    val parsed = GitLabUIUtil.convertToHtml(
      project, gitRepository, """
        [link](directory/a/b/bla.md)
      """.trimIndent())

    assertThat(parsed).contains("${GitLabUIUtil.OPEN_FILE_LINK_PREFIX}${gitRoot}/directory/a/b/bla.md")
  }

  fun `test nested file link with backslashes gets file link prefix`() {
    val parsed = GitLabUIUtil.convertToHtml(
      project, gitRepository, """
        [link](directory\a\b\bla.md)
      """.trimIndent())

    assertThat(parsed).contains("${GitLabUIUtil.OPEN_FILE_LINK_PREFIX}${gitRoot}/directory/a/b/bla.md")
  }

  fun `test simple MR link gets MR link prefix`() {
    val parsed = GitLabUIUtil.convertToHtml(
      project, gitRepository, """
        !53
      """.trimIndent())

    assertThat(parsed).contains("${GitLabUIUtil.OPEN_MR_LINK_PREFIX}53")
  }
}