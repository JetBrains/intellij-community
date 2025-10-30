package org.jetbrains.plugins.gitlab.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import git4idea.repo.GitRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import java.io.File
import java.nio.file.Path

class GitLabUIUtilTest : LightPlatformTestCase() {
  private val gitRoot = "/tmp/git-repo"

  private lateinit var gitRepository: GitRepository
  private lateinit var gitRootVf: VirtualFile

  private val baseUrl = "http://base/url/uploads/"

  override fun setUp() {
    super.setUp()

    gitRootVf = mockk {
      every { toNioPath() } returns Path.of(gitRoot)
    }
    gitRepository = mockk {
      every { root } returns gitRootVf
    }
  }

  private fun @NlsSafe String.substituteSeparators(): String = replace(File.separator, "/")

  // https://youtrack.jetbrains.com/issue/IJPL-148576
  fun `test link with query does not throw an exception`() {
    assertNoThrowable {
      GitLabUIUtil.convertToHtml(
        project, gitRepository, GitLabProjectPath("test-account", "mr-test"), """
        [link](/some/invalid/file/path?query=123)
      """.trimIndent(), baseUrl)
    }
  }

  fun `test link with starting with project path redirects to browser (for now)`() {
    val parsed = convertToHtml("""
        [link](/test-account/mr-test/-/merge_requests/1)
      """.trimIndent())

    assertThat(parsed).contains("href=\"/test-account/mr-test/-/merge_requests/1\"")
  }

  fun `test simple file link gets file link prefix`() {
    val parsed = convertToHtml("""
        [link](bla.md)
      """.trimIndent())

    assertThat(parsed.substituteSeparators()).contains("${GitLabUIUtil.OPEN_FILE_LINK_PREFIX}${gitRoot}/bla.md")
  }

  fun `test nested file link gets file link prefix`() {
    val parsed = convertToHtml("""
        [link](directory/a/b/bla.md)
      """.trimIndent())

    assertThat(parsed.substituteSeparators()).contains("${GitLabUIUtil.OPEN_FILE_LINK_PREFIX}${gitRoot}/directory/a/b/bla.md")
  }

  fun `test nested file link with backslashes gets file link prefix`() {
    val parsed = convertToHtml("""
        [link](directory\a\b\bla.md)
      """.trimIndent())

    assertThat(parsed.substituteSeparators()).contains("${GitLabUIUtil.OPEN_FILE_LINK_PREFIX}${gitRoot}/directory/a/b/bla.md")
  }

  fun `test uploads files link rendering`() {
    val parsed = convertToHtml("""
        [link](/uploads/a/b/c.jpg) some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><a href="${baseUrl}a/b/c.jpg" title="link">link</a> some text</p></body>""")
  }

  fun `test simple MR link gets MR link prefix`() {
    val parsed = convertToHtml("""
        !53
      """.trimIndent())

    assertThat(parsed).contains("${GitLabUIUtil.OPEN_MR_LINK_PREFIX}53")
  }

  fun `test images rendering`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg)
      """.trimIndent())

    assertThat(parsed.substituteSeparators()).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /></p></body>""")
  }

  fun `test images rendering with one setting`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /></p></body>""")
  }

  fun `test images rendering with one percent setting`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10%}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /></p></body>""")
  }


  fun `test images rendering with two settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /></p></body>""")
  }

  fun `test images rendering with two precent settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10% height=10%}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /></p></body>""")
  }

  fun `test images rendering with many settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10 other=123 other2=123}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /></p></body>""")
  }

  fun `test images rendering with many percent settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10% height=10% other=123% other2=123%}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /></p></body>""")
  }

  fun `test images rendering with different settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10% other=123 other2=123%}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /></p></body>""")
  }

  fun `test images rendering with two double quote settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width="10" height="10"}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /></p></body>""")
  }

  fun `test images rendering with two double quote settings and some text`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width="10" height="10"} some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /> some text</p></body>""")
  }

  fun `test images rendering with different settings and some text`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10% other=123 other2=123%} some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /> some text</p></body>""")
  }

  fun `test images rendering with settings and additional curly braces section`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10} {here is the additional section}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" /> {here is the additional section}</p></body>""")
  }

  fun `test images rendering with settings and immediate symbols after curly braces`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10}immediate text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" />immediate text</p></body>""")
  }

  fun `test images rendering with settings and immediate curly braces after curly braces section`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10}{here is the additional section}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><img src="/uploads/a/b/c.jpg" alt="link" />{here is the additional section}</p></body>""")
  }

  fun `test images rendering with settings reference link`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The picture ![image][link]
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The picture <img src="http://url" alt="image" title="label" /></p></body>""")
  }

  fun `test images rendering with settings reference link with settings`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The picture ![image][link]{width=10 height=10}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The picture <img src="http://url" alt="image" title="label" /></p></body>""")
  }

  fun `test images rendering with settings reference link with settings and immediate text after`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The picture ![image][link]{width=10 height=10}immediate text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The picture <img src="http://url" alt="image" title="label" />immediate text</p></body>""")
  }

  fun `test images rendering with settings reference link and text`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The picture ![image][link] some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The picture <img src="http://url" alt="image" title="label" /> some text</p></body>""")
  }

  fun `test images rendering with settings reference link with settings and text`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The picture ![image][link]{width=10 height=10} some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The picture <img src="http://url" alt="image" title="label" /> some text</p></body>""")
  }


  fun `test full reference link with absolute web url`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The link [link-description][link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="http://url" title="label">link-description</a> and some text</p></body>""")
  }

  fun `test full reference link for uploads file`() {
    val parsed = convertToHtml("""
        [link]: /uploads/some/path.pdf "label"
        The link [link-description][link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="http://base/url/uploads/some/path.pdf" title="label">link-description</a> and some text</p></body>""")
  }

  fun `test full reference link for local file`() {
    val parsed = convertToHtml("""
        [link]: /local/file/some/path.pdf "label"
        The link [link-description][link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="glfilelink:\local\file\some\path.pdf" title="label">link-description</a> and some text</p></body>""")
  }

  fun `test short reference link with absolute web url`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The link [link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="http://url" title="label">link</a> and some text</p></body>""")
  }

  fun `test short reference link for uploads file`() {
    val parsed = convertToHtml("""
        [link]: /uploads/some/path.pdf "label"
        The link [link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="http://base/url/uploads/some/path.pdf" title="label">link</a> and some text</p></body>""")
  }

  fun `test short reference link for local file`() {
    val parsed = convertToHtml("""
        [link]: /local/file/some/path.pdf "label"
        The link [link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="glfilelink:\local\file\some\path.pdf" title="label">link</a> and some text</p></body>""")
  }

  private fun convertToHtml(markdownSource: String): @NlsSafe String {
    val parsed = GitLabUIUtil.convertToHtml(
      project, gitRepository, GitLabProjectPath("test-account", "mr-test"), markdownSource, baseUrl)
    return parsed
  }
}