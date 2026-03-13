package org.jetbrains.plugins.gitlab.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import git4idea.repo.GitRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.ui.GitLabMarkdownToHtmlConverter.Companion.OPEN_FILE_LINK_PREFIX
import org.jetbrains.plugins.gitlab.ui.GitLabMarkdownToHtmlConverter.Companion.OPEN_MR_LINK_PREFIX
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath.Companion.extractProjectPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Path

private const val BASE_URL = """http://base/url"""
private const val IMAGES_API_BASE = """$BASE_URL/api/v4/projects/1"""
private const val WEB_BASE = """$BASE_URL/-/project/1"""
private const val P_CLASS = """class="custom_image""""
private const val FILE_LINK = """glfilelink"""
private const val MERGE_REQUEST = """glmergerequest"""

@TestApplication
class GitLabMarkdownToHtmlConverterTest {
  companion object {
    private val fixture = projectFixture()
  }

  private val project = fixture.get()
  private val gitRoot = "/tmp/git-repo"

  private lateinit var gitRepository: GitRepository
  private lateinit var gitRootVf: VirtualFile

  @BeforeEach
  fun setUp() {
    gitRootVf = mockk {
      every { toNioPath() } returns Path.of(gitRoot)
    }
    gitRepository = mockk {
      every { root } returns gitRootVf
    }
  }

  // https://youtrack.jetbrains.com/issue/IJPL-148576
  @Test
  fun `test link with query does not throw an exception`() {
    assertDoesNotThrow {
      convertToHtml("""
        [link](/some/invalid/file/path?query=123)
      """.trimIndent())
    }
  }

  @Test
  fun `test link with starting with project path redirects to browser (for now)`() {
    val parsed = convertToHtml("""
        [link](/test-account/mr-test/-/merge_requests/1)
      """.trimIndent())

    assertThat(parsed).contains("href=\"/test-account/mr-test/-/merge_requests/1\"")
  }

  @Test
  fun `test simple file link gets file link prefix`() {
    val parsed = convertToHtml("""
        [link](bla.md)
      """.trimIndent())

    assertThat(parsed).contains("${OPEN_FILE_LINK_PREFIX}${gitRoot}/bla.md")
  }

  @Test
  fun `test nested file link gets file link prefix`() {
    val parsed = convertToHtml("""
        [link](directory/a/b/bla.md)
      """.trimIndent())

    assertThat(parsed).contains("${OPEN_FILE_LINK_PREFIX}${gitRoot}/directory/a/b/bla.md")
  }

  @Test
  fun `test nested file link with backslashes gets file link prefix`() {
    val parsed = convertToHtml("""
        [link](directory\a\b\bla.md)
      """.trimIndent())

    assertThat(parsed).contains("${OPEN_FILE_LINK_PREFIX}${gitRoot}/directory/a/b/bla.md")
  }

  @Test
  fun `test uploads files link rendering`() {
    val parsed = convertToHtml("""
        [link](/uploads/a/b/c.jpg) some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><a href="$WEB_BASE/uploads/a/b/c.jpg" title="link">link</a> some text</p></body>""")
  }

  @Test
  fun `test simple MR link gets MR link prefix`() {
    val parsed = convertToHtml("""
        !53
      """.trimIndent())

    assertThat(parsed).contains("${OPEN_MR_LINK_PREFIX}53")
  }

