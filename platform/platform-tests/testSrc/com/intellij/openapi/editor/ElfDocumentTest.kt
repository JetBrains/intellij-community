// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.elf.ElfFeatureFlag
import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.ElfCandidate
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.ElfDocumentSyncScheduler
import com.intellij.openapi.editor.impl.DocumentWriteAccessGuard
import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.platform.locking.impl.getGlobalThreadingSupport
import com.intellij.util.DocumentEventUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.EDT
import org.junit.jupiter.api.Test
import java.awt.event.InvocationEvent
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

@TestApplication
class ElfDocumentTest {
  private val project: Project get() = projectFixture.get()

  @TestDisposable lateinit var testRootDisposable: Disposable

  @Test
  fun `test create elf`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      assertTextAndSnapshots(document, elfDocument, "abc")
    }
  }

  @Test
  fun `test elf document change is synced to real document with same snapshot`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      withElfScope {
        runCommandAction {
          document.insertString(0, "x")
        }
      }
      val elfChangeModStamp = elfDocument.modificationStamp
      waitForTextAndAssertSnapshots(document, elfDocument, "xabc")
      assertEquals(elfChangeModStamp, document.modificationStamp)
    }
  }

  @Test
  fun `test psi interaction is only blocked inside elf scope`() {
    withLockFreeTyping {
      assertTrue(Elf.getElf().isPsiInteractionAllowed())
      withElfScope {
        assertFalse(Elf.getElf().isPsiInteractionAllowed())
      }
      assertTrue(Elf.getElf().isPsiInteractionAllowed())
    }
  }

  @Test
  fun `test elf document cannot be mutated outside elf scope`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val failure = assertFailsWith<IllegalStateException> {
        runCommandAction {
          elfDocument.insertString(0, "x")
        }
      }
      assertEquals("ElfDocument is mutable only within elf scope", failure.message)
      assertTextAndSnapshots(document, elfDocument, "abc")
    }
  }

  @Test
  fun `test real document delegates to elf document inside elf scope`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      var realTextInsideScope: String? = null
      var realSnapshotTextInsideScope: String? = null
      var elfSnapshotTextInsideScope: String? = null
      withElfScope {
        runCommandAction {
          document.insertString(0, "x")
        }
        realTextInsideScope = document.text
        realSnapshotTextInsideScope = getSnapshot(document).string()
        elfSnapshotTextInsideScope = getSnapshot(elfDocument).string()
      }
      assertEquals("xabc", realTextInsideScope)
      assertEquals("xabc", realSnapshotTextInsideScope)
      assertEquals("xabc", elfSnapshotTextInsideScope)
      assertEquals("abc", document.text)
      assertEquals("xabc", elfDocument.text)
      assertEquals("abc", getSnapshot(document).string())
      assertEquals("xabc", getSnapshot(elfDocument).string())
      waitForTextAndAssertSnapshots(document, elfDocument, "xabc")
    }
  }

  @Test
  fun `test real document change inside elf scope is synced to elf document with same snapshot`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val realDocument = getRealDocument(document)
      withElfScope {
        runWriteCommandAction {
          realDocument.insertString(0, "x")
        }
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "xabc")
    }
  }

  @Test
  fun `test real document metadata change inside elf scope is synced to elf document with same snapshot`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val realDocument = getRealDocument(document)
      val expectedModStamp = 42L
      withElfScope {
        realDocument.modificationStamp = expectedModStamp
      }
      waitForElfSchedulerIdle()
      assertEquals(expectedModStamp, document.modificationStamp)
      assertEquals(expectedModStamp, elfDocument.modificationStamp)
      assertSameSnapshot(document, elfDocument)
    }
  }

  @Test
  fun `test get real document always sees real document content`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val realDocument = getRealDocument(document)
      assertEquals("abc", realDocument.text)
      withElfScope {
        runCommandAction {
          document.insertString(0, "x")
        }
        assertEquals("xabc", document.text)
        assertEquals("xabc", elfDocument.text)
        assertEquals("abc", realDocument.text)
        assertEquals("abc", getRealDocument(document).text)
      }
      assertEquals("abc", realDocument.text)
      waitForTextAndAssertSnapshots(document, elfDocument, "xabc")
      assertEquals("xabc", realDocument.text)
    }
  }

  @Test
  fun `test pending elf change is scheduled when elf scope fails`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val expectedFailure = RuntimeException("intentional ELF scope failure")
      val actualFailure = assertFailsWith<RuntimeException> {
        runCommandAction {
          withElfScope {
            document.insertString(0, "x")
            throw expectedFailure
          }
        }
      }
      assertSame(expectedFailure, actualFailure)
      assertEquals("xabc", elfDocument.text)
      assertEquals("abc", document.text)
      waitForTextAndAssertSnapshots(document, elfDocument, "xabc")
    }
  }

  @Test
  fun `test real listener snapshot mutation is synced to elf document with same snapshot`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val expectedModStamp = 42L
      document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          document.modificationStamp = expectedModStamp
        }
      }, testRootDisposable)
      runWriteCommandAction {
        document.insertString(0, "x")
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "xabc")
      assertEquals(expectedModStamp, document.modificationStamp)
    }
  }

  @Test
  fun `test real document snapshot access is allowed from background thread`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val text = ApplicationManager.getApplication().executeOnPooledThread(Callable {
        document.text
      })
      assertEquals("abc", waitForFutureBlocking(text))
    }
  }

  @Test
  fun `test real document modification stamp can be set from background thread`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val expectedModStamp = 42L
      val update = ApplicationManager.getApplication().executeOnPooledThread {
        document.modificationStamp = expectedModStamp
      }
      waitForFutureBlocking(update)
      assertEquals(expectedModStamp, document.modificationStamp)
      assertEquals(expectedModStamp, elfDocument.modificationStamp)
      assertSameSnapshot(document, elfDocument)
    }
  }

  /**
   * Conservative regression guard, not a strict contract:
   * production is expected to enter bulk mode for a real document on EDT, not on BGT with WIL
   */
  @Test
  fun `test real document bulk mode can be set from background write intent`() {
    withLockFreeTypingEnabled(true) {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val update = ApplicationManager.getApplication().executeOnPooledThread(Callable {
        getGlobalThreadingSupport().runWriteIntentReadAction {
          assertFalse(EDT.isCurrentThreadEdt())
          DocumentUtil.executeInBulk(document, true) {
            assertTrue(document.isInBulkUpdate)
            assertFalse(elfDocument.isInBulkUpdate)
          }
        }
      })
      waitForFutureBlocking(update)
      assertFalse(document.isInBulkUpdate)
      assertFalse(elfDocument.isInBulkUpdate)
      assertSameSnapshot(document, elfDocument)
    }
  }

  @Test
  fun `test several pending elf changes are synced in order`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      withElfScope {
        runCommandAction {
          document.insertString(0, "x")
          document.insertString(1, "y")
        }
      }
      waitForTextAndAssertSnapshots(document, "xyabc")
    }
  }

  @Test
  fun `test several pending elf changes from diff scopes are synced in order`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      withElfScope {
        runCommandAction {
          document.insertString(0, "x")
        }
      }
      withElfScope {
        runCommandAction {
          document.insertString(1, "y")
        }
      }
      waitForTextAndAssertSnapshots(document, "xyabc")
    }
  }

  @Test
  fun `test different documents are synced in separate write actions`() {
    withLockFreeTyping {
      val firstDocument = DocumentImpl("a")
      val firstElfDocument = getElfDocument(firstDocument)
      val secondDocument = DocumentImpl("b")
      val secondElfDocument = getElfDocument(secondDocument)
      val writeActionGeneration = AtomicInteger()
      val firstSyncGenerations = mutableListOf<Int>()
      val secondSyncGenerations = mutableListOf<Int>()
      var recordSyncEvents = false
      (ApplicationManager.getApplication() as ApplicationEx).addWriteActionListener(object : WriteActionListener {
        override fun writeActionStarted(action: Class<*>) {
          writeActionGeneration.incrementAndGet()
        }
      }, testRootDisposable)
      firstDocument.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          if (recordSyncEvents) {
            firstSyncGenerations.add(writeActionGeneration.get())
          }
        }
      }, testRootDisposable)
      secondDocument.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          if (recordSyncEvents) {
            secondSyncGenerations.add(writeActionGeneration.get())
          }
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          firstDocument.insertString(firstDocument.textLength, "x")
          secondDocument.insertString(secondDocument.textLength, "y")
        }
      }
      recordSyncEvents = true
      dispatchEventsUntilCondition(
        condition = {
          firstDocument.text == "ax" &&
          secondDocument.text == "by" &&
          firstSyncGenerations.isNotEmpty() &&
          secondSyncGenerations.isNotEmpty()
        },
        errorMessage = {
          "ELF documents did not process pending sync records: " +
          "firstReal='${firstDocument.text}', firstElf='${firstElfDocument.text}', " +
          "secondReal='${secondDocument.text}', secondElf='${secondElfDocument.text}'"
        },
      )
      assertEquals(1, firstSyncGenerations.size)
      assertEquals(1, secondSyncGenerations.size)
      assertNotEquals(firstSyncGenerations.single(), secondSyncGenerations.single())
      assertTextAndSnapshots(firstDocument, firstElfDocument, "ax")
      assertTextAndSnapshots(secondDocument, secondElfDocument, "by")
    }
  }

  @Test
  fun `test elf changes scheduled before document sync runs are coalesced`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      withElfScope {
        runCommandAction {
          document.insertString(document.textLength, "x")
        }
        Elf.getElf().performOnScopeFinished {
          withElfScope {
            runCommandAction {
              document.insertString(document.textLength, "y")
            }
          }
        }
      }
      waitForTextAndAssertSnapshots(document, "abcxy")
    }
  }

  @Test
  fun `test elf document reports event handling during elf events`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val eventHandlingLog = mutableListOf<String>()
      document.addDocumentListener(object : DocumentListener {
        override fun elfDocumentChanged(event: DocumentEvent) {
          eventHandlingLog.add("changed:${(event.document as DocumentEx).isInEventsHandling}")
        }
        override fun elfDocumentReverted(revertedEvent: DocumentEvent, event: DocumentEvent) {
          val revertedInEventHandling = (revertedEvent.document as DocumentEx).isInEventsHandling
          val changeInEventHandling = (event.document as DocumentEx).isInEventsHandling
          eventHandlingLog.add("reverted:$revertedInEventHandling:$changeInEventHandling")
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          document.insertString(0, "x")
        }
      }
      runWriteCommandAction {
        document.insertString(1, "y")
      }
      waitForTextAndAssertSnapshots(document, "xaybc")
      assertEquals(
        listOf(
          "changed:true",
          "reverted:true:true",
          "changed:true",
          "changed:true",
        ),
        eventHandlingLog,
      )
    }
  }

  @Test
  fun `test elf document reports bulk mode during elf bulk changes`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val bulkModeLog = mutableListOf<Boolean>()
      document.addDocumentListener(object : DocumentListener {
        override fun elfDocumentChanged(event: DocumentEvent) {
          bulkModeLog.add(event.document.isInBulkUpdate)
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          DocumentUtil.executeInBulk(document, true) {
            assertTrue(document.isInBulkUpdate)
            document.insertString(0, "x")
          }
          assertFalse(document.isInBulkUpdate)
        }
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "xabc")
      assertEquals(listOf(true), bulkModeLog)
      assertFalse(document.isInBulkUpdate)
      assertFalse(elfDocument.isInBulkUpdate)
    }
  }

  @Test
  fun `test elf document bulk mode can be set only within elf scope`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val failure = assertFailsWith<IllegalStateException> {
        DocumentUtil.executeInBulk(elfDocument, true) {}
      }
      assertEquals("ElfDocument is mutable only within elf scope", failure.message)
      assertFalse(document.isInBulkUpdate)
      assertFalse(elfDocument.isInBulkUpdate)
      withElfScope {
        DocumentUtil.executeInBulk(elfDocument, true) {
          assertTrue(elfDocument.isInBulkUpdate)
        }
      }
      assertFalse(document.isInBulkUpdate)
      assertFalse(elfDocument.isInBulkUpdate)
    }
  }

  @Test
  fun `test real document bulk mode is synced to elf document when snapshots are clean`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val realBulkModeLog = mutableListOf<Boolean>()
      val elfBulkModeLog = mutableListOf<Boolean>()
      document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          realBulkModeLog.add(event.document.isInBulkUpdate)
        }
        override fun elfDocumentChanged(event: DocumentEvent) {
          elfBulkModeLog.add(event.document.isInBulkUpdate)
        }
      }, testRootDisposable)
      runWriteCommandAction {
        DocumentUtil.executeInBulk(document, true) {
          document.insertString(0, "x")
        }
      }
      val elfDocument = getElfDocument(document)
      assertTextAndSnapshots(document, elfDocument, "xabc")
      assertEquals(listOf(true), realBulkModeLog)
      assertEquals(listOf(true), elfBulkModeLog)
      assertFalse(document.isInBulkUpdate)
      assertFalse(elfDocument.isInBulkUpdate)
    }
  }

  @Suppress("DEPRECATION")
  @Test
  fun `test bulk update listeners are notified for real and elf bulk changes when snapshots are clean`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val bulkEventLog = mutableListOf<String>()
      document.addDocumentListener(object : DocumentListener {
        override fun bulkUpdateStarting(hostDocument: Document) {
          assertSame(document, hostDocument)
          bulkEventLog.add("real:start")
        }
        override fun bulkElfUpdateStarting(hostDocument: Document) {
          assertSame(document, hostDocument)
          bulkEventLog.add("elf:start")
        }
        override fun bulkUpdateFinished(hostDocument: Document) {
          assertSame(document, hostDocument)
          bulkEventLog.add("real:finish")
        }
        override fun bulkElfUpdateFinished(hostDocument: Document) {
          assertSame(document, hostDocument)
          bulkEventLog.add("elf:finish")
        }
      }, testRootDisposable)
      ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(
        DocumentBulkUpdateListener.TOPIC,
        object : DocumentBulkUpdateListener {
          override fun updateStarted(doc: Document) {
            assertSame(document, doc)
            bulkEventLog.add("topic:start")
          }

          override fun updateFinished(doc: Document) {
            assertSame(document, doc)
            bulkEventLog.add("topic:finish")
          }
        },
      )
      runWriteCommandAction {
        DocumentUtil.executeInBulk(document, true) {
          assertTrue(document.isInBulkUpdate)
          assertTrue(elfDocument.isInBulkUpdate)
          document.insertString(0, "x")
        }
      }
      assertTextAndSnapshots(document, elfDocument, "xabc")
      assertEquals(
        listOf("real:start", "elf:start", "topic:start", "topic:finish", "real:finish", "elf:finish"),
        bulkEventLog,
      )
      assertFalse(document.isInBulkUpdate)
      assertFalse(elfDocument.isInBulkUpdate)
    }
  }

  @Test
  fun `test ElfCandidate listener observes bulk mode during synced real bulk change`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val candidateBulkModeLog = mutableListOf<Boolean>()
      @ElfCandidate
      class CandidateListener : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          candidateBulkModeLog.add(event.document.isInBulkUpdate)
        }
      }
      document.addDocumentListener(CandidateListener(), testRootDisposable)
      runWriteCommandAction {
        DocumentUtil.executeInBulk(document, true) {
          document.insertString(0, "x")
        }
      }
      val elfDocument = getElfDocument(document)
      assertTextAndSnapshots(document, elfDocument, "xabc")
      assertEquals(listOf(true), candidateBulkModeLog)
      assertFalse(document.isInBulkUpdate)
      assertFalse(elfDocument.isInBulkUpdate)
    }
  }

  @Test
  fun `test ElfCandidate listener receives routed bulk elf update callbacks`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val candidateBulkEventLog = mutableListOf<String>()
      @ElfCandidate
      class CandidateListener : DocumentListener {
        override fun bulkUpdateStarting(hostDocument: Document) {
          assertSame(document, hostDocument)
          candidateBulkEventLog.add("start")
        }
        override fun bulkUpdateFinished(hostDocument: Document) {
          assertSame(document, hostDocument)
          candidateBulkEventLog.add("finish")
        }
        override fun bulkElfUpdateStarting(hostDocument: Document) {
          fail("ElfCandidate listener must receive bulkElfUpdateStarting as bulkUpdateStarting")
        }
        override fun bulkElfUpdateFinished(hostDocument: Document) {
          fail("ElfCandidate listener must receive bulkElfUpdateFinished as bulkUpdateFinished")
        }
      }
      document.addDocumentListener(CandidateListener(), testRootDisposable)
      runWriteCommandAction {
        DocumentUtil.executeInBulk(document, true) {
          assertTrue(document.isInBulkUpdate)
          assertTrue(elfDocument.isInBulkUpdate)
          document.insertString(0, "x")
        }
      }
      assertTextAndSnapshots(document, elfDocument, "xabc")
      assertEquals(listOf("start", "finish"), candidateBulkEventLog)
      assertFalse(document.isInBulkUpdate)
      assertFalse(elfDocument.isInBulkUpdate)
    }
  }

  @Test
  fun `test rebased elf bulk replay restores real bulk mode when later change conflicts`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val realTextBulkModeLog = mutableListOf<Pair<String, Boolean>>()
      val elfTextBulkModeLog = mutableListOf<Pair<String, Boolean>>()
      document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          realTextBulkModeLog.add(event.newFragment.toString() to event.document.isInBulkUpdate)
        }
        override fun elfDocumentChanged(event: DocumentEvent) {
          elfTextBulkModeLog.add(event.newFragment.toString() to event.document.isInBulkUpdate)
        }
        override fun elfDocumentReverted(revertedEvent: DocumentEvent, event: DocumentEvent) {
          elfTextBulkModeLog.add(event.newFragment.toString() to event.document.isInBulkUpdate)
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          DocumentUtil.executeInBulk(document, true) {
            document.insertString(0, "X")
            document.replaceString(2, 3, "Y")
          }
        }
      }
      runWriteCommandAction {
        document.replaceString(1, 2, "Z")
      }
      val elfDocument = getElfDocument(document)
      waitForTextAndAssertSnapshots(document, elfDocument, "XaZc")
      assertContains(realTextBulkModeLog, "X" to true)
      assertContains(elfTextBulkModeLog, "Z" to false)
      assertContains(elfTextBulkModeLog, "X" to true)
      assertFalse(document.isInBulkUpdate)
      assertFalse(elfDocument.isInBulkUpdate)
    }
  }

  @Test
  fun `test elf change is rebased onto real document change`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val revertedEvents = mutableListOf<Pair<DocumentEvent, DocumentEvent>>()
      val changedEvents = mutableListOf<Pair<String, String>>()
      document.addDocumentListener(object : DocumentListener {
        override fun elfDocumentChanged(event: DocumentEvent) {
          changedEvents.add(event.oldFragment.toString() to event.newFragment.toString())
        }
        override fun elfDocumentReverted(revertedEvent: DocumentEvent, event: DocumentEvent) {
          revertedEvents.add(revertedEvent to event)
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          document.insertString(0, "x")
        }
      }
      assertEquals("abc", document.text)
      assertEquals("xabc", elfDocument.text)
      val elfChangeModStamp = elfDocument.modificationStamp
      runWriteCommandAction {
        assertEquals("abc", document.text)
        document.insertString(1, "y")
      }
      val realChangeModStamp = document.modificationStamp
      waitForTextAndAssertSnapshots(document, elfDocument, "xaybc")
      assertNotEquals(elfChangeModStamp, document.modificationStamp)
      assertNotEquals(realChangeModStamp, document.modificationStamp)
      assertEquals(1, revertedEvents.size)
      val (revertedEvent, changeEvent) = revertedEvents.single()
      assertEquals("", revertedEvent.oldFragment.toString())
      assertEquals("x", revertedEvent.newFragment.toString())
      assertEquals("x", changeEvent.oldFragment.toString())
      assertEquals("", changeEvent.newFragment.toString())
      assertEquals(listOf("" to "x", "" to "y", "" to "x"), changedEvents)
    }
  }

  @Test
  fun `test elf change is rolled forward with adjusted offset`() {
    withLockFreeTyping {
      val document = DocumentImpl("abcd")
      val elfDocument = getElfDocument(document)
      val revertedEvents = mutableListOf<Pair<DocumentEvent, DocumentEvent>>()
      val changedEvents = mutableListOf<Pair<Int, Pair<String, String>>>()
      document.addDocumentListener(object : DocumentListener {
        override fun elfDocumentChanged(event: DocumentEvent) {
          changedEvents.add(event.offset to (event.oldFragment.toString() to event.newFragment.toString()))
        }
        override fun elfDocumentReverted(revertedEvent: DocumentEvent, event: DocumentEvent) {
          revertedEvents.add(revertedEvent to event)
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          document.insertString(2, "x")
        }
      }
      assertEquals("abcd", document.text)
      assertEquals("abxcd", elfDocument.text)
      runWriteCommandAction {
        document.insertString(1, "y")
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "aybxcd")
      assertEquals(1, revertedEvents.size)
      assertEquals(listOf(2 to ("" to "x"), 1 to ("" to "y"), 3 to ("" to "x")), changedEvents)
    }
  }

  @Test
  fun `test multiple elf changes are rolled forward after multiple real changes`() {
    withLockFreeTyping {
      val document = DocumentImpl("abcdef")
      val elfDocument = getElfDocument(document)
      val eventLog = mutableListOf<String>()
      document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          eventLog.add("real:${event.offset}:${event.oldFragment}->${event.newFragment}")
        }
        override fun elfDocumentChanged(event: DocumentEvent) {
          eventLog.add("elf:${event.offset}:${event.oldFragment}->${event.newFragment}")
        }
        override fun elfDocumentReverted(revertedEvent: DocumentEvent, event: DocumentEvent) {
          eventLog.add("revert:${event.offset}:${event.oldFragment}->${event.newFragment}")
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          document.insertString(2, "X")
          document.insertString(5, "Y")
        }
      }
      assertEquals("abcdef", document.text)
      assertEquals("abXcdYef", elfDocument.text)
      runWriteCommandAction {
        document.insertString(1, "R")
        document.insertString(4, "S")
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "aRbXcSdYef")
      assertEquals(
        listOf(
          "elf:2:->X",
          "elf:5:->Y",
          "real:1:->R",
          "real:4:->S",
          "revert:5:Y->",
          "revert:2:X->",
          "elf:1:->R",
          "elf:4:->S",
          "elf:3:->X",
          "real:3:->X",
          "elf:7:->Y",
          "real:7:->Y",
        ),
        eventLog,
      )
    }
  }

  @Test
  fun `test later elf change stays reverted when real change replaced its old text`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val eventLog = mutableListOf<String>()
      fun formatEvent(kind: String, event: DocumentEvent): String =
        "$kind@${event.offset}: '${event.oldFragment}' -> '${event.newFragment}'"
      document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          eventLog.add(formatEvent("real", event))
        }
        override fun elfDocumentChanged(event: DocumentEvent) {
          eventLog.add(formatEvent("elf", event))
        }
        override fun elfDocumentReverted(revertedEvent: DocumentEvent, event: DocumentEvent) {
          eventLog.add(formatEvent("revert", event))
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          document.insertString(0, "X")
          document.replaceString(2, 3, "Y")
        }
      }
      assertEquals("abc", document.text)
      assertEquals("XaYc", elfDocument.text)
      runWriteCommandAction {
        document.replaceString(1, 2, "Z")
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "XaZc")
      assertEquals(
        listOf(
          "elf@0: '' -> 'X'",
          "elf@2: 'b' -> 'Y'",
          "real@1: 'b' -> 'Z'",
          "revert@2: 'Y' -> 'b'",
          "revert@0: 'X' -> ''",
          "elf@1: 'b' -> 'Z'",
          "elf@0: '' -> 'X'",
          "real@0: '' -> 'X'",
        ),
        eventLog,
      )
    }
  }

  @Test
  fun `test rebased elf insert is reported as ordinary insert`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val markerBeforeRebasedInsert = document.createRangeMarker(document.textLength, document.textLength)
      val rebasedInsertEvents = mutableListOf<DocumentEvent>()
      document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          if (event.oldLength == 0 && event.newFragment.toString() == "X") {
            rebasedInsertEvents.add(event)
          }
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          document.insertString(document.textLength, "X")
        }
      }
      assertEquals("abc", document.text)
      assertEquals("abcX", elfDocument.text)
      runWriteCommandAction {
        document.insertString(0, "Y")
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "YabcX")
      val rebasedInsertEvent = rebasedInsertEvents.single()
      assertEquals(4, rebasedInsertEvent.offset)
      assertEquals(4, (rebasedInsertEvent as DocumentEventImpl).initialStartOffset)
      assertEquals(rebasedInsertEvent.offset, rebasedInsertEvent.moveOffset)
      assertFalse(DocumentEventUtil.isMoveInsertion(rebasedInsertEvent))
      assertTrue(markerBeforeRebasedInsert.isValid)
      assertEquals(4, markerBeforeRebasedInsert.startOffset)
      assertEquals(4, markerBeforeRebasedInsert.endOffset)
      document.getLineNumber(markerBeforeRebasedInsert.startOffset)
    }
  }

  @Test
  fun `test elf change is reverted when it cannot be rebased`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val revertedEvents = mutableListOf<Pair<DocumentEvent, DocumentEvent>>()
      val changedEvents = mutableListOf<Pair<String, String>>()
      document.addDocumentListener(object : DocumentListener {
        override fun elfDocumentChanged(event: DocumentEvent) {
          changedEvents.add(event.oldFragment.toString() to event.newFragment.toString())
        }
        override fun elfDocumentReverted(revertedEvent: DocumentEvent, event: DocumentEvent) {
          revertedEvents.add(revertedEvent to event)
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          document.replaceString(1, 2, "x")
        }
      }
      assertEquals("abc", document.text)
      assertEquals("axc", elfDocument.text)
      runWriteCommandAction {
        document.replaceString(1, 2, "y")
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "ayc")
      assertEquals(1, revertedEvents.size)
      val (revertedEvent, changeEvent) = revertedEvents.single()
      assertEquals("b", revertedEvent.oldFragment.toString())
      assertEquals("x", revertedEvent.newFragment.toString())
      assertEquals("x", changeEvent.oldFragment.toString())
      assertEquals("b", changeEvent.newFragment.toString())
      assertEquals(listOf("b" to "x", "b" to "y"), changedEvents)
    }
  }

  @Test
  fun `test ElfCandidate routes elf events to document listener methods`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      val candidateDocumentEvents = mutableListOf<Pair<String, String>>()
      val candidateElfEvents = mutableListOf<Pair<String, String>>()
      val regularDocumentEvents = mutableListOf<Pair<String, String>>()
      val regularElfEvents = mutableListOf<Pair<String, String>>()
      @ElfCandidate
      class CandidateListener : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          candidateDocumentEvents.add(event.oldFragment.toString() to event.newFragment.toString())
        }
        override fun elfDocumentChanged(event: DocumentEvent) {
          candidateElfEvents.add(event.oldFragment.toString() to event.newFragment.toString())
        }
      }
      document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          regularDocumentEvents.add(event.oldFragment.toString() to event.newFragment.toString())
        }
        override fun elfDocumentChanged(event: DocumentEvent) {
          regularElfEvents.add(event.oldFragment.toString() to event.newFragment.toString())
        }
        override fun elfDocumentReverted(revertedEvent: DocumentEvent, event: DocumentEvent) {
          regularElfEvents.add(event.oldFragment.toString() to event.newFragment.toString())
        }
      }, testRootDisposable)
      document.addDocumentListener(CandidateListener(), testRootDisposable)
      withElfScope {
        runCommandAction {
          document.insertString(0, "x")
        }
      }
      assertEquals(listOf("" to "x"), candidateDocumentEvents)
      assertEquals(emptyList(), candidateElfEvents)
      assertEquals(emptyList(), regularDocumentEvents)
      assertEquals(listOf("" to "x"), regularElfEvents)
      runWriteCommandAction {
        document.insertString(1, "y")
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "xaybc")
      assertEquals(listOf("" to "x", "x" to "", "" to "y", "" to "x"), candidateDocumentEvents)
      assertEquals(emptyList(), candidateElfEvents)
      assertEquals(listOf("" to "y", "" to "x"), regularDocumentEvents)
      assertEquals(listOf("" to "x", "x" to "", "" to "y", "" to "x"), regularElfEvents)
    }
  }

  @Test
  fun `test reverted elf event exposes elf text to candidate listeners`() {
    withLockFreeTyping {
      val document = DocumentImpl("abcdefghijkl")
      val elfDocument = getElfDocument(document)
      val candidateBeforeDocumentLengths = mutableListOf<Int>()
      val candidateDocumentLengths = mutableListOf<Int>()
      val revertPriorityEvents = mutableListOf<String>()
      lateinit var rangeMarker: RangeMarker
      @ElfCandidate
      class CandidateListener : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {
          candidateBeforeDocumentLengths.add(event.document.textLength)
        }
        override fun documentChanged(event: DocumentEvent) {
          candidateDocumentLengths.add(event.document.textLength)
        }
      }
      @ElfCandidate
      class PrioritizedCandidateListener(
        private val name: String,
        private val listenerPriority: Int,
      ) : DocumentListener, PrioritizedDocumentListener {
        override fun getPriority(): Int = listenerPriority
        override fun beforeDocumentChange(event: DocumentEvent) {
          if (isRevertEvent(event)) {
            revertPriorityEvents.add("before:$name")
          }
        }
        override fun documentChanged(event: DocumentEvent) {
          if (isRevertEvent(event)) {
            revertPriorityEvents.add("after:$name")
          }
        }
        private fun isRevertEvent(event: DocumentEvent): Boolean {
          return event.newFragment.isEmpty() && event.oldFragment.toString() in listOf("x", "y", "z")
        }
      }
      document.addDocumentListener(CandidateListener(), testRootDisposable)
      document.addDocumentListener(PrioritizedCandidateListener("logical", 65), testRootDisposable)
      document.addDocumentListener(PrioritizedCandidateListener("softWrap", 100), testRootDisposable)
      withElfScope {
        runCommandAction {
          document.insertString(document.textLength, "x")
          document.insertString(document.textLength, "y")
          document.insertString(document.textLength, "z")
          rangeMarker = elfDocument.createRangeMarker(0, elfDocument.textLength)
        }
      }
      runWriteCommandAction {
        document.replaceString(0, 1, "A")
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "Abcdefghijklxyz")
      assertEquals(true, rangeMarker.isValid)
      assertEquals(listOf(12, 13, 14, 15, 14, 13, 12, 12, 13, 14), candidateBeforeDocumentLengths)
      assertEquals(listOf(13, 14, 15, 14, 13, 12, 12, 13, 14, 15), candidateDocumentLengths)
      assertEquals(
        listOf(
          "before:softWrap", "before:logical", "after:logical", "after:softWrap",
          "before:softWrap", "before:logical", "after:logical", "after:softWrap",
          "before:softWrap", "before:logical", "after:logical", "after:softWrap",
        ),
        revertPriorityEvents,
      )
    }
  }

  @Test
  fun `test reverted elf move uses current move offset`() {
    withLockFreeTyping {
      val document = DocumentImpl("abcdef")
      val elfDocument = getElfDocument(document)
      val routedEvents = mutableListOf<Triple<String, String, Int>>()
      document.addDocumentListener(object : DocumentListener {
        override fun elfDocumentChanged(event: DocumentEvent) = addEvent(event)
        override fun elfDocumentReverted(revertedEvent: DocumentEvent, event: DocumentEvent) = addEvent(event)
        private fun addEvent(event: DocumentEvent) {
          routedEvents.add(Triple(event.oldFragment.toString(), event.newFragment.toString(), event.moveOffset))
        }
      }, testRootDisposable)
      withElfScope {
        runCommandAction {
          document.moveText(1, 3, 5)
        }
      }
      assertEquals("abcdef", document.text)
      assertEquals("adebcf", elfDocument.text)
      runWriteCommandAction {
        document.insertString(0, "X")
      }
      waitForTextAndAssertSnapshots(document, elfDocument, "Xabcdef")
      assertEquals(
        listOf(
          Triple("", "bc", 1),
          Triple("bc", "", 5),
          Triple("", "bc", 3),
          Triple("bc", "", 1),
          Triple("", "X", 0),
        ),
        routedEvents,
      )
    }
  }

  @Test
  fun `test editor threading write falls back to real write action in modal context`() {
    withLockFreeTyping {
      val modalEntity = Any()
      LaterInvocator.enterModal(modalEntity)
      try {
        var writeAccessAllowedInsideAction = false
        EditorThreading.write {
          writeAccessAllowedInsideAction = ApplicationManager.getApplication().isWriteAccessAllowed
        }
        assertTrue(writeAccessAllowedInsideAction, "modal typing must use a real write action instead of delayed ELF sync")
      } finally {
        LaterInvocator.leaveModal(modalEntity)
      }
    }
  }

  @Test
  fun `test elf document is mutable only within command`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val failure = assertFailsWith<IncorrectOperationException> {
        withElfScope {
          document.insertString(0, "x")
        }
      }
      assertContains(
        failure.message.orEmpty(),
        "Must not change document outside command or undo-transparent action",
        message = "Failure message expected to contain command requirement, but actual: ${failure.message}",
      )
    }
  }

  @Test
  fun `test elf scope does not bypass read only document contract`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val elfDocument = getElfDocument(document)
      document.setReadOnly(true)
      try {
        assertFalse(document.isWritable)
        assertFalse(elfDocument.isWritable)
        assertFailsWith<ReadOnlyModificationException> {
          withElfScope {
            runCommandAction {
              document.insertString(0, "x")
            }
          }
        }
        assertTextAndSnapshots(document, elfDocument, "abc")
      } finally {
        document.setReadOnly(false)
      }
    }
  }

  @Test
  fun `test elf scope does not bypass document write access guard contract`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      DocumentWriteAccessGuard.EP_NAME.point.registerExtension(object : DocumentWriteAccessGuard() {
        override fun isWritable(document0: Document): Result {
          return if (document0 === document) {
            fail("blocked by test guard")
          } else {
            success()
          }
        }
      }, testRootDisposable)
      val failure = assertFailsWith<ReadOnlyModificationException> {
        withElfScope {
          runCommandAction {
            document.insertString(0, "x")
          }
        }
      }
      assertContains(
        failure.message.orEmpty(),
        "blocked by test guard",
        message = "Failure message expected to contain write access guard reason, but actual: ${failure.message}",
      )
      val elfDocument = getElfDocument(document)
      assertTextAndSnapshots(document, elfDocument, "abc")
    }
  }

  @Test
  fun `test elf scope does not bypass guarded block contract`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      document.createGuardedBlock(1, 3)
      document.startGuardedBlockChecking()
      try {
        assertFailsWith<ReadOnlyFragmentModificationException> {
          withElfScope {
            runCommandAction {
              document.insertString(2, "x")
            }
          }
        }
        val elfDocument = getElfDocument(document)
        assertTextAndSnapshots(document, elfDocument, "abc")
      } finally {
        document.stopGuardedBlockChecking()
      }
    }
  }

  @Test
  fun `test elf scope does not bypass line separator contract`() {
    withLockFreeTyping {
      val document = DocumentImpl("abc")
      val failure = assertFailsWith<AssertionError> {
        withElfScope {
          runCommandAction {
            document.insertString(1, "\r")
          }
        }
      }
      assertContains(
        failure.message.orEmpty(),
        "Wrong line separators",
        message = "Failure message expected to contain line separator reason, but actual: ${failure.message}",
      )
      val elfDocument = getElfDocument(document)
      assertTextAndSnapshots(document, elfDocument, "abc")
    }
  }

  private fun runWriteCommandAction(action: () -> Unit) {
    WriteCommandAction.runWriteCommandAction(project) { action() }
  }

  private fun runCommandAction(commandName: String = "", commandGroupId: Any? = null, action: () -> Unit) {
    CommandProcessor.getInstance().executeCommand(project, { action() }, commandName, commandGroupId)
  }

  // Some tests wait for delayed ELF document sync by pumping IDE events.
  // The event-pumping helper expects an existing write-intent lock: it releases that lock while events are dispatched,
  // then reacquires it. Run test bodies under writeIntentReadAction to preserve that contract and the old light-test behavior.
  private fun withLockFreeTyping(action: () -> Unit): Unit = timeoutRunBlocking {
    writeIntentReadAction {
      withLockFreeTypingEnabled(true, action)
    }
  }

  @Suppress("SameParameterValue")
  private fun withLockFreeTypingEnabled(enabled: Boolean, action: () -> Unit) {
    val oldValue = ElfFeatureFlag.isEnabled()
    ElfFeatureFlag.setEnabled(enabled)
    try {
      action()
    } finally {
      ElfFeatureFlag.setEnabled(oldValue)
    }
  }

  private fun dispatchEventsUntilCondition(
    condition: () -> Boolean,
    errorMessage: () -> String,
    releaseWriteIntent: Boolean = true,
  ) {
    if (condition()) {
      return
    }
    if (releaseWriteIntent) {
      TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
        dispatchEventsUntilConditionNoWriteIntent(condition, errorMessage)
      }
    } else {
      dispatchEventsUntilConditionNoWriteIntent(condition, errorMessage)
    }
  }

  private fun dispatchEventsUntilConditionNoWriteIntent(condition: () -> Boolean, errorMessage: () -> String) {
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
        eventQueue.dispatchEvent(eventQueue.nextEvent)
        if (timedOut.get() && !condition()) {
          fail(errorMessage())
        }
      }
    } finally {
      timeout.cancel(false)
    }
  }

  private fun <T> waitForFutureBlocking(future: Future<T>): T {
    try {
      return future.get(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw RuntimeException(e)
    } catch (e: ExecutionException) {
      throw RuntimeException(e)
    } catch (e: TimeoutException) {
      throw RuntimeException(e)
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

  private fun waitForElfSchedulerIdle() {
    val done = AtomicBoolean(false)
    ElfDocumentSyncScheduler.invokeLaterWithWriteAccess {
      done.set(true)
    }
    dispatchEventsUntilCondition(
      condition = { done.get() },
      errorMessage = { "ELF scheduler did not become idle" },
    )
  }

  private fun waitForTextAndAssertSnapshots(document: DocumentImpl, expectedText: String) {
    val elfDocument = getElfDocument(document)
    waitForSync(document, elfDocument, expectedText)
    assertTextAndSnapshots(document, elfDocument, expectedText)
  }

  private fun waitForTextAndAssertSnapshots(doc1: DocumentImpl, doc2: DocumentImpl, expectedText: String) {
    waitForSync(doc1, doc2, expectedText)
    assertTextAndSnapshots(doc1, doc2, expectedText)
  }

  private fun assertTextAndSnapshots(doc1: DocumentImpl, doc2: DocumentImpl, expectedText: String) {
    assertEquals(expectedText, doc1.text)
    assertEquals(expectedText, doc2.text)
    assertSameSnapshot(doc1, doc2)
  }

  private fun assertSameSnapshot(doc1: DocumentImpl, doc2: DocumentImpl) {
    assertSame(doc1.core.snapshot(), doc2.core.snapshot())
  }

  private fun getSnapshot(document: DocumentImpl): DocumentSnapshot {
    return document.core.snapshot()
  }

  private fun withElfScope(action: () -> Unit) {
    Elf.getElf().withElfScope {
      action.invoke()
    }
  }

  private fun getElfDocument(document: Document): DocumentImpl {
    return Elf.getElf().getElfDocument(document) as DocumentImpl
  }

  private fun getRealDocument(document: Document): DocumentImpl {
    return Elf.getElf().getRealDocument(document) as DocumentImpl
  }

  companion object {
    private val projectFixture = projectFixture()

    private const val DEADLOCK_TIMEOUT_SECONDS: Long = 120
  }
}
