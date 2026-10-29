// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.LazyRangeMarkerFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentCore
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.MarkupModelImpl
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * A text change publishes a new snapshot while marker creation reads the previous snapshot.
 * The marker must remain valid and appear in the document.
 */
@UsePMarkerImplementation
@TestApplication
internal class SnapshotMarkerPublicationRaceTest {
  private companion object {
    val projectFixture = projectFixture()
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `a range marker created during a text change stays valid`(surviveOnExternalChange: Boolean) {
    val snapshots = PausedSnapshotRead(DocumentImpl("abcdefgh", true).core)
    val document = DocumentImpl(snapshots, null)

    val marker = createDuringTextChange(
      snapshots,
      textChange = { document.insertString(0, "x") },
      create = { document.createRangeMarker(2, 4, surviveOnExternalChange) },
    )

    assertTrue(marker.isValid) {
      "The marker is lost: $marker"
    }
    // The creation takes effect either before the insert or after it.
    assertTrue(marker.textRange == TextRange(3, 5) || marker.textRange == TextRange(2, 4)) {
      "Unexpected range: ${marker.textRange}"
    }
    assertTrue(containsMarker(document, marker)) {
      "The document does not list the marker"
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `a range marker rejects offsets beyond the new snapshot length`(surviveOnExternalChange: Boolean) {
    val snapshots = PausedSnapshotRead(DocumentImpl("abcdefgh", true).core)
    val document = DocumentImpl(snapshots, null)

    val failure = assertThrows(ExecutionException::class.java) {
      createDuringTextChange(
        snapshots,
        textChange = { document.deleteString(0, 7) },
        create = { document.createRangeMarker(2, 4, surviveOnExternalChange) },
      )
    }

    assertTrue(failure.cause is IllegalArgumentException)
    assertEquals("Marker range exceeds snapshot length", failure.cause?.message)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `a file marker created during a text change stays valid`(persistent: Boolean) {
    val factory = LazyRangeMarkerFactory.getInstance(projectFixture.get())
    val file = LightVirtualFile("test.txt", "abcdefgh")
    val snapshots = PausedSnapshotRead(DocumentImpl("abcdefgh", true).core, pauseOnlyInRootUpdate = true)
    val document = DocumentImpl(snapshots, null)
    FileDocumentManagerBase.registerDocument(document, file)
    val initialMarker = factory.createRangeMarker(file, 0, 1, persistent)

    val marker = changeFileMarkerDuringTextChange(snapshots, document) {
      factory.createRangeMarker(file, 0, 2, persistent)
    }

    assertTrue(initialMarker.isValid)
    assertEquals(2, initialMarker.startOffset)
    assertTrue(marker.isValid) { "The file marker is lost: $marker" }
    assertTrue(containsMarker(document, marker))
  }

  @Test
  fun `a file marker keeps its greediness after a concurrent text change`() {
    val factory = LazyRangeMarkerFactory.getInstance(projectFixture.get())
    val file = LightVirtualFile("test.txt", "abcdefgh")
    val snapshots = PausedSnapshotRead(DocumentImpl("abcdefgh", true).core, pauseOnlyInRootUpdate = true)
    val document = DocumentImpl(snapshots, null)
    FileDocumentManagerBase.registerDocument(document, file)
    val marker = factory.createRangeMarker(file, 0, 2, false)

    changeFileMarkerDuringTextChange(snapshots, document) { marker.isGreedyToLeft = true }

    assertTrue(marker.isValid)
    assertEquals(3, marker.startOffset)
    assertTrue(marker.isGreedyToLeft)
    val root = checkNotNull(document.rangeMarkers.rootStore().root(document.core.snapshot()))
    val entry = root.overlappingIterator(0, document.textLength, 0).asSequence().single()
    assertTrue(MarkerSpec.isGreedyToLeft(entry.spec)) { "The root lost the greediness change" }
  }

  @ParameterizedTest
  @EnumSource(HighlighterTargetArea::class)
  fun `a highlighter rejects offsets beyond the new snapshot length`(targetArea: HighlighterTargetArea) {
    val snapshots = PausedSnapshotRead(DocumentImpl("abcdefgh", true).core)
    val document = DocumentImpl(snapshots, null)
    val model = object : MarkupModelImpl(document) {}
    try {
      val failure = assertThrows(ExecutionException::class.java) {
        createDuringTextChange(
          snapshots,
          textChange = { document.deleteString(0, 7) },
          create = { model.addRangeHighlighter(2, 4, 0, null, targetArea) },
        )
      }

      assertTrue(failure.cause is IllegalArgumentException)
      assertTrue(model.allHighlighters.isEmpty())
    }
    finally {
      model.dispose()
    }
  }

  private fun <T> changeFileMarkerDuringTextChange(snapshots: PausedSnapshotRead, document: DocumentImpl, change: () -> T): T {
    val published = CountDownLatch(1)
    val listener = object : PrioritizedDocumentListener {
      override fun getPriority(): Int = Int.MIN_VALUE

      override fun documentChanged(event: DocumentEvent) {
        published.countDown()
      }
    }
    document.addDocumentListener(listener)
    val textChange = FutureTask { document.insertString(0, "x") }
    val writer = Thread(textChange, "File marker text change")
    try {
      return createDuringTextChange(
        snapshots,
        textChange = {
          writer.start()
          check(published.await(10, TimeUnit.SECONDS)) { "The text change did not publish" }
        },
        create = change,
      ).also { textChange.get(10, TimeUnit.SECONDS) }
    }
    finally {
      snapshots.resume()
      writer.join(TimeUnit.SECONDS.toMillis(10))
      document.removeDocumentListener(listener)
      check(!writer.isAlive) { "The text change did not stop" }
    }
  }

  /**
   * Pauses [create] after it reads a snapshot. Runs [textChange], then lets [create] finish with the previous snapshot.
   */
  private fun <T> createDuringTextChange(snapshots: PausedSnapshotRead, textChange: () -> Unit, create: () -> T): T {
    val creation = FutureTask(create)
    val creator = Thread(creation, "Marker creation")
    snapshots.pauseIn(creator)
    creator.start()
    try {
      snapshots.awaitStart()
      textChange()
      snapshots.resume()
      return creation.get(10, TimeUnit.SECONDS)
    }
    finally {
      snapshots.resume()
      creator.join(TimeUnit.SECONDS.toMillis(10))
      check(!creator.isAlive) {
        "The marker creation did not stop"
      }
    }
  }

  private fun containsMarker(document: DocumentImpl, marker: RangeMarker): Boolean {
    var found = false
    document.processRangeMarkers {
      found = it === marker
      !found
    }
    return found
  }

  /**
   * Pauses one snapshot read on the selected thread. Other threads can still read and change the document.
   */
  private class PausedSnapshotRead(
    private val delegate: DocumentCore,
    private val pauseOnlyInRootUpdate: Boolean = false,
  ) : DocumentCore by delegate {
    private val started: CountDownLatch = CountDownLatch(1)
    private val resumed: CountDownLatch = CountDownLatch(1)
    @Volatile
    private var pausedThread: Thread? = null

    override fun snapshot(): DocumentBranch {
      val snapshot = delegate.snapshot()
      if (Thread.currentThread() === pausedThread && (!pauseOnlyInRootUpdate || isInRootUpdate())) {
        pausedThread = null
        started.countDown()
        check(resumed.await(10, TimeUnit.SECONDS)) {
          "The marker creation was not resumed"
        }
      }
      return snapshot
    }

    private fun isInRootUpdate(): Boolean = Thread.currentThread().stackTrace.any {
      it.className == VirtualFileMarkerRoot::class.java.name && it.methodName.startsWith("updateRootAtomically")
    }

    fun pauseIn(thread: Thread) {
      pausedThread = thread
    }

    fun awaitStart() {
      check(started.await(10, TimeUnit.SECONDS)) {
        "The marker creation did not read a snapshot"
      }
    }

    fun resume() {
      resumed.countDown()
    }
  }

  @Test
  fun `a guarded block created during a text change still guards its text`() {
    val snapshots = PausedSnapshotRead(DocumentImpl("abcdefgh", true).core)
    val document = DocumentImpl(snapshots, null)
    val block = createDuringTextChange(
      snapshots,
      textChange = { document.insertString(0, "x") },
      create = { document.createGuardedBlock(2, 4) },
    )
    assertNotNull(document.getRangeGuard(block.startOffset, block.endOffset)) {
      "The guarded block is lost: $block"
    }
    document.startGuardedBlockChecking()
    try {
      assertThrows(ReadOnlyFragmentModificationException::class.java) {
        document.insertString(block.startOffset + 1, "y")
      }
    }
    finally {
      document.stopGuardedBlockChecking()
    }
  }
}