  @Test
  fun `test images rendering`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg)
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with one setting`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with one percent setting`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10%}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with two settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with two precent settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10% height=10%}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with many settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10 other=123 other2=123}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with many percent settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10% height=10% other=123% other2=123%}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with different settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10% other=123 other2=123%}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with two double quote settings`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width="10" height="10"}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with two double quote settings and some text`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width="10" height="10"} some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p> some text</p></body>""")
  }

  @Test
  fun `test images rendering with different settings and some text`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10% other=123 other2=123%} some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p> some text</p></body>""")
  }

  @Test
  fun `test images rendering with settings and additional curly braces section`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10} {here is the additional section}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p> {here is the additional section}</p></body>""")
  }

  @Test
  fun `test images rendering with settings and immediate symbols after curly braces`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10}immediate text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p>immediate text</p></body>""")
  }

  @Test
  fun `test images rendering with settings and immediate curly braces after curly braces section`() {
    val parsed = convertToHtml("""
        ![link](/uploads/a/b/c.jpg){width=10 height=10}{here is the additional section}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p><p $P_CLASS><a href="$WEB_BASE/uploads/a/b/c.jpg"><img src="$IMAGES_API_BASE/uploads/a/b/c.jpg" alt="link" title="link" /></a></p>{here is the additional section}</p></body>""")
  }

  @Test
  fun `test images rendering with settings reference link`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The picture ![image][link]
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The picture <p $P_CLASS><a href="http://url"><img src="http://url" alt="image" title="label" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with settings reference link with settings`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The picture ![image][link]{width=10 height=10}
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The picture <p $P_CLASS><a href="http://url"><img src="http://url" alt="image" title="label" /></a></p></p></body>""")
  }

  @Test
  fun `test images rendering with settings reference link with settings and immediate text after`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The picture ![image][link]{width=10 height=10}immediate text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The picture <p $P_CLASS><a href="http://url"><img src="http://url" alt="image" title="label" /></a></p>immediate text</p></body>""")
  }

  @Test
  fun `test images rendering with settings reference link and text`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The picture ![image][link] some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The picture <p $P_CLASS><a href="http://url"><img src="http://url" alt="image" title="label" /></a></p> some text</p></body>""")
  }

  @Test
  fun `test images rendering with settings reference link with settings and text`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The picture ![image][link]{width=10 height=10} some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The picture <p $P_CLASS><a href="http://url"><img src="http://url" alt="image" title="label" /></a></p> some text</p></body>""")
  }


  @Test
  fun `test full reference link with absolute web url`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The link [link-description][link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="http://url" title="label">link-description</a> and some text</p></body>""")
  }

  @Test
  fun `test full reference link for uploads file`() {
    val parsed = convertToHtml("""
        [link]: /uploads/some/path.pdf "label"
        The link [link-description][link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="$WEB_BASE/uploads/some/path.pdf" title="label">link-description</a> and some text</p></body>""")
  }

  @Test
  fun `test full reference link for local file`() {
    val parsed = convertToHtml("""
        [link]: /local/file/some/path.pdf "label"
        The link [link-description][link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="$FILE_LINK:/local/file/some/path.pdf" title="label">link-description</a> and some text</p></body>""")
  }

  @Test
  fun `test full reference link for local file with backslashes`() {
    val parsed = convertToHtml("""
        [link]: win\local\file\some\path.pdf "label"
        The link [link-description][link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo(
      """<body><p>The link <a href="$FILE_LINK:$gitRoot/win/local/file/some/path.pdf" title="label">link-description</a> and some text</p></body>"""
    )
  }

  @Test
  fun `test short reference link with absolute web url`() {
    val parsed = convertToHtml("""
        [link]: http://url "label"
        The link [link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="http://url" title="label">link</a> and some text</p></body>""")
  }

  @Test
  fun `test short reference link for uploads file`() {
    val parsed = convertToHtml("""
        [link]: /uploads/some/path.pdf "label"
        The link [link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="$WEB_BASE/uploads/some/path.pdf" title="label">link</a> and some text</p></body>""")
  }

  @Test
  fun `test short reference link for local file`() {
    val parsed = convertToHtml("""
        [link]: /local/file/some/path.pdf "label"
        The link [link] and some text
      """.trimIndent())

    assertThat(parsed).isEqualTo("""<body><p>The link <a href="$FILE_LINK:/local/file/some/path.pdf" title="label">link</a> and some text</p></body>""")
  }

  @Test
  fun `test MR id`() {
    val parsed = convertToHtml("Hello !123, how are you?")
    assertThat(parsed).isEqualTo("""<body><p>Hello <a href="$MERGE_REQUEST:123">!123</a>, how are you?</p></body>""")
  }

  @Test
  fun `test MR id with space`() {
    val parsed = convertToHtml("Hello ! 123, how are you?")
    assertThat(parsed).isEqualTo("""<body><p>Hello ! 123, how are you?</p></body>""")
  }

  @Test
  fun `test many MR ids`() {
    val parsed = convertToHtml("Hello !123, !456, how are you?")
    assertThat(parsed).isEqualTo("""<body><p>Hello <a href="$MERGE_REQUEST:123">!123</a>, <a href="$MERGE_REQUEST:456">!456</a>, how are you?</p></body>""")
  }

  @Test
  fun `test MR link with letters`() {
    val parsed = convertToHtml("!53abc")
    assertThat(parsed).isEqualTo("""<body><p><a href="${OPEN_MR_LINK_PREFIX}53">!53</a>abc</p></body>""")
  }

  @Test
  fun `test MR link with prefix letters`() {
    val parsed = convertToHtml("abc!53")
    assertThat(parsed).isEqualTo("""<body><p>abc<a href="${OPEN_MR_LINK_PREFIX}53">!53</a></p></body>""")
  }

  @Test
  fun `test MR link and mentions which is in the same PSI element`() {
    val parsed = convertToHtml("!53 MR id,@mention is also here ")
    assertThat(parsed).isEqualTo("""<body><p><a href="${OPEN_MR_LINK_PREFIX}53">!53</a> MR id,<a href="$BASE_URL/mention">@mention</a> is also here</p></body>""")
  }

  @Test
  fun `test MR link with preceding comma`() {
    val parsed = convertToHtml("Here is some text,!53")
    assertThat(parsed).isEqualTo("""<body><p>Here is some text,<a href="${OPEN_MR_LINK_PREFIX}53">!53</a></p></body>""")
  }

  @Test
  fun `test single username mention in one line`() {
    val parsed = convertToHtml("Hello @username, how are you?")
    assertThat(parsed).isEqualTo("""<body><p>Hello <a href="$BASE_URL/username">@username</a>, how are you?</p></body>""")
  }

  @Test
  fun `test one-letter username mention in one line`() {
    val parsed = convertToHtml("Hello @u, how are you?")
    assertThat(parsed).isEqualTo("""<body><p>Hello <a href="$BASE_URL/u">@u</a>, how are you?</p></body>""")
  }

  @Test
  fun `test multiple username mentions in one line`() {
    val parsed = convertToHtml("Thanks @john_doe and @jane-smith for the review!")
    assertThat(parsed).isEqualTo("""<body><p>Thanks <a href="$BASE_URL/john_doe">@john_doe</a> and <a href="$BASE_URL/jane-smith">@jane-smith</a> for the review!</p></body>""")
  }

  @Test
  fun `test username mentions in multiple lines`() {
    val parsed = convertToHtml("""
      Hi @alice,

      Could you and @bob review this?

      Thanks!
    """.trimIndent())
    assertThat(parsed).isEqualTo("""<body><p>Hi <a href="$BASE_URL/alice">@alice</a>,</p><p>Could you and <a href="$BASE_URL/bob">@bob</a> review this?</p><p>Thanks!</p></body>""")
  }

  @Test
  fun `test valid username with dots dashes and underscores`() {
    val parsed = convertToHtml("Please review @user.name-123_test")
    assertThat(parsed).isEqualTo("""<body><p>Please review <a href="$BASE_URL/user.name-123_test">@user.name-123_test</a></p></body>""")
  }

  @Test
  fun `test username ending with dot should not be treated as mention`() {
    val parsed = convertToHtml("This @invalid. should not be a valid mention")
    assertThat(parsed).isEqualTo("""<body><p>This <a href="$BASE_URL/invalid">@invalid</a>. should not be a valid mention</p></body>""")
  }

  @Test
  fun `test username mention combined with markdown`() {
    val parsed = convertToHtml("**@username** mentioned in [link](file.md)")
    assertThat(parsed).isEqualTo("""<body><p><strong><a href="$BASE_URL/username">@username</a></strong> mentioned in <a href="$FILE_LINK:$gitRoot/file.md" title="link">link</a></p></body>""")
  }

  @Test
  fun `test inline code element`() {
    val parsed = convertToHtml("This is `inline code` in text")
    assertThat(parsed).isEqualTo("""<body><p>This is <code>inline code</code> in text</p></body>""")
  }

  @Test
  fun `test multiple inline code elements`() {
    val parsed = convertToHtml("Use `method()` to call `function()` here")
    assertThat(parsed).isEqualTo("""<body><p>Use <code>method()</code> to call <code>function()</code> here</p></body>""")
  }

  @Test
  fun `test code block with backticks`() {
    val parsed = convertToHtml("""
      ```
      code block
      ```
    """.trimIndent())
    assertThat(parsed).isEqualTo("""
      <body><pre><code>
      code block
      </code></pre></body>
    """.trimIndent())
  }

  @Test
  fun `test code block with language specification`() {
    // for correct highlighting, the language should be registered in Language.getRegisteredLanguages()
    val parsed = convertToHtml("""
      ```xml
      <message>Hello World</message>
      ```
    """.trimIndent())
    assertThat(parsed).isEqualTo("""
      <body><pre><code class="language-xml">&lt;<span style="color:#000080">message</span>&gt;Hello World&lt;/<span style="color:#000080">message</span>&gt;
      </code></pre></body>
      """.trimIndent())
  }

  @Test
  fun `test inline code does not resolve links`() {
    val parsed = convertToHtml("Use `<tag>` and `@annotation` and `!1` in code")
    assertThat(parsed).isEqualTo(
      """<body><p>Use <code>&lt;tag&gt;</code> and <code>@annotation</code> and <code>!1</code> in code</p></body>""")
  }

  @Test
  fun `test code block with multiple lines`() {
    val parsed = convertToHtml("""
      ```
      line 1
      line 2
      line 3
      ```
    """.trimIndent())
    assertThat(parsed).isEqualTo("""
      <body><pre><code>
      line 1
      line 2
      line 3
      </code></pre></body>
      """.trimIndent())
  }

  @Test
  fun `test code block does not process inner markdown`() {
    val parsed = convertToHtml("""
      ```
      !1 is not a link
      @mention is not a link
      **not bold**
      [not link](file.md)
      ```
    """.trimIndent())
    assertThat(parsed).isEqualTo("""
      <body><pre><code>
      !1 is not a link
      @mention is not a link
      **not bold**
      [not link](file.md)
      </code></pre></body>
    """.trimIndent())
  }

  @Test
  fun `test bold, italic, crossed`() {
    val parsed = convertToHtml("Test **bold** _italic_ ~~crossed~~ and ~~_**all together**_~~")
    assertThat(parsed).isEqualTo(
      """<body><p>Test <strong>bold</strong> <em>italic</em> <strike>crossed</strike> and <strike><em><strong>all together</strong></em></strike></p></body>"""
    )
  }

  private fun convertToHtml(markdownSource: String): @NlsSafe String {
    val serverPath = GitLabServerPath(BASE_URL)
    val projectId = "1"
    val projectFullPath = extractProjectPath("test-account/mr-test") ?: error("Failed to extract project path")
    val converter = GitLabMarkdownToHtmlConverter(project, gitRepository, serverPath, projectId, projectFullPath)
    return converter.convertToHtml(markdownSource)
  }
}