// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table.links

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.IssueNavigationLink
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.test.vcsPlatformContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

@TestApplication
class VcsLinksRendererTest {
  private val fixture = vcsPlatformContextFixture()
  private val project get() = fixture.get().project
  private val projectRoot get() = fixture.get().projectRoot

  private lateinit var linksRenderer: VcsLinksRenderer

  @Test
  fun `test single prefix match`() {
    val text = "fixup! some text"
    val linksProvider = Mockito.mock(CommitLinksProvider::class.java)
    val emptyCommitId = CommitId(EMPTY_HASH, projectRoot)

    Mockito.`when`(linksProvider.getLinks(emptyCommitId))
      .thenReturn(listOf("fixup!".toLink()))
    val textComponent = SimpleColoredComponent()
    linksRenderer = VcsLinksRenderer(project, textComponent, linksProvider)

    linksRenderer.appendTextWithLinks(text, REGULAR_ATTRIBUTES, emptyCommitId)

    assertThat(textComponent.fragmentCount).isEqualTo(2)
    assertThat(textComponent.toString()).isEqualTo(text)
  }

  @Test
  fun `test multiple prefix match`() {
    val text = "fixup! fixup! squash! some text"
    val emptyCommitId = CommitId(EMPTY_HASH, projectRoot)
    val linksProvider = Mockito.mock(CommitLinksProvider::class.java)

    Mockito.`when`(linksProvider.getLinks(emptyCommitId))
      .thenReturn(listOf("fixup!".toLink(), "fixup!".toLink(7), "squash!".toLink(14)))
    val textComponent = SimpleColoredComponent()
    linksRenderer = VcsLinksRenderer(project, textComponent, linksProvider)

    linksRenderer.appendTextWithLinks(text, REGULAR_ATTRIBUTES, emptyCommitId)

    assertThat(textComponent.fragmentCount).isEqualTo(6)
    assertThat(textComponent.toString()).isEqualTo(text)
  }

  @Test
  fun `test multiple prefix with issue links match`() {
    val text = "fixup! fixup! squash! some IDEA-1234 text IDEA-4567"
    val linksProvider = Mockito.mock(CommitLinksProvider::class.java)
    val emptyCommitId = CommitId(EMPTY_HASH, projectRoot)
    IssueNavigationConfiguration.getInstance(project).links = listOf(
      IssueNavigationLink("\\b[A-Z]+\\-\\d+\\b", "http://example.com/$0")
    )
    Mockito.`when`(linksProvider.getLinks(emptyCommitId))
      .thenReturn(listOf("fixup!".toLink(), "fixup!".toLink(7), "squash!".toLink(14)))
    val textComponent = SimpleColoredComponent()
    linksRenderer = VcsLinksRenderer(project, textComponent, linksProvider)

    linksRenderer.appendTextWithLinks(text, REGULAR_ATTRIBUTES, emptyCommitId)

    assertThat(textComponent.fragmentCount).isEqualTo(9)
    assertThat(textComponent.toString()).isEqualTo(text)
  }

  companion object {
    private val EMPTY_HASH = object : Hash {
      override fun asString(): String = ""

      override fun toShortString(): String = ""
    }

    private fun String.toLink(offset: Int = 0) = NavigateToCommit(TextRange.from(offset, length), "")
  }
}
