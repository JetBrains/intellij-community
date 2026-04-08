// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.view.TerminalOutputModelSnapshot
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.junit.jupiter.api.Test

@TestApplication
class CodexTuiPatchFoldControllerTest {
  @Test
  fun singleFileSmallPatchDoesNotProduceFoldMatch(): Unit = timeoutRunBlocking {
    val snapshot = createSnapshot(
      """
      • Edited foo.txt (+1 -0)
          1 +hello
      """.trimIndent(),
    )

    assertThat(detectCodexTuiPatchBlocks(snapshot)).isEmpty()
  }

  @Test
  fun singleFileLargePatchProducesFoldMatch(): Unit = timeoutRunBlocking {
    val snapshot = createSnapshot(
      """
      • Edited foo.txt (+5 -4)
          1 -one
          1 +one changed
          2 -two
          2 +two changed
          3 -three
          3 +three changed
          4 -four
          4 +four changed
          5 +five
      """.trimIndent(),
    )

    val match = detectCodexTuiPatchBlocks(snapshot).single()

    assertThat(match.headerText).isEqualTo("• Edited foo.txt (+5 -4)")
    assertThat(match.fileCount).isEqualTo(1)
    assertThat(match.added).isEqualTo(5)
    assertThat(match.removed).isEqualTo(4)
    assertThat(match.lineCount).isEqualTo(10)
  }

  @Test
  fun multiFilePatchStopsBeforeNextTopLevelItem(): Unit = timeoutRunBlocking {
    val snapshot = createSnapshot(
      """
      intro
      • Edited 2 files (+2 -1)
        └ a.txt (+1 -1)
          1 -one
          1 +one changed

        └ b.txt (+1 -0)
          1 +new
      Done applying changes
      """.trimIndent(),
    )

    val match = detectCodexTuiPatchBlocks(snapshot).single()
    val matchedText = extractMatchedText(snapshot, match)

    assertThat(match.fileCount).isEqualTo(2)
    assertThat(matchedText).contains("└ a.txt (+1 -1)")
    assertThat(matchedText).contains("└ b.txt (+1 -0)")
    assertThat(matchedText).doesNotContain("Done applying changes")
  }

  @Test
  fun proseBulletIsIgnored(): Unit = timeoutRunBlocking {
    val snapshot = createSnapshot(
      """
      • Added more context for the next step
      continuing normally
      """.trimIndent(),
    )

    assertThat(detectCodexTuiPatchBlocks(snapshot)).isEmpty()
  }

  @Test
  fun foldStateKeepsUserExpansionAcrossSameMatchRebuild(): Unit = timeoutRunBlocking {
    val text = """
• Edited foo.txt (+5 -4)
    1 -one
    1 +one changed
    2 -two
    2 +two changed
    3 -three
    3 +three changed
    4 -four
    4 +four changed
    5 +five
""".trimIndent()
    val match = detectCodexTuiPatchBlocks(createSnapshot(text)).single()
    val foldState = CodexTuiPatchFoldState()
    val editor = createViewer(text)
    try {
      withContext(Dispatchers.EDT) {
        // Initial apply: block is collapsed (custom fold region)
        foldState.apply(editor, listOf(match))
        assertThat(editor.foldingModel.allFoldRegions).hasSize(1)
        assertThat(editor.foldingModel.allFoldRegions[0]).isInstanceOf(CustomFoldRegion::class.java)

        // Toggle to expanded: fold region removed, inlay added
        foldState.toggleBlock(editor, match.contentHash)
        assertThat(editor.foldingModel.allFoldRegions).isEmpty()
        val inlays = editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength)
        assertThat(inlays).hasSize(1)

        // Re-apply with same match: should stay expanded
        foldState.apply(editor, listOf(match))
        assertThat(editor.foldingModel.allFoldRegions).isEmpty()
        assertThat(editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength)).hasSize(1)

        // Clear removes everything
        foldState.clear()
        assertThat(editor.foldingModel.allFoldRegions).isEmpty()
        assertThat(editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength)).isEmpty()
      }
    }
    finally {
      releaseEditor(editor)
    }
  }

  @Test
  @RegistryKey(key = CODEX_TUI_PATCH_FOLDING_REGISTRY_KEY, value = "true")
  fun installCheckHonorsRegistryKey() {
    assertThat(shouldInstallAgentChatPatchFolding(AgentSessionProvider.CODEX)).isTrue()
    assertThat(shouldInstallAgentChatPatchFolding(AgentSessionProvider.CLAUDE)).isFalse()
    assertThat(shouldInstallAgentChatPatchFolding(null)).isFalse()
  }
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

private fun extractMatchedText(snapshot: TerminalOutputModelSnapshot, match: CodexTuiPatchBlockMatch): String {
  val startOffset = snapshot.startOffset + match.startOffset.toLong()
  val endOffset = snapshot.startOffset + match.endOffset.toLong()
  return snapshot.getText(startOffset, endOffset).toString()
}
