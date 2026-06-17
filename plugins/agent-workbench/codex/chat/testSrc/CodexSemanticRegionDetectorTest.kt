// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.chat

import com.intellij.agent.workbench.chat.AgentChatSemanticRegion
import com.intellij.agent.workbench.chat.AgentChatSemanticRegionKind
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.view.TerminalOutputModelSnapshot
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexSemanticRegionDetectorTest {
  @Test
  fun extractsSingleProposedPlanSummary(): Unit = timeoutRunBlocking {
    val snapshot = createSnapshot(
      """
      intro
      • Proposed Plan
       
        # Semantic Navigation
        1. Detect plan regions
        2. Add markers
       
      outro
      """.trimIndent(),
    )

    val region = detectCodexRegions(snapshot).single()

    assertThat(region.kind).isEqualTo(AgentChatSemanticRegionKind.PROPOSED_PLAN)
    assertThat(region.summary).isEqualTo("Semantic Navigation")
    assertThat(extractMatchedText(snapshot, region))
      .contains("\u2022 Proposed Plan")
      .contains("Semantic Navigation")
  }

  @Test
  fun ignoresHeaderWithoutContent(): Unit = timeoutRunBlocking {
    val snapshot = createSnapshot(
      """
      • Proposed Plan
      """.trimIndent(),
    )

    assertThat(detectCodexRegions(snapshot)).isEmpty()
  }

  @Test
  fun keepsMultiplePlansOrderedAndStable(): Unit = timeoutRunBlocking {
    val snapshot = createSnapshot(
      """
      • Proposed Plan
        First plan
       
      chatter
      • Proposed Plan
        First plan
       
      • Proposed Plan
        Third plan
       
      """.trimIndent(),
    )

    val regions = detectCodexRegions(snapshot)

    assertThat(regions.map(AgentChatSemanticRegion::summary))
      .containsExactly("First plan", "First plan", "Third plan")
    val firstHash = regions[0].id.substringBefore(':')
    val secondHash = regions[1].id.substringBefore(':')
    val thirdHash = regions[2].id.substringBefore(':')
    assertThat(firstHash).isEqualTo(secondHash)
    assertThat(regions[0].id.substringAfter(':')).isEqualTo("1")
    assertThat(regions[1].id.substringAfter(':')).isEqualTo("2")
    assertThat(thirdHash).isNotEqualTo(firstHash)
    assertThat(regions[2].id.substringAfter(':')).isEqualTo("1")
  }

  @Test
  fun extractsUpdatedPlan(): Unit = timeoutRunBlocking {
    val snapshot = createSnapshot(
      """
      • Updated Plan
        └ Step one
          Step two
       
      """.trimIndent(),
    )

    val region = detectCodexRegions(snapshot).single()

    assertThat(region.kind).isEqualTo(AgentChatSemanticRegionKind.UPDATED_PLAN)
    assertThat(region.summary).isEqualTo("Step one")
  }

  @Test
  fun mixesProposedAndUpdatedPlansInOrder(): Unit = timeoutRunBlocking {
    val snapshot = createSnapshot(
      """
      • Proposed Plan
        Implement feature
       
      • Updated Plan
        └ Step 1 done
       
      """.trimIndent(),
    )

    val regions = detectCodexRegions(snapshot)

    assertThat(regions).hasSize(2)
    assertThat(regions[0].kind).isEqualTo(AgentChatSemanticRegionKind.PROPOSED_PLAN)
    assertThat(regions[1].kind).isEqualTo(AgentChatSemanticRegionKind.UPDATED_PLAN)
    assertThat(regions[0].startOffset).isLessThan(regions[1].startOffset)
  }
}

private fun detectCodexRegions(snapshot: TerminalOutputModelSnapshot): List<AgentChatSemanticRegion> {
  return CodexSemanticRegionDetector.detect(snapshot)
}

private suspend fun createSnapshot(text: String): TerminalOutputModelSnapshot {
  val model = MutableTerminalOutputModelImpl(EditorFactory.getInstance().createDocument(""), 0)
  return withContext(Dispatchers.EDT) {
    CommandProcessor.getInstance().runUndoTransparentAction {
      runWriteAction {
        model.updateContent(0, text, emptyList())
      }
    }
    model.takeSnapshot()
  }
}

private fun extractMatchedText(snapshot: TerminalOutputModelSnapshot, region: AgentChatSemanticRegion): String {
  val startOffset = snapshot.startOffset + region.startOffset.toLong()
  val endOffset = snapshot.startOffset + region.endOffset.toLong()
  return snapshot.getText(startOffset, endOffset).toString()
}
