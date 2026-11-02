// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

@UsePMarkerImplementation
internal class PMarkerRootOverlapTraversalTest {
  private lateinit var markerForHandles: SnapshotRangeMarkerImpl

  @BeforeEach
  fun setUp() {
    markerForHandles = DocumentImpl("", true).createRangeMarker(0, 0) as SnapshotRangeMarkerImpl
  }

  @AfterEach
  fun tearDown() {
    if (::markerForHandles.isInitialized) markerForHandles.dispose()
  }

  @Test
  fun `overlap queries match a scan after lazy shifts`() {
    val root = populatedRoot()
    val text = DocumentText.createText("x".repeat(256))
    val prefix = DocumentPatch.simple(0, 0, "abc", 1, false)
    val shifted = root.applyPatch(prefix, text, text.insert(0, "abc"))
    val middle = DocumentPatch.simple(100, 100, "abcd", 2, false)
    val edited = root.applyPatch(middle, text, text.insert(100, "abcd"))
    val first = root.overlappingIterator(0, 256, 0).next()
    val flavored = root.updateFlavor(first.handle, (first.flavorFlags.toInt() xor 7).toByte())
    val restored = root.restoreEntries(listOf(first.copy(nodeStart = 240, nodeEnd = 245)))
    val random = Random(42)

    for (current in listOf(PMarkerDirectRoot.empty(), root, shifted, edited, flavored, restored)) {
      val entries = ArrayList<MarkerEntry>()
      current.processAllRangeMarkers { entry, delta ->
        entries.add(entry.copy(nodeStart = entry.nodeStart + delta, nodeEnd = entry.nodeEnd + delta))
        true
      }
      repeat(200) {
        val start = random.nextInt(270)
        val end = start + random.nextInt(30)
        for (flags in 0..7) {
          val expected = entries.filter {
            it.nodeStart <= end && it.nodeEnd >= start && it.flavorFlags.toInt() and flags == flags
          }.map { it.handle }
          val actual = ArrayList<SnapshotMarkerReference<*>>()
          assertTrue(current.processRangeMarkersOverlappingWith(start, end, flags) {
            actual.add(it.handle)
            true
          })
          assertEquals(expected, actual, "Range [$start, $end], flags $flags")
        }
      }
    }
  }

  @Test
  fun `the processor can stop and start a nested query`() {
    val root = populatedRoot()
    val expected = ArrayList<SnapshotMarkerReference<*>>()
    root.processAllRangeMarkers { entry, _ ->
      expected.add(entry.handle)
      true
    }

    for (limit in listOf(1, 8, expected.size)) {
      val actual = ArrayList<SnapshotMarkerReference<*>>()
      assertFalse(root.processRangeMarkersOverlappingWith(0, 256, 0) {
        val nested = ArrayList<SnapshotMarkerReference<*>>()
        assertTrue(root.processRangeMarkersOverlappingWith(0, 256, 0) { entry ->
          nested.add(entry.handle)
          true
        })
        assertEquals(expected, nested)
        actual.add(it.handle)
        actual.size < limit
      })
      assertEquals(expected.take(limit), actual)
    }
  }

  private fun populatedRoot(): PMarkerDirectRoot {
    val root = PMarkerDirectRoot.empty()
    val random = Random(17)
    val entries = List(128) { index ->
      val start = random.nextInt(200)
      MarkerEntry(SnapshotMarkerReference.create(markerForHandles, false), start, start + random.nextInt(20), 0, (index % 8).toByte())
    }
    return root.insertMany(entries)
  }
}
