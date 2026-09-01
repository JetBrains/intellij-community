// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.ide.audioCues.EditorAudioCue
import com.intellij.ide.audioCues.EditorAudioCueDetector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import org.jetbrains.annotations.ApiStatus
import java.util.BitSet

@ApiStatus.Internal
class LineStatusTrackerAudioCueDetector : EditorAudioCueDetector {
  private val gutterInserted = EditorAudioCue(VcsAudioCues.GUTTER_INSERTED)
  private val gutterDeleted = EditorAudioCue(VcsAudioCues.GUTTER_DELETED)
  private val gutterModified = EditorAudioCue(VcsAudioCues.GUTTER_MODIFIED)

  override fun detect(editor: Editor, line: Int, caretOffset: Int): Set<EditorAudioCue> {
    if (editor.editorKind != EditorKind.MAIN_EDITOR || !editor.settings.isLineMarkerAreaShown) return emptySet()
    val project = editor.project?.takeUnless { it.isDisposed } ?: return emptySet()

    val tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.document) ?: return emptySet()
    if (!tracker.isValid()) return emptySet()
    if ((tracker as? LocalLineStatusTracker<*>)?.mode?.isVisible == false) return emptySet()

    val ranges = tracker.getRangesForLines(BitSet().also { it.set(line) }) ?: return emptySet()
    val result = mutableSetOf<EditorAudioCue>()
    for (range in ranges) {
      when (range.type) {
        Range.INSERTED -> result += gutterInserted
        Range.DELETED -> result += gutterDeleted
        Range.MODIFIED -> result += gutterModified
        else -> {}
      }
    }
    return result
  }
}
