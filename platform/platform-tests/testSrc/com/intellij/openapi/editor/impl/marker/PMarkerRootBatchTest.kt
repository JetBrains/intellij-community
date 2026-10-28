// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.testFramework.junit5.TestApplication
import it.unimi.dsi.fastutil.longs.LongArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

@TestApplication
@UsePMarkerImplementation
class PMarkerRootBatchTest {
  private val markerForHandles: SnapshotRangeMarkerImpl by lazy {
    SnapshotMarkerEngineImpl.createRangeMarker(DocumentImpl("", true), 0, 0, 0, false, DefaultMarkerPolicy) as SnapshotRangeMarkerImpl
  }
  private val handles: ArrayList<SnapshotMarkerReference<*>> = ArrayList()

  private fun handle(label: Long): SnapshotMarkerReference<*> {
    while (handles.size <= label) handles.add(PMarkerRootImpl.empty().createHandle(markerForHandles, false))
    return handles[label.toInt()]
  }

  private fun label(handle: SnapshotMarkerReference<*>): Long = handles.indexOf(handle).toLong()

  @Test
  fun `marker changes share offsets without changing old roots`() {
    val root = applyPatch(PMarkerRootImpl.empty().insertMany(entries()), TEXT, 0, 0, "abcd")
    root.resolve(handle(12))
    val cache = checkNotNull(offsetCache(root))
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val roots = listOf(
      root,
      root.insert(handle(100), 3, 4, spec, flavorFlags = 0),
      root.insertMany(listOf(entry(101, 1, 2), entry(102, 2, 3))),
      root.updateSpec(handle(12), spec),
      root.updateFlavor(handle(12), 0),
      root.updateMeasure(handle(1), 99),
      root.remove(handle(12)),
      root.removeMany(listOf(handle(12))),
      root.purgeMany(listOf(handle(12))),
    )

    repeat(2) { pass ->
      for (current in roots.shuffled(Random(pass))) {
        assertSame(cache, offsetCache(current))
        val expected = resolvedEntries(current).associateBy { label(it.handle) }
        for (id in ((1L..48L).toList() + (100L..102L).toList()).shuffled(Random(pass))) {
          val entry = expected[id]
          val resolution = if (entry == null) PMarkerResolution.ABSENT
          else PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(entry.nodeStart, entry.nodeEnd))
          assertEquals(resolution, current.resolve(handle(id)), "Marker $id")
        }
      }
    }

