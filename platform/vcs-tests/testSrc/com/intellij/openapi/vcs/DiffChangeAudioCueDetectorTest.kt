// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.diff.audioCues.DiffAudioCueProvider
import com.intellij.diff.audioCues.DiffAudioCues
import com.intellij.diff.audioCues.DiffChangeAudioCueDetector
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * The changes here are built with [DiffDrawUtil.createHighlighter], the same production call every diff and
 * merge viewer goes through, so these cases break if the attributes it attaches or the markup model it uses
 * change.
 */
@TestApplication
@Timeout(30)
class DiffChangeAudioCueDetectorTest {
  private val detector = DiffChangeAudioCueDetector()

  @Test
  fun `the diff provider publishes valid cues`() {
    val cues = DiffAudioCueProvider().audioCues

    assertThat(cues.map { it.id })
      .containsExactly("diff.line.inserted", "diff.line.deleted", "diff.line.modified", "diff.line.conflict")
    assertThat(cues).allSatisfy { cue ->
      assertThat(cue.title).isNotBlank()
      val sound = cue.ownerClass.classLoader.getResourceAsStream(cue.resourcePath)
      assertThat(sound).describedAs("sound of '%s'", cue.id).isNotNull()
      assertThat(sound!!.use { it.read() }).isNotEqualTo(-1)
    }
  }

  @Test
  fun `an inserted change yields the inserted cue`() = withEditor { editor ->
    addChange(editor, line = 1, TextDiffType.INSERTED)

    assertThat(detect(editor, 1, LINE_1_START)).containsExactly(DiffAudioCues.LINE_INSERTED)
  }

  @Test
  fun `a deleted change yields the deleted cue`() = withEditor { editor ->
    // a deletion is a real (non-empty) range on the side that still shows the removed lines
    addChange(editor, line = 1, TextDiffType.DELETED)

    assertThat(detect(editor, 1, LINE_1_START)).containsExactly(DiffAudioCues.LINE_DELETED)
  }

  @Test
  fun `a modified change yields the modified cue`() = withEditor { editor ->
    addChange(editor, line = 1, TextDiffType.MODIFIED)

    assertThat(detect(editor, 1, LINE_1_START)).containsExactly(DiffAudioCues.LINE_MODIFIED)
  }

  @Test
  fun `a conflict yields the conflict cue`() = withEditor { editor ->
    addChange(editor, line = 1, TextDiffType.CONFLICT)

    assertThat(detect(editor, 1, LINE_1_START)).containsExactly(DiffAudioCues.LINE_CONFLICT)
  }

  @Test
  fun `only the changed lines are reported`() = withEditor { editor ->
    addChange(editor, line = 1, TextDiffType.INSERTED)
    addChange(editor, line = 3, TextDiffType.DELETED)

    assertThat(detect(editor, 0, LINE_0_START)).isEmpty()
    assertThat(detect(editor, 2, LINE_2_START)).isEmpty()
  }

  @Test
  fun `adjacent changes of different types on one line yield both cues`() = withEditor { editor ->
    addChange(editor, line = 1, TextDiffType.INSERTED)
    addChange(editor, line = 1, TextDiffType.CONFLICT)

    assertThat(detect(editor, 1, LINE_1_START))
      .containsExactlyInAnyOrder(DiffAudioCues.LINE_INSERTED, DiffAudioCues.LINE_CONFLICT)
  }

  @Test
  fun `a resolved merge change is silent`() = withEditor { editor ->
    // PaintMode.RESOLVED has background NONE, so DiffDrawUtil attaches no attributes at all — the only reason
    // the detector stays silent here
    DiffDrawUtil.createHighlighter(editor, 1, 2, TextDiffType.MODIFIED, false, true, false, false, false)

    assertThat(detect(editor, 1, LINE_1_START)).isEmpty()
  }

  @Test
  fun `an empty range is silent`() = withEditor { editor ->
    // an empty range marks a change that has no lines on this side; it also gets no attributes
    DiffDrawUtil.createHighlighter(editor, 1, 1, TextDiffType.INSERTED, false)

    assertThat(detect(editor, 1, LINE_1_START)).isEmpty()
  }

  @Test
  fun `an inline word fragment is silent`() = withEditor { editor ->
    // inline fragments carry the same DiffTextAttributes but use EXACT_RANGE, so only the target area
    // distinguishes them from a line-level change
    DiffDrawUtil.createInlineHighlighter(editor, LINE_1_START + 1, LINE_1_START + 3, TextDiffType.MODIFIED)

    assertThat(detect(editor, 1, LINE_1_START + 2)).isEmpty()
  }

  @Test
  fun `changes in a main editor are ignored`() = withEditor(EditorKind.MAIN_EDITOR) { editor ->
    addChange(editor, line = 1, TextDiffType.INSERTED)

    assertThat(detect(editor, 1, LINE_1_START)).isEmpty()
  }

  private fun addChange(editor: Editor, line: Int, type: TextDiffType) {
    DiffDrawUtil.createHighlighter(editor, line, line + 1, type, false)
  }

  private fun detect(editor: Editor, line: Int, caretOffset: Int) =
    detector.detect(editor, line, caretOffset).mapTo(HashSet()) { it.cue }

  private fun withEditor(kind: EditorKind = EditorKind.DIFF, body: (Editor) -> Unit) = timeoutRunBlocking {
    val project = projectFixture.get()
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument(TEXT), project, kind)
        try {
          body(editor)
        }
        finally {
          factory.releaseEditor(editor)
        }
      }
    }
  }

  private companion object {
    val projectFixture = projectFixture()

    /** Four lines of five characters each: line N starts at 6N. */
    const val TEXT: String = "line0\nline1\nline2\nline3"
    const val LINE_0_START: Int = 0
    const val LINE_1_START: Int = 6
    const val LINE_2_START: Int = 12
  }
}
