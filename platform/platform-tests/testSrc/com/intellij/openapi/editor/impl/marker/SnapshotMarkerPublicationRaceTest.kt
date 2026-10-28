// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.DocumentCore
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.RangeMarkersImpl
import com.intellij.openapi.util.TextRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * A text change publishes a new snapshot while marker creation reads the previous snapshot.
 * The marker must remain valid and appear in the document.
 */
internal class SnapshotMarkerPublicationRaceTest {

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `a range marker created during a text change stays valid`(surviveOnExternalChange: Boolean) {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
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
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `a range marker rejects offsets beyond the new snapshot length`(surviveOnExternalChange: Boolean) {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
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
  private class PausedSnapshotRead(private val delegate: DocumentCore) : DocumentCore by delegate {
    private val started: CountDownLatch = CountDownLatch(1)
    private val resumed: CountDownLatch = CountDownLatch(1)
    @Volatile
    private var pausedThread: Thread? = null

    override fun snapshot(): DocumentBranch {
      val snapshot = delegate.snapshot()
      if (Thread.currentThread() === pausedThread) {
        pausedThread = null
        started.countDown()
        check(resumed.await(10, TimeUnit.SECONDS)) {
          "The marker creation was not resumed"
        }
      }
      return snapshot
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
}
