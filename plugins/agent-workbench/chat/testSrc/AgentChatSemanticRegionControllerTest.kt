// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentChatSemanticRegionControllerTest {
  @Test
  fun semanticRegionStateAddsMarkersAndWrapsNavigation(): Unit = timeoutRunBlocking {
    val text = """
intro
• Proposed Plan
  First proposal
 
between
• Proposed Plan
  Second proposal
 
tail
""".trimIndent()
    val regions = buildTestRegions(text)
    val state = AgentChatSemanticRegionState()
    val navigator = AgentChatSemanticRegionNavigator { state }
    val editor = createViewer(text)
    try {
      withContext(Dispatchers.EDT) {
        state.apply(editor, regions)

        assertThat(editor.markupModel.allHighlighters.filter { it.isValid }).hasSize(2)
        assertThat(navigator.hasNextOccurence()).isTrue()
        assertThat(navigator.hasPreviousOccurence()).isTrue()

        val first = checkNotNull(navigator.goNextOccurence())
        assertThat(editor.caretModel.offset).isEqualTo(regions[0].startOffset)
        assertThat(first.occurenceNumber).isEqualTo(1)
        assertThat(first.occurencesCount).isEqualTo(2)

        val second = checkNotNull(navigator.goNextOccurence())
        assertThat(editor.caretModel.offset).isEqualTo(regions[1].startOffset)
        assertThat(second.occurenceNumber).isEqualTo(2)

        val wrapped = checkNotNull(navigator.goNextOccurence())
        assertThat(editor.caretModel.offset).isEqualTo(regions[0].startOffset)
        assertThat(wrapped.occurenceNumber).isEqualTo(1)

        val previous = checkNotNull(navigator.goPreviousOccurence())
        assertThat(editor.caretModel.offset).isEqualTo(regions[1].startOffset)
        assertThat(previous.occurenceNumber).isEqualTo(2)

        state.clear()
        assertThat(editor.markupModel.allHighlighters.filter { it.isValid }).isEmpty()
      }
    }
    finally {
      releaseEditor(editor)
    }
  }
}

private fun buildTestRegions(text: String): List<AgentChatSemanticRegion> {
  val firstStart = text.indexOf("• Proposed Plan")
  val secondStart = text.indexOf("• Proposed Plan", startIndex = firstStart + 1)
  return listOf(
    buildTestRegion(id = "first", text = text, startOffset = firstStart, startLine = 1),
    buildTestRegion(id = "second", text = text, startOffset = secondStart, startLine = 5),
  )
}

private fun buildTestRegion(id: String, text: String, startOffset: Int, startLine: Int): AgentChatSemanticRegion {
  val endOffset = text.indexOf('\n', startIndex = startOffset).takeIf { it >= 0 } ?: text.length
  return AgentChatSemanticRegion(
    id = id,
    kind = AgentChatSemanticRegionKind.PROPOSED_PLAN,
    summary = id,
    startOffset = startOffset,
    endOffset = endOffset,
    startLine = startLine,
    endLine = startLine,
  )
}

private suspend fun createViewer(text: String): Editor {
  return withContext(Dispatchers.EDT) {
    EditorFactory.getInstance().createViewer(EditorFactory.getInstance().createDocument(text))
  }
}

private suspend fun releaseEditor(editor: Editor) {
  withContext(Dispatchers.EDT) {
    if (!editor.isDisposed) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
  }
}
