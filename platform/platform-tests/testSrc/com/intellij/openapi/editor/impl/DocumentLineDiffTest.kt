// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.PersistentMarkerPolicy
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class DocumentLineDiffTest {
  @Test
  fun `strict translation rejects replaced lines while regular translation keeps their position`() {
    val afterText = "a\nnew\nb"
    val event = event(oldFragment = "a\nold\nb", newFragment = afterText, afterText = afterText)

    assertEquals(-1, event.translateLineViaDiffStrict(1))
    assertEquals(1, event.translateLineViaDiff(1))
    assertEquals(2, event.translateLineViaDiffStrict(2))
  }

  @Test
  fun `computed changes are reused`() {
    val oldFragment = StringBuilder("a\nb")
    val afterText = "x\na\nb"
    val event = event(oldFragment, newFragment = afterText, afterText = afterText)

    assertEquals(2, event.translateLineViaDiffStrict(1))
    oldFragment.setLength(0)
    assertEquals(2, event.translateLineViaDiffStrict(1))
  }

  @Test
  @Timeout(30)
  fun `event line diff supports concurrent readers`() {
    val afterText = "prefix\nalpha\ntarget\nomega"
    val event = event(oldFragment = "alpha\ntarget\nomega", newFragment = afterText, afterText = afterText)

    runConcurrently { workerIndex ->
      if (workerIndex % 2 == 0) {
        assertEquals(2, event.translateLineViaDiffStrict(1))
        assertEquals(2, event.translateLineViaDiff(1))
      }
      else {
        assertEquals(2, event.translateLineViaDiff(1))
        assertEquals(2, event.translateLineViaDiffStrict(1))
      }
    }
  }

  @Test
  @Timeout(30)
  fun `simple patch supports concurrent persistent marker updates`() {
    val document = DocumentImpl(OLD_TEXT, true)
    val initialSnapshot = document.core.snapshot()
    val markerStart = OLD_TEXT.indexOf("target") + 1
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      initialSnapshot,
      markerStart,
      markerStart + 3,
      MarkerSpec(
        isGreedyToLeft = false,
        isGreedyToRight = false,
        policy = PersistentMarkerPolicy,
      ),
    )
    val patch = DocumentTextPatch.simple(
      startOffset = 0,
      endOffset = OLD_TEXT.length,
      newFragment = NEW_TEXT,
      newModStamp = 1,
      clearLineFlags = false,
    )
    val expectedStart = NEW_TEXT.indexOf("target") + 1

    runConcurrently {
      val snapshot = initialSnapshot.applyOp(patch)
      assertEquals(expectedStart, marker.getStartOffset(snapshot))
      assertEquals(expectedStart + 3, marker.getEndOffset(snapshot))
    }
  }

  @Test
  fun `line diff cache distinguishes after-text boundaries`() {
    val oldFragment = "\ntarget\nomega\r"
    val newFragment = "\nalpha\ntarget\nomega\r"

    fun createPatch(): DocumentTextPatch = DocumentTextPatch.simple(
      startOffset = 1,
      endOffset = oldFragment.length + 1,
      newFragment = newFragment,
      newModStamp = 1,
      clearLineFlags = false,
    )

    fun transformPersistentMarker(prefix: String, suffix: String, patch: DocumentTextPatch): Pair<Int, Int> {
      val oldText = prefix + oldFragment + suffix
      val document = DocumentImpl(oldText, true, true)
      val initialSnapshot = document.core.snapshot()
      val markerStart = oldText.indexOf("target") + 1
      val marker = SnapshotMarkerEngineImpl.createRangeMarker(
        document,
        initialSnapshot,
        markerStart,
        markerStart + 3,
        MarkerSpec(
          isGreedyToLeft = false,
          isGreedyToRight = false,
          policy = PersistentMarkerPolicy,
        ),
      )

      val snapshot = initialSnapshot.applyOp(patch)
      return marker.getStartOffset(snapshot) to marker.getEndOffset(snapshot)
    }

    val sharedPatch = createPatch()
    val contexts = listOf("\r" to "\n", "\r" to "y", "x" to "\n", "x" to "y")
    assertEquals(9 to 12, transformPersistentMarker("x", "y", createPatch()))

    for ((prefix, suffix) in contexts) {
      val expected = transformPersistentMarker(prefix, suffix, createPatch())
      assertEquals(expected, transformPersistentMarker(prefix, suffix, sharedPatch))
    }
  }

  private fun event(oldFragment: CharSequence, newFragment: CharSequence, afterText: String): DocumentEventImpl {
    val document = DocumentImpl(afterText, true)
    return DocumentEventImpl(
      document,
      0,
      oldFragment,
      newFragment,
      document.modificationStamp,
      false,
      0,
      oldFragment.length,
      0,
      oldFragment.length,
    )
  }

  private fun runConcurrently(action: (Int) -> Unit) {
    val threadCount = 8
    val executor = Executors.newFixedThreadPool(threadCount)
    val start = CountDownLatch(1)
    try {
      val futures = List(threadCount) { workerIndex ->
        executor.submit {
          start.await()
          repeat(100) {
            action(workerIndex)
          }
        }
      }
      start.countDown()
      futures.forEach { it.get() }
    }
    finally {
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
    }
  }

  companion object {
    private const val OLD_TEXT = "alpha\ntarget\nomega"
    private const val NEW_TEXT = "prefix\nalpha\ntarget\nomega"
  }
}
