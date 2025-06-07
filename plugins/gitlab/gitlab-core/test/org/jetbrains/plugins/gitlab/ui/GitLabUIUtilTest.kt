package org.jetbrains.plugins.gitlab.ui

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import git4idea.repo.GitRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import java.nio.file.Path

class GitLabUIUtilTest : LightPlatformTestCase() {
  private val gitRoot = "/tmp/git-repo"

  private lateinit var gitRepository: GitRepository
  private lateinit var gitRootVf: VirtualFile

  override fun setUp() {
    super.setUp()

    gitRootVf = mockk {
      every { toNioPath() } returns Path.of(gitRoot)
    }
    gitRepository = mockk {
      every { root } returns gitRootVf
    }
  }

  // https://youtrack.jetbrains.com/issue/IJPL-148576
  fun `test link with query does not throw an exception`() {
    assertNoThrowable {
      GitLabUIUtil.convertToHtml(
        project, gitRepository, GitLabProjectPath("test-account", "mr-test"), """
        [link](/some/invalid/file/path?query=123)
      """.trimIndent())
    }
  }

  fun `test link with starting with project path redirects to browser (for now)`() {
    val parsed = GitLabUIUtil.convertToHtml(
      project, gitRepository, GitLabProjectPath("test-account", "mr-test"), """
        [link](/test-account/mr-test/-/merge_requests/1)
      """.trimIndent())

    assertThat(parsed).contains("href=\"/test-account/mr-test/-/merge_requests/1\"")
  }

  fun `test simple file link gets file link prefix`() {
    val parsed = GitLabUIUtil.convertToHtml(
      project, gitRepository, GitLabProjectPath("test-account", "mr-test"), """
        [link](bla.md)
      """.trimIndent())

    assertThat(parsed).contains("${GitLabUIUtil.OPEN_FILE_LINK_PREFIX}${gitRoot}/bla.md")
  }

  fun `test nested file link gets file link prefix`() {
    val parsed = GitLabUIUtil.convertToHtml(
      project, gitRepository, GitLabProjectPath("test-account", "mr-test"), """
        [link](directory/a/b/bla.md)
      """.trimIndent())

    assertThat(parsed).contains("${GitLabUIUtil.OPEN_FILE_LINK_PREFIX}${gitRoot}/directory/a/b/bla.md")
  }

  fun `test nested file link with backslashes gets file link prefix`() {
    val parsed = GitLabUIUtil.convertToHtml(
      project, gitRepository, GitLabProjectPath("test-account", "mr-test"), """
        [link](directory\a\b\bla.md)
      """.trimIndent())

    assertThat(parsed).contains("${GitLabUIUtil.OPEN_FILE_LINK_PREFIX}${gitRoot}/directory/a/b/bla.md")
  }

  fun `test simple MR link gets MR link prefix`() {
    val parsed = GitLabUIUtil.convertToHtml(
      project, gitRepository, GitLabProjectPath("test-account", "mr-test"), """
        !53
      """.trimIndent())

    assertThat(parsed).contains("${GitLabUIUtil.OPEN_MR_LINK_PREFIX}53")
  }
}