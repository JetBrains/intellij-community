// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.ide.audioCues.AudioCue
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.vcs.ex.LineStatusTrackerAudioCueDetector
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.ex.VcsAudioCueProvider
import com.intellij.openapi.vcs.ex.VcsAudioCues

class LineStatusTrackerAudioCueDetectorTest : BaseLineStatusTrackerTestCase() {
  private val detector = LineStatusTrackerAudioCueDetector()

  fun `test the VCS provider publishes valid cues`() {
    val cues = VcsAudioCueProvider().audioCues

    assertEquals(listOf("vcs.gutter.inserted", "vcs.gutter.deleted", "vcs.gutter.modified"), cues.map { it.id })
    for (cue in cues) {
      assertTrue(cue.title.isNotBlank())
      val sound = checkNotNull(cue.ownerClass.classLoader.getResourceAsStream(cue.resourcePath)) { "No sound for '${cue.id}'" }
      assertTrue(sound.use { it.read() } != -1)
    }
  }

  fun `test an inserted line yields the inserted cue`() = test("A_B_C_", "A_C_") {
    // "B" is present locally but not in the base revision
    withEditor { editor ->
      assertEquals(setOf(VcsAudioCues.GUTTER_INSERTED), detect(editor, line = 1))
    }
  }

  fun `test a deleted line yields the deleted cue`() = test("A_C_", "A_B_C_") {
    // "B" was removed locally; the range is empty, and getRangesForLines matches it on its line1
    withEditor { editor ->
      assertEquals(setOf(VcsAudioCues.GUTTER_DELETED), detect(editor, line = 1))
    }
  }

  fun `test a modified line yields the modified cue`() = test("A_X_C_", "A_B_C_") {
    withEditor { editor ->
      assertEquals(setOf(VcsAudioCues.GUTTER_MODIFIED), detect(editor, line = 1))
    }
  }

  fun `test an unchanged line is silent`() = test("A_B_C_", "A_C_") {
    withEditor { editor ->
      assertEquals(emptySet<AudioCue>(), detect(editor, line = 0))
    }
  }

  fun `test a hidden gutter is silent`() = test("A_B_C_", "A_C_") {
    withEditor { editor ->
      editor.settings.isLineMarkerAreaShown = false

      assertEquals(emptySet<AudioCue>(), detect(editor, line = 1))
    }
  }

  fun `test a non-main editor is silent`() = test("A_B_C_", "A_C_") {
    withEditor(EditorKind.CONSOLE) { editor ->
      assertEquals(emptySet<AudioCue>(), detect(editor, line = 1))
    }
  }

  fun `test an invisible tracker is silent`() = test("A_B_C_", "A_C_") {
    withTrackerHidden {
      withEditor { editor ->
        assertEquals(emptySet<AudioCue>(), detect(editor, line = 1))
      }
    }
  }

  private fun detect(editor: Editor, line: Int): Set<AudioCue> =
    detector.detect(editor, line, editor.document.getLineStartOffset(line)).mapTo(HashSet()) { it.cue }

  private fun TrackerModificationsTest.withTrackerHidden(body: () -> Unit) {
    val previousMode = tracker.mode
    tracker.mode = LocalLineStatusTracker.Mode(false, previousMode.showErrorStripeMarkers, previousMode.detectWhitespaceChangedLines)
    try {
      body()
    }
    finally {
      tracker.mode = previousMode
    }
  }

  /** `withOpenedEditor` in the base class creates no [Editor], only a tracker, so one is built here over the same document. */
  private fun TrackerModificationsTest.withEditor(kind: EditorKind = EditorKind.MAIN_EDITOR, body: (Editor) -> Unit) {
    val factory = EditorFactory.getInstance()
    val editor = factory.createEditor(document, project, kind)
    try {
      body(editor)
    }
    finally {
      factory.releaseEditor(editor)
    }
  }
}
