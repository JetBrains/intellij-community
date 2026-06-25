// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.elf.ElfFeatureFlag
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import java.awt.AWTEvent
import java.awt.KeyboardFocusManager
import java.awt.event.InvocationEvent
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

@TestApplication
class ElfDocumentStressTest {
  private val project: Project get() = projectFixture.get()

  @TestDisposable
  lateinit var testRootDisposable: Disposable

  @Test
  fun `test editor typing is repeatedly rebased over raw real document inserts`() {
    withLockFreeTyping {
      val initialText = "seed"
      val realDocument = DocumentImpl(initialText)
      val elfDocument = getElfDocument(realDocument)
      val editor = EditorFactory.getInstance().createEditor(realDocument, project) as EditorImpl
      try {
        val stats = RebaseStats()
        realDocument.addDocumentListener(stats, testRootDisposable)

        val rawPrefix = StringBuilder()
        val typedSuffix = StringBuilder()
        repeat(STRESS_ITERATIONS) { iteration ->
          val typedFragment = typedFragment(iteration)
          val rawFragment = rawFragment(iteration)
          editor.caretModel.moveToOffset(elfDocument.textLength)
          val markerBeforeTypedFragment = realDocument.createRangeMarker(realDocument.textLength, realDocument.textLength)
          typedSuffix.append(typedFragment)
          val expectedText = rawFragment + rawPrefix + initialText + typedSuffix
          postTypingEventsAndRawInsert(
            editor = editor,
            c = typedFragment.single(),
            realDocument = realDocument,
            rawFragment = rawFragment,
            expectedText = expectedText,
          )
          assertValidPointMarker(
            marker = markerBeforeTypedFragment,
            expectedOffset = expectedText.length - typedFragment.length,
            document = realDocument,
          )

          rawPrefix.insert(0, rawFragment)
        }

        val expectedText = rawPrefix.toString() + initialText + typedSuffix
        waitForSync(realDocument, elfDocument, expectedText)
        assertTextAndSnapshots(realDocument, elfDocument, expectedText)
        assertTrue(
          stats.revertedEventCount >= STRESS_ITERATIONS - ALLOWED_SYNC_RACE_MISSES,
          "Expected most iterations to trigger ELF revert/rebase, got ${stats.revertedEventCount}",
        )
        assertTrue(stats.elfChangedEventCount >= STRESS_ITERATIONS, "Expected many ELF change events, got ${stats.elfChangedEventCount}")
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  private fun postTypingEventsAndRawInsert(
    editor: EditorImpl,
    c: Char,
    realDocument: DocumentImpl,
    rawFragment: String,
    expectedText: String,
  ) {
    val timerTick = System.currentTimeMillis()
    val pressedEvent = KeyEvent(editor.component, KeyEvent.KEY_PRESSED, timerTick, 0, KeyEvent.VK_UNDEFINED, c)
    val typedEvent = KeyEvent(editor.component, KeyEvent.KEY_TYPED, timerTick, 0, KeyEvent.VK_UNDEFINED, c)
    var typedEventProcessed = false
    IdeEventQueue.getInstance().postEvent(pressedEvent)
    IdeEventQueue.getInstance().postEvent(typedEvent)
    dispatchEventsUntilCondition(
      condition = {
        typedEventProcessed &&
        realDocument.text == expectedText &&
        editor.elfDocument.text == expectedText
      },
      errorMessage = {
        "Posted typing event '$c' was not rebased over '$rawFragment': real='${realDocument.text}', elf='${editor.elfDocument.text}'"
      },
      dispatchEvent = { event ->
        when (event) {
          pressedEvent -> dispatchKeyEventToEditor(editor, event)
          typedEvent -> {
            dispatchKeyEventToEditor(editor, event)
            typedEventProcessed = true
            runWriteCommandAction {
              realDocument.insertString(0, rawFragment)
            }
          }
          else -> IdeEventQueue.getInstance().dispatchEvent(event)
        }
      },
    )
  }

  private fun assertValidPointMarker(marker: RangeMarker, expectedOffset: Int, document: Document) {
    assertTrue(marker.isValid, "Marker expected to stay valid")
    assertEquals(expectedOffset, marker.startOffset)
    assertEquals(expectedOffset, marker.endOffset)
    assertTrue(marker.endOffset <= document.textLength, "Marker $marker is outside document length ${document.textLength}")
    document.getLineNumber(marker.startOffset)
  }

  private fun dispatchKeyEventToEditor(editor: EditorImpl, event: AWTEvent) {
    // Headless tests do not naturally focus the editor, so route queued key events the same way editor input tests do.
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(editor.contentComponent, event)
  }

  private class RebaseStats : DocumentListener {
    var elfChangedEventCount = 0
      private set
    var revertedEventCount = 0
      private set

    override fun elfDocumentChanged(event: DocumentEvent) {
      elfChangedEventCount++
    }

    override fun elfDocumentReverted(revertedEvent: DocumentEvent, event: DocumentEvent) {
      revertedEventCount++
    }
  }

  private fun runWriteCommandAction(action: () -> Unit) {
    ApplicationManager.getApplication().runWriteAction {
      CommandProcessor.getInstance().executeCommand(project, { action() }, "", null)
    }
  }

  private fun getElfDocument(document: DocumentImpl): DocumentImpl {
    return Elf.getElf().getElfDocument(document) as DocumentImpl
  }

  private fun withLockFreeTyping(action: () -> Unit): Unit =
    timeoutRunBlocking(
      context = Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement(),
    ) {
      writeIntentReadAction {
        withLockFreeTypingEnabled(action)
      }
    }

  private fun withLockFreeTypingEnabled(action: () -> Unit) {
    val oldValue = ElfFeatureFlag.isEnabled()
    ElfFeatureFlag.setEnabled(true)
    try {
      action()
    }
    finally {
      ElfFeatureFlag.setEnabled(oldValue)
    }
  }

  private fun dispatchEventsUntilCondition(
    condition: () -> Boolean,
    errorMessage: () -> String,
    releaseWriteIntent: Boolean = true,
    dispatchEvent: (AWTEvent) -> Unit = { event -> IdeEventQueue.getInstance().dispatchEvent(event) },
  ) {
    if (condition()) {
      return
    }
    if (releaseWriteIntent) {
      TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
        dispatchEventsUntilConditionNoWriteIntent(condition, errorMessage, dispatchEvent)
      }
    }
    else {
      dispatchEventsUntilConditionNoWriteIntent(condition, errorMessage, dispatchEvent)
    }
  }

  private fun dispatchEventsUntilConditionNoWriteIntent(
    condition: () -> Boolean,
    errorMessage: () -> String,
    dispatchEvent: (AWTEvent) -> Unit,
  ) {
    if (condition()) {
      return
    }
    val eventQueue = IdeEventQueue.getInstance()
    val timedOut = AtomicBoolean(false)
    val timeout = AppExecutorUtil.getAppScheduledExecutorService().schedule(
      { eventQueue.postEvent(InvocationEvent(eventQueue) { timedOut.set(true) }) },
      DEADLOCK_TIMEOUT_SECONDS,
      TimeUnit.SECONDS,
    )
    try {
      while (!condition()) {
        dispatchEvent(eventQueue.nextEvent)
        if (timedOut.get() && !condition()) {
          fail(errorMessage())
        }
      }
    }
    finally {
      timeout.cancel(false)
    }
  }

  private fun waitForSync(document: DocumentImpl, elfDocument: DocumentImpl, expectedText: String) {
    dispatchEventsUntilCondition(
      condition = {
        document.text == expectedText &&
        elfDocument.text == expectedText
      },
      errorMessage = {
        "ELF document did not process pending sync records: real='${document.text}', elf='${elfDocument.text}'"
      },
    )
  }

  private fun assertTextAndSnapshots(document: DocumentImpl, elfDocument: DocumentImpl, expectedText: String) {
    assertEquals(expectedText, document.text)
    assertEquals(expectedText, elfDocument.text)
    assertSame(document.core.snapshot(), elfDocument.core.snapshot())
  }

  private fun typedFragment(iteration: Int): String {
    return ('a'.code + iteration % 26).toChar().toString()
  }

  private fun rawFragment(iteration: Int): String {
    return "<r${iteration.toString(radix = 36)}>"
  }

  companion object {
    private const val STRESS_ITERATIONS = 120
    private const val ALLOWED_SYNC_RACE_MISSES = 20
    private const val DEADLOCK_TIMEOUT_SECONDS: Long = 120

    private val projectFixture = projectFixture()
  }
}
