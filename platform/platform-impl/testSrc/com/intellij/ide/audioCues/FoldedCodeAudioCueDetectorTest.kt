// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Pins the [FoldingModelEx][com.intellij.openapi.editor.ex.FoldingModelEx] offset semantics that
 * [FoldedCodeAudioCueDetector] relies on. None of them are guaranteed by documentation, and a change in any
 * makes the folding cue go silent (or fire everywhere) without any other symptom.
 */
@TestApplication
@Timeout(30)
class FoldedCodeAudioCueDetectorTest {
  private val detector = FoldedCodeAudioCueDetector()

  @Test
  fun `collapsed region spanning the line is reported for that line`() = withFoldingEditor { editor ->
    collapse(editor, LINE_1_START, LINE_2_END)

    assertThat(detect(editor, line = 1, caretOffset = LINE_0_START)).containsExactly(IdeAudioCues.FOLDED_LINE)
  }

  @Test
  fun `expanded region is not reported`() = withFoldingEditor { editor ->
    addRegion(editor, LINE_1_START, LINE_2_END)

    assertThat(detect(editor, line = 1, caretOffset = LINE_0_START)).isEmpty()
  }

  @Test
  fun `custom fold region is not reported, not even under the caret`() = withFoldingEditor { editor ->
    // rendered doc comments, notebook separators: deliberately silent, they are not folded code
    val region = onEdt { EditorTestUtil.addCustomFoldRegion(editor, 1, 2) }
    assertThat(region).isNotNull()

    assertThat(detect(editor, line = 1, caretOffset = LINE_0_START)).isEmpty()
    // the caret boundaries the touch-inclusive overlap scan matches: the region start, and one past its end
    assertThat(detect(editor, line = 1, caretOffset = region!!.startOffset)).isEmpty()
    assertThat(detect(editor, line = 2, caretOffset = region.endOffset)).isEmpty()
  }

  @Test
  fun `region ending exactly at the line start is reported for that line`() = withFoldingEditor { editor ->
    // touch-inclusive overlap: the region swallowed the preceding line break, so its placeholder renders here
    collapse(editor, LINE_0_START, LINE_1_START)

    assertThat(detect(editor, line = 1, caretOffset = LINE_3_START)).containsExactly(IdeAudioCues.FOLDED_LINE)
  }

  @Test
  fun `region ending at the previous line's last character is not reported`() = withFoldingEditor { editor ->
    collapse(editor, LINE_0_START, LINE_0_END)

    assertThat(detect(editor, line = 1, caretOffset = LINE_3_START)).isEmpty()
  }

  @Test
  fun `caret at the region start offset is caret-scoped`() = withFoldingEditor { editor ->
    collapse(editor, LINE_1_START, LINE_2_END)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START))
      .containsExactlyInAnyOrder(IdeAudioCues.FOLDED_LINE, IdeAudioCues.FOLDED_CARET)
  }

  @Test
  fun `caret exactly at the region end offset is caret-scoped`() = withFoldingEditor { editor ->
    // the caret cannot rest inside a collapsed region, so parking right after it is the common case
    collapse(editor, LINE_1_START, LINE_2_END)

    assertThat(detect(editor, line = 2, caretOffset = LINE_2_END))
      .containsExactlyInAnyOrder(IdeAudioCues.FOLDED_LINE, IdeAudioCues.FOLDED_CARET)
  }

  @Test
  fun `a folded region nested in a wider one is reported at the caret off the EDT`() = withFoldingEditor { editor ->
    // getCollapsedRegionAtOffset only ever returned the *outermost* region; the overlap scan sees every one, so a
    // caret at an inner region's start is cued even when a wider region starts elsewhere. Off the EDT because that
    // is how the manager calls it: FoldRegionsTree's cached top-level data is rebuilt on the EDT only.
    collapse(editor, LINE_0_START, LINE_3_START)
    collapse(editor, LINE_1_START, LINE_2_END)

    assertThat(detectInBackground(editor, line = 1, caretOffset = LINE_1_START))
      .containsExactlyInAnyOrder(IdeAudioCues.FOLDED_LINE, IdeAudioCues.FOLDED_CARET)
  }

  private suspend fun detect(editor: Editor, line: Int, caretOffset: Int): Set<AudioCue> =
    onEdt { detector.detect(editor, line, caretOffset).mapTo(HashSet()) { it.cue } }

  /** How the manager calls it: a background read action, where the EDT-only folding caches are unavailable. */
  private suspend fun detectInBackground(editor: Editor, line: Int, caretOffset: Int): Set<AudioCue> =
    withContext(Dispatchers.Default) {
      readAction {
        check(!ApplicationManager.getApplication().isDispatchThread) { "the detection did not leave the EDT" }
        detector.detect(editor, line, caretOffset).mapTo(HashSet()) { it.cue }
      }
    }

  private suspend fun addRegion(editor: Editor, start: Int, end: Int): FoldRegion = onEdt {
    var region: FoldRegion? = null
    editor.foldingModel.runBatchFoldingOperation { region = editor.foldingModel.addFoldRegion(start, end, "...") }
    requireNotNull(region) { "fold region $start..$end was rejected" }
  }

  private suspend fun collapse(editor: Editor, start: Int, end: Int) {
    val region = addRegion(editor, start, end)
    onEdt { editor.foldingModel.runBatchFoldingOperation { region.isExpanded = false } }
  }

  private fun withFoldingEditor(body: suspend (Editor) -> Unit) = timeoutRunBlocking {
    val factory = EditorFactory.getInstance()
    val editor = onEdt { factory.createEditor(factory.createDocument(TEXT)) }
    try {
      body(editor)
    }
    finally {
      onEdt { factory.releaseEditor(editor) }
    }
  }

  private suspend fun <T> onEdt(action: () -> T): T = withContext(Dispatchers.EDT) { writeIntentReadAction { action() } }

  private companion object {
    /** Four lines of five characters each: line N starts at 6N and ends at 6N + 5. */
    const val TEXT: String = "line0\nline1\nline2\nline3"

    const val LINE_0_START: Int = 0
    const val LINE_0_END: Int = 5
    const val LINE_1_START: Int = 6
    const val LINE_2_END: Int = 17
    const val LINE_3_START: Int = 18
  }
}
