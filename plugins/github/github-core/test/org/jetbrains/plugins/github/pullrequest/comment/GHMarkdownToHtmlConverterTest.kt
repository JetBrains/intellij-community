// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.jetbrains.plugins.github.api.GithubServerPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class GHMarkdownToHtmlConverterTest {

  private companion object {
    val projectFixture = projectFixture()
  }

  val serverPath = GithubServerPath("github.com")
  val project = projectFixture.get()

  @Test
  fun testConvertMarkdown() {

    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      "This is a [link](https://github.com/JetBrains/intellij-community) to GitHub repository"
    )
    assertEquals(
      """<body><p>This is a <a href="https://github.com/JetBrains/intellij-community">link</a> to GitHub repository</p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithPullRequestId() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      "This is a #1 pull request link to GitHub repository"
    )
    assertEquals(
      """<body><p>This is a <a href="ghpullrequest:1">#1</a> pull request link to GitHub repository</p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithMention() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      "This is a @mention pull request link to GitHub repository", serverPath
    )
    assertEquals(
      """<body><p>This is a <a href="https://github.com/mention">@mention</a> pull request link to GitHub repository</p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithMultipleMentions() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      "This are many mentions: @mention1 @mention2 @mention3", serverPath
    )
    assertEquals(
      "<body><p>This are many mentions: " +
      "<a href=\"https://github.com/mention1\">@mention1</a> " +
      "<a href=\"https://github.com/mention2\">@mention2</a> " +
      "<a href=\"https://github.com/mention3\">@mention3</a>" +
      "</p></body>",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithMultipleMentionsAndPunctuation() {
    val convertMarkdown =
      GHMarkdownToHtmlConverter(project).convertMarkdown("This are many mentions:@mention1;@mention2,@mention3.", serverPath)
    assertEquals(
      "<body><p>This are many mentions:" +
      "<a href=\"https://github.com/mention1\">@mention1</a>;" +
      "<a href=\"https://github.com/mention2\">@mention2</a>," +
      "<a href=\"https://github.com/mention3\">@mention3</a>." +
      "</p></body>",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithInvalidMentionFormat() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      "This is a not@mention pull request link to GitHub repository", serverPath
    )
    assertEquals(
      """<body><p>This is a not@mention pull request link to GitHub repository</p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithMentionAtStart() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      "@mention is in the beginning", serverPath
    )
    assertEquals(
      """<body><p><a href="https://github.com/mention">@mention</a> is in the beginning</p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithMentionAtEnd() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      "Mention is in the end @mention", serverPath
    )
    assertEquals(
      """<body><p>Mention is in the end <a href="https://github.com/mention">@mention</a></p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithMentionInMultipleParagraphs() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      """hello
        |
        |here is @mention
        |
        |bye""".trimMargin(), serverPath
    )
    assertEquals(
      """<body><p>hello</p><p>here is <a href="https://github.com/mention">@mention</a></p><p>bye</p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithMultipleMentionsInMultipleParagraphs() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      """hello
        |
        |here is first @mention1
        |
        |here is second @mention2
        |
        |bye""".trimMargin(), serverPath
    )
    assertEquals(
      """<body><p>hello</p><p>here is first <a href="https://github.com/mention1">@mention1</a></p><p>here is second <a href="https://github.com/mention2">@mention2</a></p><p>bye</p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithCodeBlock() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      """
        ```java
        some code
        ```
        here is my suggestion
      """.trimIndent()
    )

    val expected =
      """
        <body><pre><code class="language-java">some code
        </code></pre><p>here is my suggestion</p></body>
      """.trimIndent()
    assertEquals(expected, convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithAtSignInCodeBlock() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      """
        ```java
        some code with @Annotation
        ```
        here is my suggestion
      """.trimIndent()
    )

    val expected =
      """
        <body><pre><code class="language-java">some code with @Annotation
        </code></pre><p>here is my suggestion</p></body>
      """.trimIndent()
    assertEquals(expected, convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithAtSignInInlineCode() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      """Here is `@Annotation` which is not a mention"""
    )

    val expected =
      """<body><p>Here is <code>@Annotation</code> which is not a mention</p></body>"""
    assertEquals(expected, convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithAtSignInLink() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdown(
      """Here is [@link](@url) which is not a mention"""
    )

    val expected =
      """<body><p>Here is <a href="@url">@link</a> which is not a mention</p></body>"""
    assertEquals(expected, convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithSuggestedChange() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdownWithSuggestedChange(
      "This is a [link](https://github.com/JetBrains/intellij-community) to GitHub repository",
      filePath = "file.txt", reviewContent = "content", server = null
    )
    assertEquals(
      """<body><p>This is a <a href="https://github.com/JetBrains/intellij-community">link</a> to GitHub repository</p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithSuggestedChangeWithPullRequestId() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdownWithSuggestedChange(
      "This is a #1 pull request link to GitHub repository",
      filePath = "file.txt", reviewContent = "content", server = null
    )
    assertEquals(
      """<body><p>This is a <a href="ghpullrequest:1">#1</a> pull request link to GitHub repository</p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithSuggestedChangeWithMention() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdownWithSuggestedChange(
      "This is a @mention pull request link to GitHub repository",
      filePath = "file.txt", reviewContent = "content", server = serverPath
    )
    assertEquals(
      """<body><p>This is a <a href="https://github.com/mention">@mention</a> pull request link to GitHub repository</p></body>""",
      convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithSuggestedChangeWithCodeBlock() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdownWithSuggestedChange(

      """
        ```java
        some code
        ```
        here is my suggestion
      """.trimIndent(),
      filePath = "file.txt", reviewContent = "content", server = null
    )

    val expected =
      """
        <body><pre><code class="language-java"><pre style="background-color: #d6d6d6; 
        margin: 0;
        padding: 2 2;">content</pre><pre style="background-color: #bee6be; 
        margin: 0;
        padding: 2 2;">some code</pre></code></pre><p>here is my suggestion</p></body>
      """.trimIndent()
    assertEquals(expected, convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithSuggestedChangeWithAtSignInCodeBlock() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdownWithSuggestedChange(
      """
        ```java
        some code with @Annotation
        ```
        here is my suggestion
      """.trimIndent(),
      filePath = "file.txt", reviewContent = "content", server = null
    )

    val expected =
      """
        <body><pre><code class="language-java"><pre style="background-color: #d6d6d6; 
        margin: 0;
        padding: 2 2;">content</pre><pre style="background-color: #bee6be; 
        margin: 0;
        padding: 2 2;">some code with @Annotation</pre></code></pre><p>here is my suggestion</p></body>
      """.trimIndent()
    assertEquals(expected, convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithSuggestedChangeWithAtSignInInlineCode() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdownWithSuggestedChange(
      """Here is `@Annotation` which is not a mention""",
      filePath = "file.txt", reviewContent = "content", server = null
    )

    val expected =
      """<body><p>Here is <code>@Annotation</code> which is not a mention</p></body>"""
    assertEquals(expected, convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithSuggestedChangeWithAtSignInLink() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdownWithSuggestedChange(
      """Here is [@link](@url) which is not a mention""",
      filePath = "file.txt", reviewContent = "content", server = null
    )

    val expected =
      """<body><p>Here is <a href="@url">@link</a> which is not a mention</p></body>"""
    assertEquals(expected, convertMarkdown)
  }

  @Test
  fun testConvertMarkdownWithSuggestedWithSuggestion() {
    val convertMarkdown = GHMarkdownToHtmlConverter(project).convertMarkdownWithSuggestedChange(
      """
        ```suggestion
        some code
        ```
        here is my suggestion
      """.trimIndent(),
      filePath = "file.txt", reviewContent = "content", server = null
    )

    val expected =
      """
        <body><pre><code class="language-suggestion"><pre style="background-color: #d6d6d6; 
        margin: 0;
        padding: 2 2;">content</pre><pre style="background-color: #bee6be; 
        margin: 0;
        padding: 2 2;">some code</pre></code></pre><p>here is my suggestion</p></body>
        """.trimIndent()
    assertEquals(expected, convertMarkdown)
  }
}