    val edited = applyPatch(root, "abcd$TEXT", 0, 0, "x")
    assertNull(offsetCache(edited))
    assertEquals(TextRangeScalarUtil.shift(root.resolve(handle(12)), 1), edited.resolve(handle(12)))
    assertNotSame(cache, offsetCache(edited))
  }

  @Test
  fun `shared cache rejects conflicting offsets for one marker ID`() {
    val root = PMarkerRootImpl.empty().insertMany(entries())
    root.resolve(handle(12))
    val inserted = root.insertMany(listOf(entry(100, 1, 2)))

    assertThrows(AssertionError::class.java) { root.insertMany(listOf(entry(100, 3, 4))) }
    assertEquals(PMarkerResolution.ABSENT, root.resolve(handle(100)))
    assertEquals(TextRangeScalarUtil.toScalarRange(1, 2), inserted.resolve(handle(100)))
  }

  @Test
  fun `cached absolute offsets preserve shifted roots and removals`() {
    val initial = PMarkerRootImpl.empty().insertMany(entries() + entry(100, 0, 0))
    val shifted = applyPatch(initial, TEXT, 0, 0, "abcd")
    val edited = applyPatch(shifted, "abcd$TEXT", 10, 12, "")
    edited.resolve(handle(12))
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false, isStickingToRight = false)
    val roots = listOf(initial, shifted, edited, edited.updateSpec(handle(12), spec))

    for (root in roots) {
      val expected = resolvedEntries(root).associateBy { label(it.handle) }
      repeat(2) { pass ->
        for (id in expected.keys.shuffled(Random(pass))) {
          val entry = expected.getValue(id)
          val resolution = root.resolve(handle(id))
          assertTrue(PMarkerResolution.isValid(resolution))
          assertEquals(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(entry.nodeStart, entry.nodeEnd)), resolution)
        }
      }
      val entry = expected.getValue(12)
      val removed = root.remove(handle(12))
      assertTrue(PMarkerResolution.isAbsent(removed.resolve(handle(12))))
      assertEquals(PMarkerResolution.ABSENT, removed.resolve(handle(12)))
      assertEquals(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(entry.nodeStart, entry.nodeEnd)), root.resolve(handle(12)))
    }
  }

  @Test
  fun `cached absolute offsets support empty and maximum ranges`() {
    val root = PMarkerRootImpl.empty().insertMany(listOf(entry(1, 0, 0), entry(2, Int.MAX_VALUE - 1, Int.MAX_VALUE)))

    repeat(2) {
      assertEquals(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(0, 0)), root.resolve(handle(1)))
      assertEquals(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(Int.MAX_VALUE - 1, Int.MAX_VALUE)), root.resolve(handle(2)))
    }
  }

  @Test
  fun `insert batch preserves shifted markers and entry metadata`() {
    val initial = insertIndividually(PMarkerRootImpl.empty(), entries())
    val shifted = applyPatch(initial, TEXT, 0, 0, "xx").remove(handle(7))
    val additions = listOf(entry(100, 3, 3, measure = 5), entry(102, 10, 12), entry(101, 3, 6))
    val before = resolvedEntries(shifted)

    val actual = shifted.insertMany(additions)
    val expected = insertIndividually(shifted, additions)

    assertEquivalent(expected, actual, (1L..48L).toList() + listOf(100L, 101L, 102L))
    assertEquals(before, resolvedEntries(shifted))
    assertEquivalent(insertIndividually(PMarkerRootImpl.empty(), entries()),
                     PMarkerRootImpl.empty().insertMany(entries()),
                     (1L..48L).toList())
  }

  @Test
  fun `remove and purge batches handle shifted invalid absent and repeated IDs`() {
    val initial = PMarkerRootImpl.empty().insertMany(entries())
    val shifted = applyPatch(initial, TEXT, 0, 0, "xx")
    val root = applyPatch(shifted, "xx$TEXT", 3, 7, "").remove(handle(7))
    val ids = LongArrayList(longArrayOf(1, 2, 7, 24, 36, 999, 36))
    val before = resolvedEntries(root)
    val removed = root.removeMany(ids.map(::handle))
    val purged = root.purgeMany(ids.map(::handle))

    val individuallyRemoved = ids.fold(root) { current, id -> current.remove(handle(id)) }
    assertEquivalent(individuallyRemoved, removed, (1L..48L).toList())
    assertEquivalent(individuallyRemoved, purged, (1L..48L).toList(), ids.toSet())
    assertEquivalent(individuallyRemoved, removed.purgeMany(ids.map(::handle)), (1L..48L).toList(), ids.toSet())
    assertTrue((removed as PMarkerRootImpl).containsMarker(handle(24)))
    assertFalse((purged as PMarkerRootImpl).containsMarker(handle(24)))
    assertEquals(PMarkerResolution.ABSENT, removed.resolve(handle(24)))
    assertEquals(PMarkerResolution.ABSENT, purged.resolve(handle(24)))
    assertEquals(before, resolvedEntries(root))

    val beforeText = "xx$TEXT".removeRange(3, 7)
    val patchText = beforeText.take(20)
    assertEquivalent(
      applyPatch(ids.fold(root) { current, id -> current.remove(handle(id)) }, beforeText, 0, beforeText.length, patchText),
      applyPatch(removed, beforeText, 0, beforeText.length, patchText),
      (1L..48L).toList(),
    )
  }

  @Test
  fun `unchanged batches retain root identity`() {
    val root = PMarkerRootImpl.empty().insertMany(entries()).remove(handle(1))
    val unknownIds = LongArrayList(longArrayOf(999, 999))

    assertSame(root, root.insertMany(emptyList()))
    assertSame(root, root.removeMany(emptyList()))
    assertSame(root, root.purgeMany(emptyList()))
    assertSame(root, root.removeMany(unknownIds.map(::handle)))
    assertSame(root, root.purgeMany(unknownIds.map(::handle)))
    assertSame(root, root.removeMany(listOf(handle(1), handle(1), handle(999))))
  }

  @Test
  fun `batches maintain the persistent policy count`() {
    var transformed = 0
    val countingPolicy = MarkerPolicy { marker, _, _, _ ->
      transformed++
      MarkerTransformResult(marker)
    }
    val root = PMarkerRootImpl.empty().insertMany(listOf(
      entry(1, 0, 0).copy(spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false), policy = countingPolicy),
      entry(2, 5, 6).copy(spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false), policy = PersistentMarkerPolicy),
    ))

    applyPatch(root, "0123456789", 1, 9, "")
    assertEquals(1, transformed)
    for (reduced in listOf(root.removeMany(listOf(handle(2))), root.purgeMany(listOf(handle(2))))) {
      transformed = 0
      applyPatch(reduced, "0123456789", 1, 9, "")
      assertEquals(0, transformed)
    }
  }

  @Test
  fun `invalid insert batches leave the receiver unchanged`() {
    val root = PMarkerRootImpl.empty().insertMany(entries())
    val before = resolvedEntries(root)
    val valid = entry(100, 1, 2)
    val invalidEntries = listOf(entry(1, 1, 2), entry(100, 1, 2), entry(101, -1, 2), entry(102, 2, 1))

    for (invalid in invalidEntries) {
      assertThrows(IllegalArgumentException::class.java) { root.insertMany(listOf(valid, invalid)) }
      assertEquals(before, resolvedEntries(root))
      assertFalse((root as PMarkerRootImpl).containsMarker(handle(100)))
    }
  }

  @Test
  fun `random batches match individual operations through text changes`() {
    val random = Random(0x5EED)
    var expected: PMarkerRoot = PMarkerRootImpl.empty()
    var actual: PMarkerRoot = PMarkerRootImpl.empty()
    val purgedIds = HashSet<Long>()
    var nextId = 1L
    var text = TEXT
    repeat(300) {
      when (random.nextInt(4)) {
        0 -> {
          val additions = List(random.nextInt(1, 9)) {
            val start = random.nextInt(text.length + 1)
            val end = random.nextInt(start, text.length + 1)
            entry(nextId++, start, end, if (start == end) random.nextInt(10) else 0)
          }
          expected = insertIndividually(expected, additions)
          actual = actual.insertMany(additions)
          additions.forEach { purgedIds.remove(label(it.handle)) }
        }
        1 -> {
          val ids = LongArrayList(LongArray(random.nextInt(9)) { random.nextLong(1, nextId + 5) })
          expected = ids.fold(expected) { root, id -> root.remove(handle(id)) }
          actual = actual.removeMany(ids.map(::handle))
        }
        2 -> {
          val ids = LongArrayList(LongArray(random.nextInt(9)) { random.nextLong(1, nextId + 5) })
          expected = ids.fold(expected) { root, id -> root.remove(handle(id)) }
          actual = actual.purgeMany(ids.map(::handle))
          purgedIds.addAll(ids)
        }
        3 -> {
          val start = random.nextInt(text.length + 1)
          val end = random.nextInt(start, text.length + 1)
          val replacement = "xyz".take(random.nextInt(4))
          expected = applyPatch(expected, text, start, end, replacement)
          actual = applyPatch(actual, text, start, end, replacement)
          text = text.replaceRange(start, end, replacement)
        }
      }
      assertEquivalent(expected, actual, (1L until nextId).toList(), purgedIds)
    }
  }

  private fun offsetCache(root: PMarkerRoot): Any? = PMarkerRootImpl::class.java.getDeclaredField("cachedOffsets").apply {
    isAccessible = true
  }.get(root)

  private fun entries(): List<MarkerEntry> = List(48) { index ->
    val start = index / 2 + 2
    val end = if (index % 3 == 0) start else start + 2
    entry(index + 1L, start, end, if (start == end) index + 1 else 0)
  }

  private fun entry(id: Long, start: Int, end: Int, measure: Int = 0): MarkerEntry = MarkerEntry(
    handle(id),
    start,
    end,
    MarkerSpec.create(id % 2 == 0L, measure == 0 && id % 3 == 0L, id % 4 == 0L),
    (1 shl (id % 8).toInt()).toByte(),
    measure = measure,
    policy = if (id % 5 == 0L) PersistentMarkerPolicy else DefaultMarkerPolicy,
  )

  private fun insertIndividually(root: PMarkerRoot, entries: List<MarkerEntry>): PMarkerRoot = entries.fold(root) { current, entry ->
    current.insert(entry.handle, entry.nodeStart, entry.nodeEnd, entry.spec, entry.flavorFlags, entry.measure,
                   entry.policy)
  }

  private fun resolvedEntries(root: PMarkerRoot): List<MarkerEntry> {
    val entries = ArrayList<MarkerEntry>()
    root.processAllRangeMarkers { entry, delta ->
      entries.add(entry.copy(nodeStart = entry.nodeStart + delta, nodeEnd = entry.nodeEnd + delta))
      true
    }
    return entries
  }

  private fun assertEquivalent(expected: PMarkerRoot, actual: PMarkerRoot, ids: List<Long>, purgedIds: Set<Long> = emptySet()) {
    assertEquals(resolvedEntries(expected), resolvedEntries(actual))
    assertEquals(expected.isEmpty(), actual.isEmpty())
    for (id in ids) {
      val expectedResolution = if (id in purgedIds) PMarkerResolution.ABSENT else expected.resolve(handle(id))
      val actualResolution = actual.resolve(handle(id))
      assertEquals(expectedResolution, actualResolution, "Marker $id")
      assertEquals(if (id in purgedIds) null else expected.invalidationReason(handle(id)),
                   actual.invalidationReason(handle(id)),
                   "Marker $id")
      val expectedContains = id !in purgedIds && (expected as PMarkerRootImpl).containsMarker(handle(id))
      assertEquals(expectedContains, (actual as PMarkerRootImpl).containsMarker(handle(id)), "Marker $id")
    }
    for (offset in -1..32) {
      assertEquals(expected.getPrefixAggregate(offset), actual.getPrefixAggregate(offset), "Offset $offset")
    }
    for (flavor in listOf(0, 1, 2, 3, 0x80)) {
      val expectedIds = expected.overlappingIterator(2, 12, flavor).asSequence().map { label(it.handle) }.toList()
      val actualIds = actual.overlappingIterator(2, 12, flavor).asSequence().map { label(it.handle) }.toList()
      assertEquals(expectedIds, actualIds)
    }
  }

  private fun applyPatch(root: PMarkerRoot, text: String, start: Int, end: Int, replacement: String): PMarkerRoot {
    val before = DocumentImpl(text, true).core.snapshot().text()
    val patch = DocumentPatch.simple(start, end, replacement, newModStamp = 1, clearLineFlags = false)
    val after = before.replace(start, end, replacement)
    return root.applyPatch(patch, before, after)
  }

  companion object {
    private const val TEXT: String = "01234567890123456789012345678901234567890123456789"
  }
}
