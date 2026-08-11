// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.ui.table.links.CommitLinksProvider
import com.intellij.vcs.log.ui.table.links.NavigateToCommit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@TestApplication
class CommitPresentationUtilTest {

  private val projectFixture = projectFixture()

  @Test
  fun `subject without prefix links is rendered in bold`(@TestDisposable disposable: Disposable) {
    val text = render(message = "Plain subject", links = emptyList(), disposable = disposable)
    assertEquals("<b>Plain subject</b>", text)
  }

  @Test
  fun `single prefix link is wrapped inside bold subject`(@TestDisposable disposable: Disposable) {
    val text = render(
      message = "fixup! Real subject",
      links = listOf(prefixLink(0, 6, FIXUP_TARGET)),
      disposable = disposable,
    )
    assertEquals("""<b><a href="go-to-hash:$FIXUP_TARGET">fixup!</a> Real subject</b>""", text)
  }

  @Test
  fun `multiple prefix links are wrapped independently`(@TestDisposable disposable: Disposable) {
    val squashTarget = "2222222222222222222222222222222222222222"
    val text = render(
      message = "fixup! squash! Original",
      links = listOf(
        prefixLink(0, 6, FIXUP_TARGET),
        prefixLink(7, 14, squashTarget),
      ),
      disposable = disposable,
    )
    assertEquals(
      """<b><a href="go-to-hash:$FIXUP_TARGET">fixup!</a> <a href="go-to-hash:$squashTarget">squash!</a> Original</b>""",
      text,
    )
  }

  @Test
  fun `description is separated from subject and newlines become br`(@TestDisposable disposable: Disposable) {
    val text = render(
      message = "fixup! Subject\n\nLine 1\nLine 2",
      links = listOf(prefixLink(0, 6, FIXUP_TARGET)),
      disposable = disposable,
    )
    assertEquals(
      """<b><a href="go-to-hash:$FIXUP_TARGET">fixup!</a> Subject</b><br/><br/>Line 1<br/>Line 2""",
      text,
    )
  }

  @Test
  fun `subject spanning multiple lines is rendered as plain text without bold`(@TestDisposable disposable: Disposable) {
    val text = render(
      message = "Subject line 1\nSubject line 2\n\nDescription",
      links = emptyList(),
      disposable = disposable,
    )
    assertEquals("Subject line 1<br/>Subject line 2<br/><br/>Description", text)
  }

  private fun render(message: String, links: List<NavigateToCommit>, disposable: Disposable): String {
    val project = projectFixture.get()
    val commitId = CommitId(object : Hash {
      override fun asString(): String = "0000000000000000000000000000000000000000"
      override fun toShortString(): String = "0000000"
    }, LightVirtualFile())

    val provider = mock<CommitLinksProvider> {
      on { getLinks(commitId) } doReturn links
    }
    project.replaceService(CommitLinksProvider::class.java, provider, disposable)
    return CommitPresentationUtil.CommitPresentation(project, commitId, message, "", MultiMap.empty()).text
  }

  private fun prefixLink(start: Int, end: Int, target: String): NavigateToCommit =
    NavigateToCommit(TextRange(start, end), target)

  companion object {
    private const val FIXUP_TARGET = "1111111111111111111111111111111111111111"
  }
}
