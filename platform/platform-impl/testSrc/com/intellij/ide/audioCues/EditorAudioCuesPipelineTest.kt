// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.replaceService
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@RegistryKey(key = AUDIO_CUES_ENABLED_REGISTRY_KEY, value = "true")
@Timeout(120)
class EditorAudioCuesPipelineTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `a cue fires when the caret settles on a new line`() = pipelineTest { editor ->
    detector.cues = setOf(LINE_CUE)

    moveCaret(editor, LINE_1_START)

    assertThat(awaitPlay()).containsExactly(LINE_CUE)
  }

  @Test
  fun `a line-scoped cue is not replayed while the caret stays on the line`() = pipelineTest { editor ->
    detector.cues = setOf(LINE_CUE)
    moveCaret(editor, LINE_1_START)
    assertThat(awaitPlay()).containsExactly(LINE_CUE)

    moveCaret(editor, LINE_1_START + 2)
    awaitIdle()

    assertThat(detector.detected()).containsExactly(Detection(1, LINE_1_START), Detection(1, LINE_1_START + 2))
    assertThat(player.recorded).hasSize(1)
  }

  @Test
  fun `a caret-scoped cue does fire on intra-line movement`() = pipelineTest { editor ->
    detector.cues = setOf(CARET_CUE)
    moveCaret(editor, LINE_1_START)
    assertThat(awaitPlay()).containsExactly(CARET_CUE)

    moveCaret(editor, LINE_1_START + 2)

    assertThat(awaitPlay()).containsExactly(CARET_CUE)
  }

  @Test
  fun `a programmatic move rebases the settled line, so a later move within it is silent`() = pipelineTest { editor ->
    detector.cues = setOf(LINE_CUE)
    moveCaretWithoutCommand(editor, LINE_1_START)
    awaitIdle()
    assertThat(detector.detected()).isEmpty()

    moveCaret(editor, LINE_1_START + 2)
    awaitIdle()

    assertThat(detector.detected()).containsExactly(Detection(1, LINE_1_START + 2))
    assertThat(player.recorded).isEmpty()
  }

  @Test
  fun `editors of a kind the cues do not cover are ignored`() = pipelineTest(editorKind = EditorKind.PREVIEW) { editor ->
    detector.cues = setOf(LINE_CUE)

    moveCaret(editor, LINE_1_START)
    awaitIdle()

    assertThat(detector.detected()).isEmpty()
  }

  @Test
  fun `moving a secondary caret is ignored`() = pipelineTest { editor ->
    detector.cues = setOf(LINE_CUE)

    moveSecondaryCaret(editor, LINE_3_START)
    awaitIdle()

    assertThat(detector.detected()).isEmpty()
  }

  @Test
  fun `rapid moves collapse into one detection at the last position`() = pipelineTest { editor ->
    detector.cues = setOf(CARET_CUE)

    // all three in one EDT hop, so they land well inside the settle window
    moveCaretSequence(editor, LINE_1_START, LINE_2_START, LINE_3_START)

    assertThat(awaitPlay()).containsExactly(CARET_CUE)
    awaitIdle()
    assertThat(detector.detected()).containsExactly(Detection(3, LINE_3_START))
  }

  @Test
  fun `a move right after an edit waits for the longer, edit-adjacent delay`() = pipelineTest { editor ->
    detector.cues = setOf(LINE_CUE)

    editThenMoveCaret(editor, LINE_1_START)

    delay(IDLE_WAIT)
    assertThat(detector.detected()).isEmpty()

    assertThat(awaitPlay(EDIT_ADJACENT_BUDGET)).containsExactly(LINE_CUE)
  }

  @Test
  fun `a move long after an edit uses the short delay`() = pipelineTest { editor ->
    detector.cues = setOf(LINE_CUE)

    editDocumentAtEnd(editor)
    delay(TYPING_WINDOW * 3)
    moveCaret(editor, LINE_1_START)

    assertThat(awaitPlay(SHORT_PATH_BUDGET)).containsExactly(LINE_CUE)
  }

  @Test
  fun `a deletion before the caret cues although it fires no caret event`() = pipelineTest { editor ->
    detector.cues = setOf(CARET_CUE)
    moveCaret(editor, LINE_1_START + 3)
    assertThat(awaitPlay()).containsExactly(CARET_CUE)

    deleteBeforeCaret(editor)

    // an edit always takes the longer, edit-adjacent delay
    delay(IDLE_WAIT)
    assertThat(detector.detected()).containsExactly(Detection(1, LINE_1_START + 3))

    assertThat(awaitPlay(EDIT_ADJACENT_BUDGET)).containsExactly(CARET_CUE)
    // the deletion carried the caret one character back
    assertThat(detector.detected()).containsExactly(Detection(1, LINE_1_START + 3), Detection(1, LINE_1_START + 2))
  }

  @Test
  fun `a deletion at the caret cues although the caret does not move at all`() = pipelineTest { editor ->
    detector.cues = setOf(CARET_CUE)
    moveCaret(editor, LINE_1_START + 2)
    assertThat(awaitPlay()).containsExactly(CARET_CUE)

    deleteAtCaret(editor)

    assertThat(awaitPlay(EDIT_ADJACENT_BUDGET)).containsExactly(CARET_CUE)
    assertThat(detector.detected()).containsExactly(Detection(1, LINE_1_START + 2), Detection(1, LINE_1_START + 2))
  }

  @Test
  fun `an edit does not replay line-scoped cues`() = pipelineTest { editor ->
    // an edit is scoped like intra-line movement, or every keystroke would replay the line's VCS gutter cue
    detector.cues = setOf(LINE_CUE, CARET_CUE)
    moveCaret(editor, LINE_1_START + 3)
    assertThat(awaitPlay()).containsExactlyInAnyOrder(LINE_CUE, CARET_CUE)

    deleteBeforeCaret(editor)

    assertThat(awaitPlay(EDIT_ADJACENT_BUDGET)).containsExactly(CARET_CUE)
  }

  @Test
  fun `an edit away from the caret is silent`() = pipelineTest { editor ->
    detector.cues = setOf(CARET_CUE)
    moveCaret(editor, LINE_1_START)
    assertThat(awaitPlay()).containsExactly(CARET_CUE)

    editDocumentAtEnd(editor)
    delay(IDLE_WAIT_AFTER_EDIT)

    assertThat(detector.detected()).containsExactly(Detection(1, LINE_1_START))
    assertThat(player.recorded).hasSize(1)
  }

  @Test
  fun `a document change outside a command is silent`() = pipelineTest { editor ->
    detector.cues = setOf(CARET_CUE)
    moveCaret(editor, LINE_1_START + 3)
    assertThat(awaitPlay()).containsExactly(CARET_CUE)

    deleteBeforeCaretWithoutCommand(editor)
    delay(IDLE_WAIT_AFTER_EDIT)

    assertThat(detector.detected()).containsExactly(Detection(1, LINE_1_START + 3))
    assertThat(player.recorded).hasSize(1)
  }

  @Test
  fun `an edit in an editor of a kind the cues do not cover is ignored`() = pipelineTest(editorKind = EditorKind.PREVIEW) { editor ->
    detector.cues = setOf(CARET_CUE)

    moveCaret(editor, LINE_1_START + 3)
    deleteBeforeCaret(editor)
    delay(IDLE_WAIT_AFTER_EDIT)

    assertThat(detector.detected()).isEmpty()
  }

  @Test
  fun `a document change delivered off the EDT is ignored`() = pipelineTest { editor ->
    detector.cues = setOf(CARET_CUE)
    moveCaret(editor, LINE_1_START + 3)
    assertThat(awaitPlay()).containsExactly(CARET_CUE)

    // a document created with allowUpdatesWithoutWriteAction (consoles, LSP formatting) drops every write assertion,
    // so the multicaster can deliver documentChanged off the EDT - where the caret model is unreadable and
    // `currentCommand` is a torn read. Real off-EDT delivery is not reproducible here, hence the direct call.
    val failure = AtomicReference<Throwable>()
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        CommandProcessor.getInstance().executeCommand(project, {
          val offset = editor.caretModel.offset
          // started inside the command, so that `currentCommand` is published to the worker
          val worker = thread(name = "off-EDT document change") {
            runCatching { manager.handleDocumentChange(editor.document, offset - 1, offset) }.onFailure(failure::set)
          }
          worker.join()
        }, null, null)
      }
    }
    delay(IDLE_WAIT_AFTER_EDIT)

    assertThat(failure.get()).isNull()
    assertThat(detector.detected()).containsExactly(Detection(1, LINE_1_START + 3))
    assertThat(player.recorded).hasSize(1)
  }

  @Test
  fun `multi-caret typing cues the primary caret`() = pipelineTest { editor ->
    detector.cues = setOf(CARET_CUE)
    moveCaret(editor, LINE_1_START + 3)
    assertThat(awaitPlay()).containsExactly(CARET_CUE)

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        // makePrimary = false, or the added caret would become the primary one and there would be nothing to tell apart
        requireNotNull(editor.caretModel.addCaret(VisualPosition(3, 3), false)) { "a second caret could not be added" }
      }
      // one Backspace per caret, the way a typing action drives it. runForEachCaret walks the carets in document
      // order, so the *secondary* one is current for the last document event: reading caretModel.offset there is
      // getCurrentCaret().offset, and the read action's primaryCaret comparison then throws that request away.
      WriteCommandAction.runWriteCommandAction(project) {
        editor.caretModel.runForEachCaret { caret -> editor.document.deleteString(caret.offset - 1, caret.offset) }
      }
    }

    assertThat(awaitPlay(EDIT_ADJACENT_BUDGET)).containsExactly(CARET_CUE)
    // the cue must describe the primary caret, not the secondary one that was current for the last document event
    assertThat(detector.detected().last()).isEqualTo(Detection(1, LINE_1_START + 2))
  }

  @Test
  fun `a detector that cancels does not silence later caret moves`() = pipelineTest(extraDetectors = listOf(CancellingDetector())) { editor ->
    detector.cues = setOf(LINE_CUE)

    // getOrLogException rethrows a ProcessCanceledException, and there is exactly one collector - but collectLatest
    // runs each request in a child coroutine, where a CancellationException is its own cancel-previous signal and
    // never reaches the collector. That is what makes the plain runCatching enough; a restructuring that collects
    // the requests directly would silence the feature for the rest of the session instead.
    moveCaret(editor, LINE_1_START)
    awaitIdle()
    assertThat(player.recorded).isEmpty()

    moveCaret(editor, LINE_2_START)
    assertThat(awaitPlay()).containsExactly(LINE_CUE)
  }

  @Test
  fun `an edit inside a bulk document update is silent`() = pipelineTest { editor ->
    detector.cues = setOf(CARET_CUE)
    moveCaret(editor, LINE_1_START + 3)
    assertThat(awaitPlay()).containsExactly(CARET_CUE)

    bulkDeleteBeforeCaret(editor)
    delay(IDLE_WAIT_AFTER_EDIT)

    assertThat(detector.detected()).containsExactly(Detection(1, LINE_1_START + 3))
    assertThat(player.recorded).hasSize(1)
  }

  @Test
  fun `a move right after a bulk update still waits for the edit-adjacent delay`() = pipelineTest { editor ->
    detector.cues = setOf(LINE_CUE)

    bulkEditThenMoveCaret(editor, LINE_1_START)

    delay(IDLE_WAIT)
    assertThat(detector.detected()).isEmpty()

    assertThat(awaitPlay(EDIT_ADJACENT_BUDGET)).containsExactly(LINE_CUE)
  }

  @Test
  fun `an edit that carries the caret along drops an already debounced request`() = pipelineTest { editor ->
    detector.cues = setOf(CARET_CUE)
    moveCaret(editor, LINE_3_START)
    assertThat(awaitPlay()).containsExactly(CARET_CUE)

    deleteAtCaret(editor)
    insertAtDocumentStart(editor)
    delay(IDLE_WAIT_AFTER_EDIT)

    assertThat(detector.detected()).containsExactly(Detection(3, LINE_3_START))
    assertThat(player.recorded).hasSize(1)
  }

  @Test
  fun `a caret cue is dropped when its line cue fires in the same batch`() = pipelineTest { editor ->
    detector.cues = setOf(COUNTERPART_LINE_CUE, CARET_CUE)

    moveCaret(editor, LINE_1_START)

    assertThat(awaitPlay()).containsExactly(COUNTERPART_LINE_CUE)
  }

  @Test
  fun `a caret cue survives when its line cue is muted`() = pipelineTest { editor ->
    // the suppression runs ahead of the player's settings filter, so an ungated one would leave nothing to play
    detector.cues = setOf(COUNTERPART_LINE_CUE, CARET_CUE)
    settings.setCueEnabled(COUNTERPART_LINE_CUE, false)

    moveCaret(editor, LINE_1_START)

    assertThat(awaitPlay()).containsExactly(CARET_CUE)
  }

  @Test
  fun `a caret cue still fires on intra-line movement while its line cue is detected`() = pipelineTest { editor ->
    // the line cue is filtered out by the caret scoping first, so there is nothing left to suppress against
    detector.cues = setOf(COUNTERPART_LINE_CUE, CARET_CUE)
    moveCaret(editor, LINE_1_START)
    assertThat(awaitPlay()).containsExactly(COUNTERPART_LINE_CUE)

    moveCaret(editor, LINE_1_START + 2)

    assertThat(awaitPlay()).containsExactly(CARET_CUE)
  }

  @Test
  fun `a position stale by the time the read action runs reaches no detector`() = pipelineTest { editor ->
    detector.cues = setOf(LINE_CUE)
    val document = editor.document

    assertThat(detectCues(editor, line = -1, caretOffset = LINE_1_START)).isEmpty()
    assertThat(detectCues(editor, line = document.lineCount, caretOffset = LINE_1_START)).isEmpty()
    assertThat(detectCues(editor, line = 1, caretOffset = document.textLength + 1)).isEmpty()

    assertThat(detector.detected()).isEmpty()
  }

  @Test
  fun `disabling the feature detaches the listeners`() = pipelineTest { editor ->
    detector.cues = setOf(LINE_CUE)

    settings.setMode(AudioCuesMode.OFF)
    // refreshAudioCuesState() only reaches the manager *service*, which this test deliberately does not create
    manager.updateListenersState()
    moveCaret(editor, LINE_1_START)
    awaitIdle()

    assertThat(detector.detected()).isEmpty()
  }

  // --- harness ---------------------------------------------------------------------------------------

  private data class Detection(val line: Int, val offset: Int)

  private class FakeDetector : EditorAudioCueDetector {
    @Volatile
    var cues: Set<AudioCue> = emptySet()

    private val all = CopyOnWriteArrayList<Detection>()

    override fun detect(editor: Editor, line: Int, caretOffset: Int): Set<EditorAudioCue> {
      check(!ApplicationManager.getApplication().isDispatchThread) { "detector ran on the EDT" }
      check(ApplicationManager.getApplication().isReadAccessAllowed) { "detector ran without read access" }
      all += Detection(line, caretOffset)
      return cues.mapTo(HashSet()) { cue ->
        EditorAudioCue(cue, lineCounterpart = COUNTERPART_LINE_CUE.takeIf { cue === CARET_CUE })
      }
    }

    fun detected(): List<Detection> = all.toList()
  }

  /**
   * A PCE is what a container mid-disposal throws; both `getOrLogException` and `forEachExtensionSafe` rethrow it.
   * Only once, though: rethrowing means it aborts the whole detection, so a permanently cancelling detector would
   * keep every later batch empty as well and prove nothing about the collector.
   */
  private class CancellingDetector : EditorAudioCueDetector {
    private val pending = AtomicBoolean(true)

    override fun detect(editor: Editor, line: Int, caretOffset: Int): Set<EditorAudioCue> {
      if (pending.getAndSet(false)) throw ProcessCanceledException()
      return emptySet()
    }
  }

  private class RecordingPlayer : AudioCuePlayer() {
    private val all = CopyOnWriteArrayList<List<AudioCue>>()
    private val signals = Channel<List<AudioCue>>(Channel.UNLIMITED)

    override fun playEnabled(cues: Collection<AudioCue>) {
      val batch = cues.toList()
      all += batch
      signals.trySend(batch)
    }

    val recorded: List<List<AudioCue>> get() = all.toList()

    suspend fun awaitPlay(): List<AudioCue> = signals.receive()
  }

  private lateinit var project: Project
  private lateinit var settings: AudioCuesSettings
  private lateinit var manager: EditorAudioCuesManager
  private lateinit var detector: FakeDetector
  private lateinit var player: RecordingPlayer

  private fun pipelineTest(
    editorKind: EditorKind = EditorKind.MAIN_EDITOR,
    extraDetectors: List<EditorAudioCueDetector> = emptyList(),
    body: suspend (Editor) -> Unit,
  ) = timeoutRunBlocking(60.seconds) {
    project = projectFixture.get()
    detector = FakeDetector()
    player = RecordingPlayer()

    withAudioCuesSettings { audioCuesSettings ->
      settings = audioCuesSettings
      val managerScope = childScope("EditorAudioCuesManager under test")
      try {
        // ON, not AUTO: tests run without a screen reader, so AUTO would silence everything
        settings.setMode(AudioCuesMode.ON)
        ApplicationManager.getApplication().replaceService(AudioCuePlayer::class.java, player, disposable)
        ExtensionTestUtil.maskExtensions(EditorAudioCueDetector.EP_NAME, extraDetectors + detector, disposable)

        manager = EditorAudioCuesManager(managerScope, SETTLE_DELAY, EDIT_ADJACENT_DELAY)

        val factory = EditorFactory.getInstance()
        val editor = withContext(Dispatchers.EDT) {
          writeIntentReadAction { factory.createEditor(factory.createDocument(TEXT), project, editorKind) }
        }
        UIUtil.markAsFocused(editor.contentComponent, true)
        try {
          body(editor)
        }
        finally {
          withContext(Dispatchers.EDT) { writeIntentReadAction { factory.releaseEditor(editor) } }
        }
      }
      finally {
        managerScope.cancel()
      }
    }
  }

  private suspend fun moveCaret(editor: Editor, offset: Int) = moveCaretSequence(editor, offset)

  private suspend fun moveCaretSequence(editor: Editor, vararg offsets: Int) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        CommandProcessor.getInstance().executeCommand(project, {
          offsets.forEach { editor.caretModel.moveToOffset(it) }
        }, null, null)
      }
    }
  }

  private suspend fun moveCaretWithoutCommand(editor: Editor, offset: Int) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction { editor.caretModel.moveToOffset(offset) }
    }
  }

  private suspend fun moveSecondaryCaret(editor: Editor, offset: Int) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val added: Caret? = editor.caretModel.addCaret(VisualPosition(2, 0))
        requireNotNull(added) { "a second caret could not be added" }
        val secondary = editor.caretModel.allCarets.first { it !== editor.caretModel.primaryCaret }
        CommandProcessor.getInstance().executeCommand(project, { secondary.moveToOffset(offset) }, null, null)
      }
    }
  }

  private suspend fun editDocumentAtEnd(editor: Editor) {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(editor.document.textLength, "!") }
    }
  }

  /**
   * Both in one EDT hop: the manager's typing window is 100 ms of real time, so the edit and the move must not
   * be separated by a dispatch.
   */
  private suspend fun editThenMoveCaret(editor: Editor, offset: Int) {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(editor.document.textLength, "!") }
      writeIntentReadAction {
        CommandProcessor.getInstance().executeCommand(project, { editor.caretModel.moveToOffset(offset) }, null, null)
      }
    }
  }

  /**
   * What `Backspace` does: the range marker carries the caret along, firing no caret event. Deliberately not a
   * caret move — that absence is the point of the cases using it.
   */
  private suspend fun deleteBeforeCaret(editor: Editor) {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        val offset = editor.caretModel.offset
        editor.document.deleteString(offset - 1, offset)
      }
    }
  }

  /** What `Delete` does: not even the caret offset changes. */
  private suspend fun deleteAtCaret(editor: Editor) {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        val offset = editor.caretModel.offset
        editor.document.deleteString(offset, offset + 1)
      }
    }
  }

  /**
   * An edit above the caret: it carries the caret along through range markers, firing no caret event, and the
   * caret is outside the changed range, so the document listener emits nothing either.
   */
  private suspend fun insertAtDocumentStart(editor: Editor) {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(0, "!") }
    }
  }

  /** A bulk update delivers no per-change event at all, so nothing may be detected from it. */
  private suspend fun bulkDeleteBeforeCaret(editor: Editor) {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        DocumentUtil.executeInBulk(editor.document) {
          val offset = editor.caretModel.offset
          editor.document.deleteString(offset - 1, offset)
        }
      }
    }
  }

  /** Both in one EDT hop, for the same reason as [editThenMoveCaret]: the typing window is 100 ms of real time. */
  private suspend fun bulkEditThenMoveCaret(editor: Editor, offset: Int) {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        DocumentUtil.executeInBulk(editor.document) { editor.document.insertString(editor.document.textLength, "!") }
      }
      writeIntentReadAction {
        CommandProcessor.getInstance().executeCommand(project, { editor.caretModel.moveToOffset(offset) }, null, null)
      }
    }
  }

  /** An undo-transparent action is the only way in: the platform rejects a document change outside both it and a command. */
  private suspend fun deleteBeforeCaretWithoutCommand(editor: Editor) {
    edtWriteAction {
      CommandProcessor.getInstance().runUndoTransparentAction {
        val offset = editor.caretModel.offset
        editor.document.deleteString(offset - 1, offset)
      }
    }
  }

  private suspend fun detectCues(editor: Editor, line: Int, caretOffset: Int): Set<EditorAudioCue> =
    readAction { manager.detectCues(editor, line, caretOffset) }

  private suspend fun awaitPlay(within: Duration = AWAIT_BUDGET): List<AudioCue> = withTimeout(within) { player.awaitPlay() }

  /** An absence cannot be awaited; this waits well past the normal settle delay instead. */
  private suspend fun awaitIdle() = delay(IDLE_WAIT)

  private companion object {
    val projectFixture = projectFixture()

    /** The manager's own typing window; a caret move within this long of an edit takes the slow path. */
    val TYPING_WINDOW = 100.milliseconds

    /**
     * The manager's two debounce delays, injected rather than taken from production: every wait below has to
     * outlast one of them, so shrinking them here is what keeps this class from sleeping for twenty seconds.
     * The gap between them has to stay wide enough for [SHORT_PATH_BUDGET] to tell the two paths apart.
     */
    val SETTLE_DELAY = 20.milliseconds
    val EDIT_ADJACENT_DELAY = 400.milliseconds

    /** Comfortably past [SETTLE_DELAY], comfortably short of [EDIT_ADJACENT_DELAY]. */
    val IDLE_WAIT = 150.milliseconds

    /** Silence after an edit has to outlast [EDIT_ADJACENT_DELAY], which [IDLE_WAIT] deliberately does not. */
    val IDLE_WAIT_AFTER_EDIT = 800.milliseconds

    /** A normal move must have cued by now; anything slower means it took the edit-adjacent path. */
    val SHORT_PATH_BUDGET = 250.milliseconds
    val EDIT_ADJACENT_BUDGET = 5.seconds
    val AWAIT_BUDGET = 10.seconds

    /** Must stay a cue that is *not* [CARET_CUE]'s line counterpart: cases pairing the two rely on both surviving. */
    val LINE_CUE = IdeAudioCues.FOLDED_LINE
    val CARET_CUE = IdeAudioCues.ERROR_CARET

    /** [CARET_CUE]'s line counterpart, for the cases that exercise that suppression. */
    val COUNTERPART_LINE_CUE = IdeAudioCues.ERROR_LINE

    const val TEXT: String = "line0\nline1\nline2\nline3"
    const val LINE_1_START: Int = 6
    const val LINE_2_START: Int = 12
    const val LINE_3_START: Int = 18
  }
}
