// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.containers.ConcurrentLongLongMap
import com.intellij.util.ref.GCWatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@TestApplication
@UsePMarkerImplementation
internal class PMarkerDirectRootTest {
  private val empty: PMarkerDirectRoot = PMarkerDirectRoot.empty()
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
  fun `factory creates a root with direct marker links`() {
    assertTrue(PMarkerRoot.empty() is PMarkerDirectRoot)
  }

  @Test
  fun `cached resolutions advance across insertions deletions and replacements`() {
    val entries = listOf(entry(1, 3), entry(4, 6), entry(5, 10), entry(6, 6), entry(7, 8), entry(9, 9), entry(9, 12), entry(14, 16))
    val root = empty.insertMany(entries)
    val patches = listOf(
      DocumentPatch.simple(6, 6, "xx", 1, false),
      DocumentPatch.simple(6, 9, "", 1, false),
      DocumentPatch.simple(6, 9, "x", 1, false),
      DocumentPatch.simple(6, 9, "xxx", 1, false),
    )
    for (patch in patches) {
      val edited = applyPatch(root, patch)
      for ((handle, start, end) in entries) {
        val previous = root.resolve(handle)
        val expected = if (end < patch.startOffset() || start > patch.endOffset()) {
          edited.resolve(handle)
        }
        else PMarkerResolution.UNRESOLVED
        assertEquals(expected, edited.advanceResolution(root.resolutionCacheIdentity, previous))
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `policy changes before the edit disable the prefix shortcut`(invalidate: Boolean) {
    val before = entry(0, 1)
    val changed = entry(2, 4).copy(policy = MarkerPolicy { entry, _, _, _ ->
      if (invalidate) MarkerTransformResult(MarkerTransformError.INVALIDATED_BY_EDIT)
      else MarkerTransformResult(entry.nodeStart, 5)
    })
    val containing = entry(6, 20)
    val root = empty.insertMany(listOf(before, changed, containing))
    val edited = applyPatch(root, DocumentPatch.simple(10, 10, "x", 1, false))

    val expected = if (invalidate) PMarkerResolution.invalid(TextRangeScalarUtil.toScalarRange(2, 4))
    else TextRangeScalarUtil.toScalarRange(2, 5)
    assertEquals(expected, edited.resolve(changed.handle))
    assertEquals(PMarkerResolution.UNRESOLVED, edited.advanceResolution(root.resolutionCacheIdentity, root.resolve(changed.handle)))
    assertEquals(root.resolve(before.handle), edited.advanceResolution(root.resolutionCacheIdentity, root.resolve(before.handle)))
  }

  @Test
  fun `metadata changes preserve advancement and membership changes discard it`() {
    val first = entry(1, 3)
    val second = entry(12, 12)
    val root = empty.insertMany(listOf(first, second))
    val source = root.updateFlavor(first.handle, 2)
    val edited = applyPatch(source, DocumentPatch.simple(8, 8, "xx", 1, false))
    val metadata = edited.updateSpec(first.handle, MarkerSpec.create(isGreedyToLeft = true, isGreedyToRight = false))
      .updateFlavor(first.handle, 4)
      .updateMeasure(second.handle, 99)

    for (entry in listOf(first, second)) {
      assertEquals(metadata.resolve(entry.handle), metadata.advanceResolution(root.resolutionCacheIdentity, root.resolve(entry.handle)))
    }
    val addition = entry(20, 22)
    val changedRoots = listOf(
      metadata.insertMany(listOf(addition)),
      metadata.remove(first.handle),
      metadata.purgeMany(listOf(first.handle)),
      metadata.restoreEntries(listOf(second.copy(nodeStart = 30, nodeEnd = 32))),
      metadata.mergeValidMarkersFrom(metadata.insertMany(listOf(addition))),
      PMarkerDirectRoot.empty(),
    )
    for (changedRoot in changedRoots) {
      assertEquals(PMarkerResolution.UNRESOLVED, changedRoot.advanceResolution(root.resolutionCacheIdentity, root.resolve(second.handle)))
    }
  }

  @Test
  fun `advancement requires the source identity and a valid resolution`() {
    val entry = entry(12, 14)
    val root = empty.insertMany(listOf(entry))
    val edited = applyPatch(root, DocumentPatch.simple(8, 8, "xx", 1, false))
    val branch = applyPatch(root, DocumentPatch.simple(8, 9, "", 1, false))
    val next = applyPatch(edited, DocumentPatch.simple(8, 8, "x", 2, false), "x".repeat(102))
    val previous = root.resolve(entry.handle)

    assertEquals(TextRangeScalarUtil.toScalarRange(14, 16), edited.advanceResolution(root.resolutionCacheIdentity, previous))
    assertEquals(TextRangeScalarUtil.toScalarRange(11, 13), branch.advanceResolution(root.resolutionCacheIdentity, previous))
    assertEquals(PMarkerResolution.UNRESOLVED, edited.advanceResolution(branch.resolutionCacheIdentity, previous))
    assertEquals(PMarkerResolution.UNRESOLVED, next.advanceResolution(root.resolutionCacheIdentity, previous))
    assertEquals(next.resolve(entry.handle), next.advanceResolution(edited.resolutionCacheIdentity, edited.resolve(entry.handle)))
    for (resolution in listOf(PMarkerResolution.UNRESOLVED, PMarkerResolution.ABSENT, PMarkerResolution.invalid(previous))) {
      assertEquals(PMarkerResolution.UNRESOLVED, edited.advanceResolution(root.resolutionCacheIdentity, resolution))
    }
  }

  @Test
  fun `moves and full traversals require normal resolution`() {
    val entry = entry(1, 3)
    val persistent = entry(20, 24).copy(policy = PersistentMarkerPolicy)
    val root = empty.insertMany(listOf(entry, persistent))
    val moved = applyPatch(root, DocumentPatch.complex(10, 10, "xx", 1, false, 10, 10, 30))
    val before = DocumentImpl("x".repeat(100), true).core.snapshot().text()
    val patch = DocumentPatch.simple(10, 95, "y".repeat(85), 1, false)
    val after = before.replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
    assertTrue(PersistentMarkerPolicy.requiresFullTraversal(patch, before, after))
    val rebuilt = root.applyPatch(patch, before, after)

    assertEquals(PMarkerResolution.UNRESOLVED, moved.advanceResolution(root.resolutionCacheIdentity, root.resolve(entry.handle)))
    assertEquals(PMarkerResolution.UNRESOLVED, rebuilt.advanceResolution(root.resolutionCacheIdentity, root.resolve(entry.handle)))
  }

  @Test
  fun `concurrent first resolutions retain every cached resolution`() {
    val executor = Executors.newFixedThreadPool(8)
    try {
      repeat(20) {
        val entries = List(8) { entry(it * 2 + 1, it * 2 + 2) }
        val root = empty.insertMany(entries)
        assertNull(resolutionCache(root))
        val ready = CountDownLatch(entries.size)
        val start = CountDownLatch(1)
        val resolutions = entries.map { entry ->
          executor.submit<Long> {
            ready.countDown()
            check(start.await(10, TimeUnit.SECONDS))
            root.resolve(entry.handle)
          }
        }
        try {
          assertTrue(ready.await(10, TimeUnit.SECONDS))
        }
        finally {
          start.countDown()
        }
        for ((entry, resolution) in entries.zip(resolutions)) {
          assertEquals(TextRangeScalarUtil.toScalarRange(entry.nodeStart, entry.nodeEnd), resolution.get(10, TimeUnit.SECONDS))
        }
        val cache = checkNotNull(resolutionCache(root))
        for ((handle, nodeStart, nodeEnd) in entries) {
          assertEquals(TextRangeScalarUtil.toScalarRange(nodeStart, nodeEnd), cache[handle.id])
        }
      }
    }
    finally {
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
    }
  }

  @Test
  fun `metadata changes share cached resolutions without changing old snapshots`() {
    val entries = List(24) { index -> entry(index * 2 + 1, index * 2 + if (index % 3 == 0) 1 else 2) }
    val root = insertText(empty.insertMany(entries), "xx")
    root.resolve(entries[12].handle)
    val resolutions = checkNotNull(resolutionCache(root))
    val additions = listOf(entry(2, 4), entry(6, 8), entry(10, 10))
    val spec = root.updateSpec(entries[12].handle, MarkerSpec.create(isGreedyToLeft = true, isGreedyToRight = false))
    val flavor = root.updateFlavor(entries[12].handle, 2)
    val measure = root.updateMeasure(entries[0].handle, 99)
    val roots = listOf(
      root,
      root.insert(additions[0].handle, 2, 4, additions[0].spec, additions[0].flavorFlags, 0, DefaultMarkerPolicy),
      root.insertMany(additions.drop(1)),
      spec,
      flavor,
      measure,
      root.remove(entries[12].handle),
      root.remove(entries[12].handle).remove(entries[12].handle),
      root.purgeMany(listOf(entries[12].handle, entries[12].handle)),
      root.mergeValidMarkersFrom(root.insertMany(additions)),
    )

    for (updated in listOf(spec, flavor, measure)) {
      assertSame(root.resolutionCacheIdentity, updated.resolutionCacheIdentity)
      assertSame(resolutions, resolutionCache(updated))
    }
    for (updated in listOf(roots[1], roots[2], roots[6], roots[7], roots[8], roots[9])) {
      assertNull(resolutionCache(updated))
    }
    assertNotSame(root.resolutionCacheIdentity, roots[1].resolutionCacheIdentity)
    assertNotSame(root.resolutionCacheIdentity, roots[6].resolutionCacheIdentity)

    repeat(2) { pass ->
      for (current in roots.shuffled(Random(pass))) {
        val expected = resolvedEntries(current).associateBy { it.handle }
        for (entry in (entries + additions).shuffled(Random(pass))) {
          val actual = expected[entry.handle]
          assertEquals(actual != null, current.isValid(entry.handle))
          assertEquals(actual?.let { TextRangeScalarUtil.toScalarRange(it.nodeStart, it.nodeEnd) } ?: PMarkerResolution.ABSENT,
                       current.resolve(entry.handle))
        }
      }
    }
  }

  @Test
  fun `restored offsets and text changes use separate caches`() {
    val first = entry(2, 4)
    val second = entry(8, 8)
    val root = empty.insertMany(listOf(first, second))
    root.resolve(first.handle)
    root.resolve(second.handle)
    val cache = checkNotNull(resolutionCache(root))

    val restored = root.restoreEntries(listOf(first.copy(nodeStart = 11, nodeEnd = 13)))
    assertNull(resolutionCache(restored))
    assertNotSame(root.resolutionCacheIdentity, restored.resolutionCacheIdentity)
    assertEquals(TextRangeScalarUtil.toScalarRange(11, 13), restored.resolve(first.handle))
    assertNotSame(cache, resolutionCache(restored))
    assertEquals(TextRangeScalarUtil.toScalarRange(8, 8), restored.resolve(second.handle))
    assertEquals(TextRangeScalarUtil.toScalarRange(2, 4), root.resolve(first.handle))
    assertSame(root, root.restoreEntries(emptyList()))

    val edited = insertText(root, "x")
    assertNull(resolutionCache(edited))
    assertEquals(TextRangeScalarUtil.toScalarRange(3, 5), edited.resolve(first.handle))
    assertNotSame(cache, resolutionCache(edited))
    assertEquals(TextRangeScalarUtil.toScalarRange(2, 4), root.resolve(first.handle))
  }

  @Test
  fun `purged markers stay absent after collection of weak history values`() {
    val entries = List(8) { entry(it * 2 + 1, it * 2 + 2) }
    val root = empty.insertMany(entries)
    entries.forEach { root.resolve(it.handle) }
    val purged = root.purgeMany(listOf(entries[3].handle))
    val descendant = purged.updateFlavor(entries[6].handle, 2)

    GCWatcher.tracking(Any()).ensureCollected()

    for (current in listOf(purged, descendant)) {
      assertNotSame(resolutionCache(root), resolutionCache(current))
      assertFalse(current.isValid(entries[3].handle))
      assertSame(current, current.purgeMany(listOf(entries[3].handle)))
      assertEquals(PMarkerResolution.ABSENT, current.resolve(entries[3].handle))
      assertEquals(TextRangeScalarUtil.toScalarRange(entries[6].nodeStart, entries[6].nodeEnd), current.resolve(entries[6].handle))
    }
    assertEquals(TextRangeScalarUtil.toScalarRange(entries[3].nodeStart, entries[3].nodeEnd), root.resolve(entries[3].handle))
  }

  @Test
  fun `branches cache distinct offsets for an inserted handle`() {
    val first = entry(1, 2)
    val root = empty.insertMany(listOf(first))
    root.resolve(first.handle)
    val addition = entry(3, 4)
    val inserted = root.insertMany(listOf(addition))
    val branch = root.insertMany(listOf(addition.copy(nodeStart = 5, nodeEnd = 6)))

    repeat(2) {
      assertEquals(TextRangeScalarUtil.toScalarRange(3, 4), inserted.resolve(addition.handle))
      assertEquals(TextRangeScalarUtil.toScalarRange(5, 6), branch.resolve(addition.handle))
      assertEquals(PMarkerResolution.ABSENT, root.resolve(addition.handle))
      assertFalse(root.isValid(addition.handle))
    }
    assertNotSame(resolutionCache(inserted), resolutionCache(branch))
  }

  @Test
  fun `independent roots keep separate marker histories`() {
    val firstEntry = entry(2, 4)
    val secondEntry = entry(8, 10)
    val first = empty.insertMany(listOf(firstEntry))
    val second = PMarkerDirectRoot.empty().insertMany(listOf(secondEntry))
    val firstEdited = insertText(first, "xx")
    val secondEdited = insertText(second, "x")
    val firstBranch = insertText(first, "xxx")

    assertEquals(TextRangeScalarUtil.toScalarRange(2, 4), first.resolve(firstEntry.handle))
    assertEquals(TextRangeScalarUtil.toScalarRange(4, 6), firstEdited.resolve(firstEntry.handle))
    assertEquals(TextRangeScalarUtil.toScalarRange(5, 7), firstBranch.resolve(firstEntry.handle))
    assertEquals(TextRangeScalarUtil.toScalarRange(8, 10), second.resolve(secondEntry.handle))
    assertEquals(TextRangeScalarUtil.toScalarRange(9, 11), secondEdited.resolve(secondEntry.handle))
    for (root in listOf(first, firstEdited, firstBranch)) {
      assertEquals(PMarkerResolution.ABSENT, root.resolve(secondEntry.handle))
    }
    for (root in listOf(second, secondEdited)) {
      assertEquals(PMarkerResolution.ABSENT, root.resolve(firstEntry.handle))
    }
    val reset = PMarkerDirectRoot.empty()
    assertTrue(reset.isEmpty())
    assertEquals(PMarkerResolution.ABSENT, reset.resolve(firstEntry.handle))
  }

  private fun entry(start: Int, end: Int): MarkerEntry = MarkerEntry(
    SnapshotMarkerReference.create(markerForHandles, false),
    start,
    end,
    MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false),
    flavorFlags = 1,
  )

  private fun insertText(root: PMarkerDirectRoot, text: String): PMarkerDirectRoot {
    val patch = DocumentPatch.simple(0, 0, text, newModStamp = 1, clearLineFlags = false)
    return applyPatch(root, patch)
  }

  private fun applyPatch(
    root: PMarkerDirectRoot,
    patch: DocumentPatch,
    text: String = "x".repeat(100),
  ): PMarkerDirectRoot {
    val before = DocumentImpl(text, true).core.snapshot().text()
    return root.applyPatch(patch, before, before.replace(patch.startOffset(), patch.endOffset(), patch.newFragment()))
  }

  private fun resolutionCache(root: PMarkerDirectRoot): ConcurrentLongLongMap? = PMarkerDirectRoot::class.java
    .getDeclaredField("cachedResolutions").apply { isAccessible = true }.get(root) as ConcurrentLongLongMap?

  private fun resolvedEntries(root: PMarkerRoot): List<MarkerEntry> {
    val entries = ArrayList<MarkerEntry>()
    root.processAllRangeMarkers { entry, delta ->
      entries.add(entry.copy(nodeStart = entry.nodeStart + delta, nodeEnd = entry.nodeEnd + delta))
      true
    }
    return entries
  }
}
