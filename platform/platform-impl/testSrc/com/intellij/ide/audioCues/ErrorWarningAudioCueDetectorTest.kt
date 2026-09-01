// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.awt.Font

/**
 * Highlights are synthesized the way the daemon builds them — a [HighlightInfo] associated with a range
 * highlighter in the document markup model — since running the real daemon would need a language plugin and
 * would not exercise anything more of this detector.
 */
@TestApplication
@Timeout(30)
class ErrorWarningAudioCueDetectorTest {
  private val detector = ErrorWarningAudioCueDetector()
  private val sourceDocuments = mutableListOf<Document>()

  @Test
  fun `error yields the error cues`() = withEditor { editor, project ->
    addHighlight(editor.document, project, HighlightSeverity.ERROR, LINE_1_START + 1, LINE_1_START + 3)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_END)).containsExactly(IdeAudioCues.ERROR_LINE)
    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2))
      .containsExactlyInAnyOrder(IdeAudioCues.ERROR_LINE, IdeAudioCues.ERROR_CARET)
  }

  @Test
  fun `error in a diff editor yields the error cues`() = withEditor(EditorKind.DIFF) { editor, project ->
    addHighlight(editor.document, project, HighlightSeverity.ERROR, LINE_1_START + 1, LINE_1_START + 3)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2))
      .containsExactlyInAnyOrder(IdeAudioCues.ERROR_LINE, IdeAudioCues.ERROR_CARET)
  }

  @Test
  fun `an error copied from another document yields the error cues`() = withEditor(EditorKind.DIFF) { editor, project ->
    copyHighlight(editor, project, HighlightSeverity.ERROR, LINE_1_START + 1, LINE_1_START + 3)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_END)).containsExactly(IdeAudioCues.ERROR_LINE)
    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2))
      .containsExactlyInAnyOrder(IdeAudioCues.ERROR_LINE, IdeAudioCues.ERROR_CARET)
  }

  @Test
  fun `a warning copied from another document yields the warning cues`() = withEditor(EditorKind.DIFF) { editor, project ->
    copyHighlight(editor, project, HighlightSeverity.WARNING, LINE_1_START + 1, LINE_1_START + 3)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2))
      .containsExactlyInAnyOrder(IdeAudioCues.WARNING_LINE, IdeAudioCues.WARNING_CARET)
  }

  @Test
  fun `a copied highlight is caret-scoped by its own range, not the source range`() = withEditor(EditorKind.DIFF) { editor, project ->
    // the unified viewer shifts a copy to the offset the fragment got in its synthetic document
    copyHighlight(editor, project, HighlightSeverity.ERROR, LINE_1_START + 1, LINE_1_START + 3, sourceStart = LINE_3_START)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2))
      .containsExactlyInAnyOrder(IdeAudioCues.ERROR_LINE, IdeAudioCues.ERROR_CARET)
    assertThat(detect(editor, line = 1, caretOffset = LINE_1_END)).containsExactly(IdeAudioCues.ERROR_LINE)
  }

  @Test
  fun `a highlighter without a highlight info is ignored`() = withEditor { editor, project ->
    DocumentMarkupModel.forDocument(editor.document, project, true)
      .addRangeHighlighter(LINE_1_START + 1, LINE_1_START + 3, HighlighterLayer.ERROR, null, HighlighterTargetArea.EXACT_RANGE)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2)).isEmpty()
  }

  @Test
  fun `a file-level highlight is ignored`() = withEditor { editor, project ->
    addFileLevelHighlight(editor.document, project, HighlightSeverity.WARNING)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2)).isEmpty()
    assertThat(detect(editor, line = 3, caretOffset = LINE_3_START)).isEmpty()
  }

  @Test
  fun `a file-level highlight does not mask a line highlight`() = withEditor { editor, project ->
    addFileLevelHighlight(editor.document, project, HighlightSeverity.ERROR)
    addHighlight(editor.document, project, HighlightSeverity.WARNING, LINE_1_START + 1, LINE_1_START + 3)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2))
      .containsExactlyInAnyOrder(IdeAudioCues.WARNING_LINE, IdeAudioCues.WARNING_CARET)
    assertThat(detect(editor, line = 3, caretOffset = LINE_3_START)).isEmpty()
  }

  @Test
  fun `caret exactly at the highlight end offset is caret-scoped`() = withEditor { editor, project ->
    // caret containment goes through RangeHighlighterEx.containsInclusive: both ends count
    addHighlight(editor.document, project, HighlightSeverity.ERROR, LINE_1_START + 1, LINE_1_START + 3)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 3))
      .containsExactlyInAnyOrder(IdeAudioCues.ERROR_LINE, IdeAudioCues.ERROR_CARET)
  }

  @Test
  fun `warning yields the warning cues`() = withEditor { editor, project ->
    addHighlight(editor.document, project, HighlightSeverity.WARNING, LINE_1_START + 1, LINE_1_START + 3)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_END)).containsExactly(IdeAudioCues.WARNING_LINE)
    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2))
      .containsExactlyInAnyOrder(IdeAudioCues.WARNING_LINE, IdeAudioCues.WARNING_CARET)
  }

  @Test
  fun `severities below warning are ignored`() = withEditor { editor, project ->
    addHighlight(editor.document, project, HighlightSeverity.WEAK_WARNING, LINE_1_START + 1, LINE_1_START + 3)
    addHighlight(editor.document, project, HighlightSeverity.INFORMATION, LINE_1_START + 1, LINE_1_START + 3)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2)).isEmpty()
  }

  @Test
  fun `a custom severity ranked above error counts as an error`() = withEditor { editor, project ->
    withRegisteredSeverity(project, FATAL_SEVERITY) {
      addHighlight(editor.document, project, FATAL_SEVERITY, LINE_1_START + 1, LINE_1_START + 3)

      assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2))
        .containsExactlyInAnyOrder(IdeAudioCues.ERROR_LINE, IdeAudioCues.ERROR_CARET)
    }
  }

  @Test
  fun `an error and a warning on one line yield both cues`() = withEditor { editor, project ->
    addHighlight(editor.document, project, HighlightSeverity.ERROR, LINE_1_START, LINE_1_START + 1)
    addHighlight(editor.document, project, HighlightSeverity.WARNING, LINE_1_START + 3, LINE_1_START + 4)

    assertThat(detect(editor, line = 1, caretOffset = LINE_1_START + 2))
      .containsExactlyInAnyOrder(IdeAudioCues.ERROR_LINE, IdeAudioCues.WARNING_LINE)
  }

  @Test
  fun `a highlight on another line is not reported`() = withEditor { editor, project ->
    addHighlight(editor.document, project, HighlightSeverity.ERROR, LINE_1_START + 1, LINE_1_START + 3)

    assertThat(detect(editor, line = 3, caretOffset = LINE_3_START)).isEmpty()
  }

  @Test
  fun `an editor without a project is ignored`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument(TEXT))
        try {
          assertThat(detector.detect(editor, 1, LINE_1_START)).isEmpty()
        }
        finally {
          factory.releaseEditor(editor)
        }
      }
    }
  }

  private fun detect(editor: Editor, line: Int, caretOffset: Int): Set<AudioCue> =
    detector.detect(editor, line, caretOffset).mapTo(HashSet()) { it.cue }

  private fun addHighlight(document: Document, project: Project, severity: HighlightSeverity, start: Int, end: Int): RangeHighlighterEx {
    val markupModel = DocumentMarkupModel.forDocument(document, project, true)
    val highlighter = markupModel.addRangeHighlighter(start, end, HighlighterLayer.ERROR, null, HighlighterTargetArea.EXACT_RANGE)
    val info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
      .severity(severity)
      .range(start, end)
      .createUnconditionally()
    BackgroundUpdateHighlightersUtil.associateInfoAndHighlighter(info, highlighter as RangeHighlighterEx)
    return highlighter
  }

  /**
   * Mimics `HighlightInfoUpdaterImpl.createOrReuseFakeFileLevelHighlighter`: a file-level info is carried by an
   * invisible marker spanning the whole document, so it overlaps every line and contains every caret offset.
   */
  private fun addFileLevelHighlight(document: Document, project: Project, severity: HighlightSeverity) {
    val markupModel = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
    val info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
      .severity(severity)
      .range(0, document.textLength)
      .fileLevelAnnotation()
      .createUnconditionally()
    markupModel.addRangeHighlighterAndChangeAttributes(
      null, 0, document.textLength, HighlighterLayer.ERROR, HighlighterTargetArea.EXACT_RANGE, false
    ) { BackgroundUpdateHighlightersUtil.associateInfoAndHighlighter(info, it) }
  }

  /**
   * Mimics `UnifiedEditorRangeHighlighter`: the marker in the editor's document is a copy carrying the
   * [HighlightInfo] of a marker that lives in another document, at [sourceStart] there.
   */
  private fun copyHighlight(
    editor: Editor,
    project: Project,
    severity: HighlightSeverity,
    start: Int,
    end: Int,
    sourceStart: Int = start,
  ) {
    // the source document is kept referenced so that its marker — and with it the copied info — stays valid
    val sourceDocument = EditorFactory.getInstance().createDocument(TEXT).also { sourceDocuments += it }
    val source = addHighlight(sourceDocument, project, severity, sourceStart, sourceStart + (end - start))
    val targetModel = DocumentMarkupModel.forDocument(editor.document, project, true) as MarkupModelEx
    targetModel.addRangeHighlighterAndChangeAttributes(
      source.textAttributesKey, start, end, source.layer, source.targetArea, false
    ) { it.copyFrom(source) }
  }

  private fun <T> withRegisteredSeverity(project: Project, severity: HighlightSeverity, body: () -> T): T {
    val registrar = SeverityRegistrar.getSeverityRegistrar(project)
    val attributes = SeverityRegistrar.SeverityBasedTextAttributes(
      TextAttributes(null, Color.PINK, null, null, Font.PLAIN),
      HighlightInfoType.HighlightInfoTypeImpl(severity, TextAttributesKey.createTextAttributesKey(severity.name))
    )
    registrar.registerSeverity(attributes, null)
    try {
      return body()
    }
    finally {
      registrar.unregisterSeverity(severity)
    }
  }

  private fun withEditor(editorKind: EditorKind = EditorKind.MAIN_EDITOR, body: (Editor, Project) -> Unit) = timeoutRunBlocking {
    val project = projectFixture.get()
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val factory = EditorFactory.getInstance()
        val editor = factory.createEditor(factory.createDocument(TEXT), project, editorKind)
        try {
          body(editor, project)
        }
        finally {
          factory.releaseEditor(editor)
        }
      }
    }
  }

  private companion object {
    val projectFixture = projectFixture()

    /** Four lines of five characters each: line N starts at 6N and ends at 6N + 5. */
    const val TEXT: String = "line0\nline1\nline2\nline3"

    const val LINE_1_START: Int = 6
    const val LINE_1_END: Int = 11
    const val LINE_3_START: Int = 18

    val FATAL_SEVERITY: HighlightSeverity = HighlightSeverity("AUDIO_CUE_TEST_FATAL", HighlightSeverity.ERROR.myVal + 100)
  }
}
