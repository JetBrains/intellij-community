// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.DocumentCore
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.util.ObjectUtilsRt
import com.intellij.util.Processor
import com.intellij.util.ref.GCUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.concurrent.thread
import kotlin.random.Random

@Suppress("SameParameterValue")
@UsePMarkerImplementation
class SnapshotMarkerEngineImplTest {
  private val markerForHandles: SnapshotRangeMarkerImpl by lazy {
    SnapshotMarkerEngineImpl.createRangeMarker(DocumentImpl("", true), 0, 0, 0, false, DefaultMarkerPolicy) as SnapshotRangeMarkerImpl
  }
  private val handles: ArrayList<SnapshotMarkerReference<*>> = ArrayList()
  private var handleRoot: PMarkerRoot = PMarkerRootImpl.empty()

  private fun handle(label: Long): SnapshotMarkerReference<*> {
    while (handles.size <= label) handles.add(handleRoot.createHandle(markerForHandles, false))
    return handles[label.toInt()]
  }

  private fun label(handle: SnapshotMarkerReference<*>): Long = handles.indexOf(handle).toLong()

  @Test
  fun `persistent marker uses snapshot engine`() {
    val document = DocumentImpl("text")

    val marker = document.createRangeMarker(1, 3, true)

    assertTrue(marker is SnapshotMarker)
  }

  @Test
  fun `cached root reference observes marker changes in the same snapshot`() {
    val fixture = Fixture("abcdef")
    val snapshot = fixture.initialSnapshot
    val reference = fixture.rootStore.rootReference(snapshot)
    val marker = fixture.document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
    assertEquals(TextRange(2, 4), marker.textRange)
    assertSame(reference.get(), fixture.rootStore.root(snapshot))

    fixture.rootStore.updateRoot(snapshot) {
      applyPatch(it, "abcdef", textPatch(0, 0, "x"))
    }

    assertSame(snapshot, fixture.document.core.snapshot())
    assertSame(reference, marker.currentRootReference())
    assertSame(reference.get(), fixture.rootStore.root(snapshot))
    assertEquals(TextRange(3, 5), marker.textRange)
  }

  @Test
  fun `cached root reference follows document snapshot changes`() {
    val fixture = Fixture("abcdef")
    val marker = fixture.document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
    val reference = marker.currentRootReference()
    val originalRoot = reference.get()
    assertEquals(TextRange(2, 4), marker.textRange)

    fixture.document.insertString(0, "XX")

    assertNotSame(reference, marker.currentRootReference())
    assertSame(marker.currentRootReference().get(), fixture.rootStore.root(fixture.document.core.snapshot()))
    assertSame(originalRoot, fixture.rootStore.root(fixture.initialSnapshot))
    assertEquals(TextRange(4, 6), marker.textRange)
    assertSame(reference, fixture.rootStore.rootReference(fixture.initialSnapshot))
    assertEquals(TextRange(4, 6), marker.textRange)
  }

  @Test
  fun `root lookup does not create a root for an unknown snapshot`() {
    val fixture = Fixture("abcdef")
    fixture.rootStore.rootReference(fixture.initialSnapshot)
    val snapshot = fixture.initialSnapshot.newInstance()

    assertNull(fixture.rootStore.root(snapshot))
    assertFalse(fixture.rootStore.containsSnapshot(snapshot))
  }

  @Test
  fun `root lookup returns no root after store disposal`() {
    val fixture = Fixture("abcdef")
    val snapshot = fixture.initialSnapshot
    val reference = fixture.rootStore.rootReference(snapshot)
    assertSame(reference.get(), fixture.rootStore.root(snapshot))

    fixture.rootStore.dispose(fixture.document.snapshotMarkerStores)

    assertNull(fixture.rootStore.root(snapshot))
  }

  @Test
  fun `cached offsets advance across edits outside the marker`() {
    val document = DocumentImpl("x".repeat(20), true)
    val before = document.createRangeMarker(0, 1)
    val containing = document.createRangeMarker(1, 20)
    val after = document.createRangeMarker(12, 14)
    val beforeRange = before.textRange
    containing.textRange
    after.textRange

    document.insertString(10, "x")

    assertEquals(beforeRange, before.textRange)
    assertEquals(TextRange(1, 21), containing.textRange)
    assertEquals(TextRange(13, 15), after.textRange)
    val afterRange = after.textRange

    document.replaceString(10, 11, "y")

    assertEquals(beforeRange, before.textRange)
    assertEquals(afterRange, after.textRange)

    document.deleteString(10, 11)
    before.isGreedyToRight = true

    assertEquals(beforeRange, before.textRange)
    assertEquals(TextRange(12, 14), after.textRange)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `cached offsets resolve edits at marker boundaries`(greedy: Boolean) {
    val document = DocumentImpl("abcdefgh", true)
    val ending = document.createRangeMarker(0, 3)
    val starting = document.createRangeMarker(3, 6)
    val point = document.createRangeMarker(3, 3)
    val markers = listOf(ending, starting, point)
    for (marker in markers) {
      marker.isGreedyToLeft = greedy
      marker.isGreedyToRight = greedy
      marker.textRange
    }

    document.insertString(3, "XX")

    assertEquals(TextRange(0, if (greedy) 5 else 3), ending.textRange)
    assertEquals(TextRange(if (greedy) 3 else 5, 8), starting.textRange)
    assertEquals(TextRange(3, if (greedy) 5 else 3), point.textRange)
    markers.forEach { assertCurrentResolution(it as SnapshotRangeMarkerImpl) }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `cached offsets observe policy changes before the edit`(invalidate: Boolean) {
    val document = DocumentImpl("x".repeat(20), true)
    val before = document.createRangeMarker(0, 1)
    var transformed = false
    val policy = MarkerPolicy { entry, _, _, _ ->
      transformed = true
      if (invalidate) MarkerTransformResult(MarkerTransformError.INVALIDATED_BY_EDIT)
      else MarkerTransformResult(entry.nodeStart, 5)
    }
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document, 2, 4, nonGreedySpec(), policy = policy,
    ) as SnapshotRangeMarkerImpl
    val containing = document.createRangeMarker(6, 20)
    val beforeRange = before.textRange
    assertEquals(TextRange(2, 4), marker.textRange)

    document.insertString(10, "x")

    assertTrue(transformed)
    assertEquals(beforeRange, before.textRange)
    assertEquals(!invalidate, marker.isValid)
    assertEquals(TextRange(2, if (invalidate) 4 else 5), marker.textRange)
    assertCurrentResolution(marker)
    assertEquals(TextRange(6, 21), containing.textRange)
  }

  @Test
  fun `cached offsets fall back after skipped edits and branch switches`() {
    val document = DocumentImpl("abcdefgh", true)
    val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
    val reference = marker.currentRootReference()
    val original = reference.get()
    marker.textRange
    val first = applyPatch(original, "abcdefgh", textPatch(0, 0, "X"))
    reference.set(applyPatch(first, "Xabcdefgh", textPatch(0, 0, "YY")))

    assertEquals(TextRange(5, 7), marker.textRange)

    reference.set(applyPatch(original, "abcdefgh", textPatch(0, 0, "ZZ")))

    assertEquals(TextRange(4, 6), marker.textRange)

    reference.set(original)

    assertEquals(TextRange(2, 4), marker.textRange)
  }

  @Test
  fun `advancing cached offsets does not retain the previous root`() {
    val document = DocumentImpl("abcdefgh", true)
    val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
    fun replaceRoot(): WeakReference<PMarkerRoot> {
      val reference = marker.currentRootReference()
      val root = reference.get()
      marker.textRange
      reference.set(applyPatch(root, "abcdefgh", textPatch(6, 6, "X")))
      assertEquals(TextRange(2, 4), marker.textRange)
      return WeakReference(root)
    }

    val previousRoot = replaceRoot()

    GCUtil.tryGcSoftlyReachableObjects { previousRoot.get() == null }

    assertNull(previousRoot.get())
    Reference.reachabilityFence(marker)
  }

  @Test
  fun `cached offsets match root resolution after random changes`() {
    val seed = 0xCA43
    val random = Random(seed)
    val document = DocumentImpl("abc\n".repeat(32), true)
    val markers = ArrayList<SnapshotRangeMarkerImpl>()
    val disposedRanges = HashMap<SnapshotRangeMarkerImpl, TextRange>()
    fun addMarker() {
      val start = random.nextInt(document.textLength + 1)
      val end = random.nextInt(start, document.textLength + 1)
      val marker = document.createRangeMarker(start, end, random.nextBoolean()) as SnapshotRangeMarkerImpl
      marker.isGreedyToLeft = random.nextBoolean()
      marker.isGreedyToRight = random.nextBoolean()
      marker.setStickingToRight(random.nextBoolean())
      markers.add(marker)
    }
    repeat(50) { addMarker() }
    markers.forEach { assertCurrentResolution(it) }

    repeat(300) { iteration ->
      val start = random.nextInt(document.textLength + 1)
      val end = minOf(document.textLength, start + random.nextInt(9))
      when (random.nextInt(4)) {
        0 -> document.insertString(start, "x\n".repeat(random.nextInt(1, 4)))
        1 -> document.deleteString(start, end)
        2 -> document.replaceString(start, end, "y\n".repeat(random.nextInt(4)))
        else -> {
          val target = random.nextInt(document.textLength + 1)
          if (target !in start..end) document.moveText(start, end, target)
        }
      }
      if (iteration % 5 == 0) addMarker()
      if (iteration % 17 == 0) {
        val marker = markers.random(random)
        marker.isGreedyToRight = !marker.isGreedyToRight
      }
      if (iteration % 31 == 0) document.replaceString(0, document.textLength, "abc\n".repeat(32))
      if (iteration % 37 == 0) {
        val marker = markers.random(random)
        disposedRanges.putIfAbsent(marker, marker.textRange)
        marker.dispose()
      }
      for (marker in markers) {
        if (iteration % 13 == 0 || random.nextBoolean()) {
          assertCurrentResolution(marker, "Seed $seed, iteration $iteration, marker ${marker.id}", disposedRanges[marker])
        }
      }
    }
    markers.forEach { assertCurrentResolution(it, disposedRange = disposedRanges[it]) }
  }

  @Test
  fun `root reference cache releases unused snapshots and roots`() {
    val fixture = Fixture("abcdef")
    fun cacheDetachedRoot(): Pair<WeakReference<DocumentSnapshot>, WeakReference<AtomicReference<PMarkerRoot>>> {
      val snapshot = fixture.initialSnapshot.newInstance()
      val reference = fixture.rootStore.rootReference(snapshot)
      assertSame(reference, fixture.rootStore.rootReference(snapshot))
      assertSame(reference.get(), fixture.rootStore.root(snapshot))
      return WeakReference(snapshot) to WeakReference(reference)
    }
    val [snapshot, reference] = cacheDetachedRoot()

    GCUtil.tryGcSoftlyReachableObjects {
      fixture.rootStore.processQueue()
      snapshot.get() == null && reference.get() == null
    }

    assertNull(snapshot.get())
    assertNull(reference.get())
  }

  @Test
  fun `clean snapshot merge keeps markers created in both branches`() {
    val core = DocumentImpl("abcdef", true).core
    val initialSnapshot = core.snapshot()
    var current = initialSnapshot
    val document = DocumentImpl(object : DocumentCore by core {
      override fun snapshot(): DocumentBranch = current
    }, null)
    val markerStores = document.snapshotMarkerStores
    val primary = initialSnapshot.applyOp(DocumentOp.modStampOp(1, true))
    val metadata = initialSnapshot.applyOp(DocumentOp.modStampOp(2, true))
    markerStores.applyPatch(initialSnapshot, primary, null)
    markerStores.applyPatch(initialSnapshot, metadata, null)

    current = primary
    val primaryMarker = document.createRangeMarker(1, 4) as SnapshotMarker
    current = metadata
    val metadataMarker = document.createRangeMarker(5, 5) as SnapshotMarker

    val merged = markerStores.mergeMarkerRoots(primary, metadata)

    assertSame(metadata.text(), merged.text())
    assertEquals(metadata.modState().stamp(), merged.modState().stamp())
    assertRange(primaryMarker, merged, startOffset = 1, endOffset = 4)
    assertRange(metadataMarker, merged, startOffset = 5, endOffset = 5)
  }

  @Test
  fun `marker has different offsets in two child snapshots`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )

    val firstSnapshot = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "XX")
    val secondSnapshot = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "YYYY")

    assertRange(marker, initialSnapshot, startOffset = 2, endOffset = 4)
    assertRange(marker, firstSnapshot, startOffset = 4, endOffset = 6)
    assertRange(marker, secondSnapshot, startOffset = 6, endOffset = 8)
  }

  @Test
  fun `sibling snapshots with the same modification sequence have independent roots`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )

    val firstSnapshot = fixture.editWithNaturalModSequence(
      initialSnapshot,
      startOffset = 0,
      endOffset = 0,
      newFragment = "X"
    )
    val secondSnapshot = fixture.editWithNaturalModSequence(
      initialSnapshot,
      startOffset = 0,
      endOffset = 0,
      newFragment = "YYYY"
    )

    assertEquals(firstSnapshot.modState().sequence(), secondSnapshot.modState().sequence())
    assertRange(marker, firstSnapshot, startOffset = 3, endOffset = 5)
    assertRange(marker, secondSnapshot, startOffset = 6, endOffset = 8)
  }

  @Test
  fun `child marker root is published only once`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )
    val child = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "X")
    assertRange(marker, child, startOffset = 3, endOffset = 5)
  }

  @Test
  fun `marker created after child snapshot is propagated only to future children`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val existingChild = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "X")

    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )

    val futureChild = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "YY")

    assertRange(marker, initialSnapshot, startOffset = 2, endOffset = 4)
    assertAbsent(marker, existingChild)
    assertRange(marker, futureChild, startOffset = 4, endOffset = 6)
  }

  @Test
  fun `engine removal disposes marker across snapshots`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )
    val existingChild = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "X")

    assertTrue(SnapshotMarkerEngineImpl.removeRangeMarker(marker))
    assertFalse(SnapshotMarkerEngineImpl.removeRangeMarker(marker))

    val futureChild = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "YY")

    assertAbsent(marker, initialSnapshot)
    assertDisposed(marker, existingChild, startOffset = 3, endOffset = 5)
    assertAbsent(marker, futureChild)
    assertFalse(marker.isValid)
    assertEquals(TextRange(2, 4), marker.textRange)
  }

  @Test
  fun `left greediness controls insertion at marker start`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val greedy = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = MarkerSpec.create(isGreedyToLeft = true, isGreedyToRight = false)
    )
    val nonGreedy = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    )

    val child = fixture.edit(initialSnapshot, startOffset = 2, endOffset = 2, newFragment = "XX")

    assertRange(greedy, initialSnapshot, startOffset = 2, endOffset = 4)
    assertRange(nonGreedy, initialSnapshot, startOffset = 2, endOffset = 4)
    assertRange(greedy, child, startOffset = 2, endOffset = 6)
    assertRange(nonGreedy, child, startOffset = 4, endOffset = 6)
  }

  @Test
  fun `right greediness controls insertion at marker end`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val greedy = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = true)
    )
    val nonGreedy = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    )

    val child = fixture.edit(initialSnapshot, startOffset = 4, endOffset = 4, newFragment = "XX")

    assertRange(greedy, child, startOffset = 2, endOffset = 6)
    assertRange(nonGreedy, child, startOffset = 2, endOffset = 4)
  }

  @Test
  fun `sticking to right controls insertion at point marker`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val nonSticking = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 2,
      spec = nonGreedySpec()
    )
    val sticking = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 2,
      spec = nonGreedySpec()
    ) as SnapshotRangeMarkerImpl
    val greedy = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 2,
      spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = true)
    ) as SnapshotRangeMarkerImpl
    sticking.setStickingToRight(true)
    greedy.setStickingToRight(true)

    val child = fixture.edit(initialSnapshot, startOffset = 2, endOffset = 2, newFragment = "XX")

    assertRange(nonSticking, initialSnapshot, startOffset = 2, endOffset = 2)
    assertRange(sticking, initialSnapshot, startOffset = 2, endOffset = 2)
    assertRange(greedy, initialSnapshot, startOffset = 2, endOffset = 2)
    assertRange(nonSticking, child, startOffset = 2, endOffset = 2)
    assertRange(sticking, child, startOffset = 4, endOffset = 4)
    assertRange(greedy, child, startOffset = 2, endOffset = 4)
  }

  @Test
  fun `replacement inside marker changes only child range`() {
    val fixture = Fixture("abcdefgh")
    val initialSnapshot = fixture.initialSnapshot
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 6,
      spec = nonGreedySpec()
    )

    val child = fixture.edit(initialSnapshot, startOffset = 3, endOffset = 5, newFragment = "XYZ")

    assertRange(marker, initialSnapshot, startOffset = 2, endOffset = 6)
    assertRange(marker, child, startOffset = 2, endOffset = 7)
  }

  @Test
  fun `whole replacement translates persistent marker through line diff`() {
    val oldText = "alpha\ntarget\nomega"
    val newText = "prefix\nalpha\ntarget\nomega\nsuffix"
    val document = DocumentImpl(oldText, true)
    val markerStart = oldText.indexOf("target") + 1
    val persistentMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      markerStart,
      markerStart + 3,
      nonGreedySpec(),
      policy = PersistentMarkerPolicy,
    )
    val ordinaryMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      markerStart,
      markerStart + 3,
      nonGreedySpec(),
    ) as SnapshotRangeMarkerImpl

    document.replaceString(0, oldText.length, newText)
    val replacedSnapshot = document.core.snapshot()
    val expectedStart = newText.indexOf("target") + 1

    assertRange(persistentMarker, replacedSnapshot, expectedStart, expectedStart + 3)
    assertTrue(
      !PMarkerResolution.isValid(SnapshotMarkerEngineImpl.resolveRangeMarker(ordinaryMarker, rootFor(ordinaryMarker, replacedSnapshot)))
    )
  }

  @Test
  fun `large partial replacement translates persistent marker through line diff`() {
    val oldText = "x\nold-a\ntarget\nold-b\ny"
    val replacement = "new-a\nextra\npadding\ntarget\nnew-b\n"
    val document = DocumentImpl(oldText, true)
    val markerStart = oldText.indexOf("target") + 1
    val persistentMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      markerStart,
      markerStart + 3,
      nonGreedySpec(),
      policy = PersistentMarkerPolicy,
    )
    val ordinaryMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      markerStart,
      markerStart + 3,
      nonGreedySpec(),
    ) as SnapshotRangeMarkerImpl
    val replacementStart = oldText.indexOf("old-a")
    val replacementEnd = oldText.indexOf('y')

    document.replaceString(replacementStart, replacementEnd, replacement)
    val replacedSnapshot = document.core.snapshot()
    val expectedStart = document.text.indexOf("target") + 1

    assertRange(persistentMarker, replacedSnapshot, expectedStart, expectedStart + 3)
    assertTrue(
      !PMarkerResolution.isValid(SnapshotMarkerEngineImpl.resolveRangeMarker(ordinaryMarker, rootFor(ordinaryMarker, replacedSnapshot)))
    )
  }

  @Test
  fun `small partial replacement uses ordinary marker transformation`() {
    val oldText = "stable0\nstable1\nold-a\ntarget\nold-b\nstable2\nstable3\nstable4"
    val replacement = "new-a\ntarget\nnew-b\n"
    val document = DocumentImpl(oldText, true)
    val markerStart = oldText.indexOf("target") + 1
    val persistentMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      markerStart,
      markerStart + 3,
      nonGreedySpec(),
      policy = PersistentMarkerPolicy,
    ) as SnapshotRangeMarkerImpl
    val replacementStart = oldText.indexOf("old-a")
    val replacementEnd = oldText.indexOf("stable2")

    document.replaceString(replacementStart, replacementEnd, replacement)

    val snapshot = document.core.snapshot()
    assertTrue(
      !PMarkerResolution.isValid(SnapshotMarkerEngineImpl.resolveRangeMarker(persistentMarker, rootFor(persistentMarker, snapshot)))
    )
  }

  @Test
  fun `deletion before marker shifts marker left only in child`() {
    val fixture = Fixture("abcdefgh")
    val initialSnapshot = fixture.initialSnapshot
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 4,
      endOffset = 6,
      spec = nonGreedySpec()
    )

    val child = fixture.edit(initialSnapshot, startOffset = 1, endOffset = 3, newFragment = "")

    assertRange(marker, initialSnapshot, startOffset = 4, endOffset = 6)
    assertRange(marker, child, startOffset = 2, endOffset = 4)
  }

  @Test
  fun `moving text to the beginning retargets contained markers`() {
    val fixture = Fixture("01234567890")
    val pointMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 5,
      endOffset = 5,
      spec = nonGreedySpec()
    )
    val rangeMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 5,
      endOffset = 7,
      spec = nonGreedySpec()
    )

    fixture.document.moveText(4, 7, 1)
    val movedSnapshot = fixture.document.core.snapshot()

    assertEquals("04561237890", movedSnapshot.text().string())
    assertRange(pointMarker, movedSnapshot, startOffset = 2, endOffset = 2)
    assertRange(rangeMarker, movedSnapshot, startOffset = 2, endOffset = 4)
  }

  @Test
  fun `moving text to the end retargets contained markers`() {
    val fixture = Fixture("01234567890")
    val firstMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 1,
      endOffset = 3,
      spec = nonGreedySpec()
    )
    val secondMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )

    fixture.document.moveText(0, 5, 8)
    val movedSnapshot = fixture.document.core.snapshot()

    assertEquals("56701234890", movedSnapshot.text().string())
    assertRange(firstMarker, movedSnapshot, startOffset = 4, endOffset = 6)
    assertRange(secondMarker, movedSnapshot, startOffset = 5, endOffset = 7)
  }

  @Test
  fun `deleting whole marker invalidates child marker and keeps parent marker valid`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )

    val child = fixture.edit(initialSnapshot, startOffset = 1, endOffset = 5, newFragment = "")

    assertRange(marker, initialSnapshot, startOffset = 2, endOffset = 4)
    val storedMarker = marker as SnapshotRangeMarkerImpl
    val invalid = SnapshotMarkerEngineImpl.resolveRangeMarker(storedMarker, rootFor(storedMarker, child))
    assertEquals(PMarkerResolution.invalid(TextRangeScalarUtil.toScalarRange(2, 4)), invalid)
    assertTrue(SnapshotMarkerEngineImpl.removeRangeMarker(marker))
    assertDisposed(marker, child, startOffset = 2, endOffset = 4)
  }

  @Test
  fun `disposed marker retains its range from the current root`() {
    val fixture = Fixture("abcdef")
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    ) as SnapshotRangeMarkerImpl

    assertEquals(1, countOverlappingMarkers(fixture.rootStore, fixture.initialSnapshot, startOffset = 0, endOffset = 6))

    fixture.document.insertString(0, "XX")
    val currentSnapshot = fixture.document.core.snapshot()
    marker.dispose()

    val root = rootFor(marker, currentSnapshot)
    val resolution = SnapshotMarkerEngineImpl.resolveRangeMarker(marker, root)
    assertEquals("Marker is disposed", SnapshotMarkerEngineImpl.invalidationReason(marker, root))
    assertEquals(PMarkerResolution.ABSENT, resolution)
    assertFalse(marker.isValid)
    assertEquals(TextRange(4, 6), marker.textRange)
    assertEquals(0, countOverlappingMarkers(fixture.rootStore, currentSnapshot, startOffset = 0, endOffset = 8))
    assertDisposed(marker, fixture.initialSnapshot, startOffset = 2, endOffset = 4)

    fixture.document.insertString(0, "Y")
    marker.dispose()
    assertEquals(TextRange(4, 6), marker.textRange)
  }

  @Test
  fun `document removal disposes marker and removes it from the current root`() {
    val fixture = Fixture("abcdef")
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    ) as SnapshotRangeMarkerImpl
    fixture.document.insertString(0, "XX")
    assertTrue(fixture.document.removeRangeMarker(marker))

    val currentSnapshot = fixture.document.core.snapshot()
    val root = rootFor(marker, currentSnapshot)
    val resolution = SnapshotMarkerEngineImpl.resolveRangeMarker(marker, root)
    assertEquals(PMarkerResolution.ABSENT, resolution)
    assertEquals("Marker is disposed", SnapshotMarkerEngineImpl.invalidationReason(marker, root))
    assertFalse(marker.isValid)
    assertEquals(0, countOverlappingMarkers(fixture.rootStore, currentSnapshot, startOffset = 0, endOffset = 8))
    assertFalse(fixture.document.removeRangeMarker(marker))
    assertEquals(TextRange(4, 6), marker.textRange)
  }

  @Test
  @UsePMarkerImplementation(false)
  fun `legacy document full traversal preserves order and stops early`() {
    val document = DocumentImpl("abcdef", true)
    val last = document.createRangeMarker(5, 6)
    val persistent = document.createRangeMarker(2, 3, true)
    val first = document.createRangeMarker(0, 1)
    val visited = ArrayList<RangeMarker>()
    assertFalse(first is SnapshotMarker)

    assertTrue(document.processRangeMarkers {
      visited.add(it)
      true
    })
    assertEquals(listOf(first, persistent, last), visited)

    visited.clear()
    assertFalse(document.processRangeMarkers {
      visited.add(it)
      false
    })
    assertEquals(listOf(first), visited)
  }

  @Test
  fun `document full traversal preserves order after an edit and stops early`() {
    val document = DocumentImpl("abcdef", true)
    assertTrue(document.processRangeMarkers { error("An empty document root must not visit a marker") })
    val last = document.createRangeMarker(6, 6)
    val firstEqual = document.createRangeMarker(2, 4)
    val first = document.createRangeMarker(0, 0)
    val secondEqual = document.createRangeMarker(2, 3)
    document.insertString(0, "x")
    val visited = ArrayList<RangeMarker>()

    assertTrue(document.processRangeMarkers {
      visited.add(it)
      true
    })
    assertEquals(listOf(first, firstEqual, secondEqual, last), visited)

    visited.clear()
    assertFalse(document.processRangeMarkers {
      visited.add(it)
      false
    })
    assertEquals(listOf(first), visited)
  }

  @Test
  fun `document full traversal purges cleared references and skips disposed markers`() {
    val document = DocumentImpl("abcdef", true)
    val snapshot = document.core.snapshot()
    val first = document.createRangeMarker(0, 0)
    val disposed = document.createRangeMarker(2, 3)
    val cleared = document.createRangeMarker(4, 5) as SnapshotMarker
    val last = document.createRangeMarker(6, 6)
    val rootStore = document.rangeMarkers.rootStore()
    val reference = rootStore.root(snapshot)!!.overlappingIterator(4, 5, 0).next().handle as Reference<*>
    reference.clear()
    val visited = ArrayList<RangeMarker>()

    assertTrue(document.processRangeMarkers {
      visited.add(it)
      if (it === first) disposed.dispose()
      true
    })

    assertEquals(listOf(first, last), visited)
    assertFalse(rootStore.containsMarker(snapshot, (cleared as SnapshotRangeMarkerImpl).markerHandle))
    assertFalse(disposed.isValid)
  }

  @Test
  fun `enumeration returns the original marker with its user data`() {
    val fixture = Fixture("abcdef")
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec(),
    ) as SnapshotRangeMarkerImpl
    val key = Key.create<String>("snapshot.marker.test.data")
    marker.putUserData(key, "value")

    var processedMarker: RangeMarkerEx? = null
    assertTrue(
      SnapshotMarkerEngineImpl.processRangeMarkersOverlappingWith(
        fixture.rootStore,
        fixture.initialSnapshot,
        startOffset = 0,
        endOffset = 6,
        tastePreference = 0,
      ) {
        processedMarker = it
        true
      }
    )

    assertSame(marker, processedMarker)
    assertEquals("value", processedMarker!!.getUserData(key))
  }

  @Test
  fun `taste preference excludes unflavored snapshot markers`() {
    val fixture = Fixture("abcdef")
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec(),
    )

    var processedMarker: RangeMarkerEx? = null
    assertTrue(
      SnapshotMarkerEngineImpl.processRangeMarkersOverlappingWith(
        fixture.rootStore,
        fixture.initialSnapshot,
        startOffset = 0,
        endOffset = 6,
        tastePreference = 1,
      ) {
        processedMarker = it
        true
      }
    )
    assertNull(processedMarker)

    assertTrue(
      SnapshotMarkerEngineImpl.processRangeMarkersOverlappingWith(
        fixture.rootStore,
        fixture.initialSnapshot,
        startOffset = 0,
        endOffset = 6,
        tastePreference = 0,
      ) {
        processedMarker = it
        true
      }
    )
    assertSame(marker, processedMarker)
  }

  @Test
  fun `root emptiness ignores removed markers`() {
    val empty = PMarkerRootImpl.empty()
    assertTrue(empty.isEmpty())

    val populated = empty
      .insert(handle(1), 2, 4, nonGreedySpec(), 0)
      .insert(handle(2), 5, 6, nonGreedySpec(), 0)
    assertFalse(populated.isEmpty())

    val oneRemoved = populated.remove(handle(1))
    assertFalse(oneRemoved.isEmpty())

    val allRemoved = oneRemoved.remove(handle(2))
    assertTrue(allRemoved.isEmpty())
    assertTrue(PMarkerResolution.isAbsent(allRemoved.resolve(handle(1))))
    assertTrue(allRemoved.purgeMany(listOf(handle(1), handle(2))).isEmpty())
    assertFalse(populated.isEmpty())
  }

  @Test
  fun `root emptiness ignores invalid markers`() {
    val populated = PMarkerRootImpl.empty()
      .insert(handle(1), 2, 4, nonGreedySpec(), 0)
      .insert(handle(2), 5, 6, nonGreedySpec(), 0)
    val invalidated = applyPatch(populated, "abcdef", textPatch(1, 5, ""))
    assertFalse(PMarkerResolution.isValid(invalidated.resolve(handle(1))))
    assertFalse(invalidated.isEmpty())

    val noValidMarkers = invalidated.remove(handle(2))
    assertTrue(noValidMarkers.isEmpty())
    assertTrue(noValidMarkers.purgeMany(listOf(handle(1), handle(2))).isEmpty())
    assertFalse(populated.isEmpty())
  }

  @Test
  fun `full traversal preserves marker order and lazy shifts`() {
    var root: PMarkerRoot = PMarkerRootImpl.empty()
      .insert(handle(30), 8, 10, nonGreedySpec(), 0)
      .insert(handle(20), 2, 7, nonGreedySpec(), 0)
      .insert(handle(10), 2, 4, nonGreedySpec(), 0)
      .insert(handle(40), 14, 16, nonGreedySpec(), 0)
      .insert(handle(50), 20, 22, nonGreedySpec(), 0)
      .insert(handle(60), 25, 25, nonGreedySpec(), 0)
      .remove(handle(40))
    root = applyPatch(root, "012345678901234567890123456789", textPatch(0, 0, "ab"))
    val entries = ArrayList<Pair<Long, TextRange>>()
    var hasLazyShift = false

    assertTrue(root.processAllRangeMarkers { (val handle, val nodeStart, val nodeEnd), ancestorDelta ->
      entries.add(label(handle) to TextRange(nodeStart + ancestorDelta, nodeEnd + ancestorDelta))
      if (ancestorDelta != 0) hasLazyShift = true
      true
    })

    assertEquals(
      listOf(10L to TextRange(4, 6), 20L to TextRange(4, 9), 30L to TextRange(10, 12),
             50L to TextRange(22, 24), 60L to TextRange(27, 27)),
      entries,
    )
    assertTrue(hasLazyShift)
  }

  @Test
  fun `full traversal stops when the processor returns false`() {
    val root = PMarkerRootImpl.empty()
      .insert(handle(1), 2, 4, nonGreedySpec(), 0)
      .insert(handle(2), 6, 8, nonGreedySpec(), 0)
      .insert(handle(3), 10, 12, nonGreedySpec(), 0)
    val visited = ArrayList<Long>()

    assertFalse(root.processAllRangeMarkers { entry, _ ->
      visited.add(label(entry.handle))
      false
    })

    assertEquals(listOf(1L), visited)
    assertTrue(PMarkerRootImpl.empty().processAllRangeMarkers { _, _ -> error("An empty root must not visit a marker") })
  }

  @Test
  fun `garbage collected marker is purged from the current root`() {
    val fixture = Fixture("abcdef")
    val weakMarker = createWeakMarker(fixture, startOffset = 2, endOffset = 4)
    assertTrue(currentRootContains(fixture, weakMarker.handle))

    gcMarkerAndWaitForProcessQueues(weakMarker)

    assertEquals(0, countOverlappingMarkers(fixture.rootStore, fixture.initialSnapshot, startOffset = 0, endOffset = 6))
    assertFalse(currentRootContains(fixture, weakMarker.handle))
  }

  @Test
  fun `queue processing reports an earlier successful purge`() {
    val document = DocumentImpl("abcdef", true)
    val snapshot = document.core.snapshot()
    val firstMarker = document.createRangeMarker(1, 2) as SnapshotRangeMarkerImpl
    val secondMarker = document.createRangeMarker(3, 4) as SnapshotRangeMarkerImpl
    val rootStore = document.rangeMarkers.rootStore()
    val markerReferences = rootStore.root(snapshot)!!
      .overlappingIterator(0, document.textLength, 0)
      .asSequence()
      .associate { it.handle to (it.handle as Reference<*>) }
    assertTrue(rootStore.purgeMany(snapshot, listOf(secondMarker.markerHandle)))

    // Manual enqueue uses reverse order. The successful purge runs first.
    assertTrue(markerReferences.getValue(secondMarker.markerHandle).enqueue())
    assertTrue(markerReferences.getValue(firstMarker.markerHandle).enqueue())

    assertTrue(SnapshotMarkerEngineImpl.processQueue())
    assertFalse(rootStore.containsMarker(snapshot, firstMarker.markerHandle))
  }

  @ParameterizedTest
  @ValueSource(ints = [1, 2, 3])
  fun `queue processing batches references from one storage and purges interleaved storages`(documentCount: Int) {
    val documents = List(documentCount) { DocumentImpl("abcdef", true) }
    val markers = documents.map { document -> List(3) { document.createRangeMarker(it, it + 1) as SnapshotRangeMarkerImpl } }
    val batches = ArrayList<List<SnapshotMarkerReference<*>>>()
    val originalRoots = documents.map { document ->
      val reference = document.rangeMarkers.rootStore().rootReference(document.core.snapshot())
      val root = reference.get()
      reference.set(object : PMarkerRoot by root {
        override fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot {
          batches.add(handles.toList())
          return root.purgeMany(handles)
        }
      })
      root
    }
    val entriesByRoot = originalRoots.map { it.overlappingIterator(0, 6, 0).asSequence().toList() }
    for (index in markers.first().indices) {
      for (entries in entriesByRoot) {
        assertTrue((entries[index].handle as Reference<*>).enqueue())
      }
    }

    assertTrue(SnapshotMarkerEngineImpl.processQueue())

    if (documentCount == 1) {
      assertEquals(1, batches.size)
      assertEquals(markers.single().map { it.markerHandle }.toSet(), batches.single().toSet())
    }
    for ([index, document] in documents.withIndex()) {
      val root = document.rangeMarkers.rootStore().rootReference(document.core.snapshot()).get()
      for (marker in markers[index]) {
        assertFalse(root.containsMarker(marker.markerHandle))
        assertTrue(originalRoots[index].containsMarker(marker.markerHandle))
      }
    }
    assertFalse(SnapshotMarkerEngineImpl.processQueue())
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `traversal batches collected markers when the processor stops or fails`(overlapping: Boolean) {
    for (fail in listOf(false, true)) {
      val document = DocumentImpl("abcdefgh", true)
      val markers = List(4) { document.createRangeMarker(it * 2, it * 2 + 1) as SnapshotRangeMarkerImpl }
      val snapshot = document.core.snapshot()
      val rootStore = document.rangeMarkers.rootStore()
      val reference = rootStore.rootReference(snapshot)
      val root = reference.get()
      val entries = root.overlappingIterator(0, document.textLength, 0).asSequence().toList()
      for (index in listOf(0, 1, 3)) {
        (entries[index].handle as Reference<*>).clear()
      }
      val batches = ArrayList<List<SnapshotMarkerReference<*>>>()
      reference.set(object : PMarkerRoot by root {
        override fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot {
          batches.add(handles.toList())
          return root.purgeMany(handles)
        }
      })
      val failure = IllegalStateException("The processor failed")
      val visited = ArrayList<Long>()
      val processor = Processor<RangeMarkerEx> {
        visited.add(it.id)
        if (fail) throw failure
        false
      }
      val traverse = {
        if (overlapping) {
          SnapshotMarkerEngineImpl.processRangeMarkersOverlappingWith(rootStore, snapshot, 0, document.textLength, 0, processor)
        }
        else {
          SnapshotMarkerEngineImpl.processAllRangeMarkers(rootStore, snapshot, processor)
        }
      }

      if (fail) assertSame(failure, assertThrows(IllegalStateException::class.java) { traverse() })
      else assertFalse(traverse())

      assertEquals(listOf(markers[2].id), visited)
      assertEquals(listOf(listOf(markers[0].markerHandle, markers[1].markerHandle)), batches)
      assertFalse(rootStore.containsMarker(snapshot, markers[0].markerHandle))
      assertFalse(rootStore.containsMarker(snapshot, markers[1].markerHandle))
      assertTrue(rootStore.containsMarker(snapshot, markers[2].markerHandle))
      assertTrue(rootStore.containsMarker(snapshot, markers[3].markerHandle))
    }
  }

  @Test
  fun `strong marker is retained by a valid root`() {
    val fixture = Fixture("abcdef")
    val strongMarker = createStrongMarker(fixture.document, startOffset = 2, endOffset = 4)

    GCUtil.tryGcSoftlyReachableObjects()

    assertNotNull(strongMarker.get())
    ObjectUtilsRt.reachabilityFence(fixture)
  }

  @Test
  fun `invalid root releases a strong marker`() {
    val document = DocumentImpl("abcdef", true)
    val strongMarker = createInvalidStrongMarker(document, startOffset = 2, endOffset = 4)

    GCUtil.tryGcSoftlyReachableObjects {
      document.rangeMarkers.rootStore().processQueue()
      strongMarker.get() == null
    }

    assertNull(strongMarker.get())
    ObjectUtilsRt.reachabilityFence(document)
  }

  @Test
  fun `invalid child keeps a strong marker retained by its valid parent`() {
    val document = DocumentImpl("abcdef", true)
    val parent = document.core.snapshot()
    val strongMarker = createStrongMarker(document, startOffset = 2, endOffset = 4)

    document.snapshotMarkerStores.applyPatch(parent, textPatch(1, 5, ""))
    GCUtil.tryGcSoftlyReachableObjects()

    assertNotNull(strongMarker.get())
    ObjectUtilsRt.reachabilityFence(parent)
    ObjectUtilsRt.reachabilityFence(document)
  }

  @Test
  fun `descendant snapshot retains a disposed strong marker`() {
    val document = DocumentImpl("abcdef", true)
    val parent = document.core.snapshot()
    val strongMarker = createStrongMarker(document, startOffset = 2, endOffset = 4)
    val rootStore = document.rangeMarkers.rootStore()
    val child = document.snapshotMarkerStores.applyPatch(parent, textPatch(0, 0, "X"))

    disposeMarker(strongMarker)
    GCUtil.tryGcSoftlyReachableObjects()

    assertNotNull(strongMarker.get())
    assertTrue(rootStore.containsMarker(child, checkNotNull(strongMarker.get()).markerHandle))
  }

  @Test
  fun `garbage collected marker is purged before an edit is inherited`() {
    val fixture = Fixture("abcdef")
    val weakMarker = createWeakMarker(fixture, startOffset = 2, endOffset = 4)

    gcMarkerAndWaitForProcessQueues(weakMarker)
    fixture.document.insertString(0, "X")

    assertFalse(currentRootContains(fixture, weakMarker.handle))
  }

  @Test
  fun `garbage collected invalid marker is purged`() {
    val fixture = Fixture("abcdef")
    val weakMarker = createWeakInvalidMarker(fixture, 2, 4)
    assertTrue(currentRootContains(fixture, weakMarker.handle))

    gcMarkerAndWaitForProcessQueues(weakMarker)
    assertEquals(0, countOverlappingMarkers(fixture.rootStore, fixture.document.core.snapshot(), startOffset = 0, endOffset = 2))

    assertFalse(currentRootContains(fixture, weakMarker.handle))
  }

  private fun gcMarkerAndWaitForProcessQueues(weakMarker: WeakMarker) {
    GCUtil.tryGcSoftlyReachableObjects { weakMarker.reference.get() == null }
    while (!SnapshotMarkerEngineImpl.processQueue()) {
      Thread.yield()
    }
    assertNull(weakMarker.reference.get())
  }

  @Test
  fun `marker reference does not retain document`() {
    val documentReference = createWeakDocumentWithMarker()

    GCUtil.tryGcSoftlyReachableObjects { documentReference.get() == null }

    assertNull(documentReference.get())
  }

  @Test
  fun `strong marker reference does not retain document graph`() {
    val documentReference = createWeakDocumentWithStrongMarker()

    GCUtil.tryGcSoftlyReachableObjects { documentReference.get() == null }

    assertNull(documentReference.get())
  }

  @Test
  fun `root purge removes state while disposal removal retains it`() {
    val root = PMarkerRootImpl.empty().insert(handle(1), 2, 4, nonGreedySpec(), flavorFlags = 0)

    val removed = root.remove(handle(1)) as PMarkerRootImpl
    val purged = root.purgeMany(listOf(handle(1))) as PMarkerRootImpl

    assertTrue(removed.containsMarker(handle(1)))
    assertFalse(purged.containsMarker(handle(1)))
  }

  @Test
  fun `root aggregates marker measures by start offset`() {
    val root = PMarkerRootImpl.empty()
      .insert(handle(1), 0, 0, nonGreedySpec(), flavorFlags = 0, measure = 2)
      .insert(handle(2), 2, 2, nonGreedySpec(), flavorFlags = 0, measure = 3)
      .insert(handle(3), 2, 2, nonGreedySpec(), flavorFlags = 0, measure = 5)
      .insert(handle(4), 5, 5, nonGreedySpec(), flavorFlags = 0, measure = 7)
      .insert(handle(5), 1, 1, nonGreedySpec(), flavorFlags = 0)

    assertEquals(0, root.getPrefixAggregate(-1))
    assertEquals(2, root.getPrefixAggregate(0))
    assertEquals(2, root.getPrefixAggregate(1))
    assertEquals(10, root.getPrefixAggregate(2))
    assertEquals(17, root.getPrefixAggregate(Int.MAX_VALUE))
  }

  @Test
  fun `non-empty markers reject non-zero measures`() {
    val root = PMarkerRootImpl.empty().insert(handle(1), 1, 2, nonGreedySpec(), flavorFlags = 0)

    assertThrows(IllegalArgumentException::class.java) {
      root.insert(handle(2), 2, 3, nonGreedySpec(), flavorFlags = 0, measure = 1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      root.updateMeasure(handle(1), 1)
    }
  }

  @Test
  fun `root marker measures survive persistent mutations`() {
    val root = PMarkerRootImpl.empty()
      .insert(handle(1), 1, 1, nonGreedySpec(), flavorFlags = 0, measure = 2)
      .insert(handle(2), 3, 3, nonGreedySpec(), flavorFlags = 0, measure = 4)
      .insert(handle(3), 5, 5, nonGreedySpec(), flavorFlags = 0, measure = 8)
    val updated = root.updateMeasure(handle(2), 10)

    assertEquals(14, root.getPrefixAggregate(Int.MAX_VALUE))
    assertEquals(20, updated.getPrefixAggregate(Int.MAX_VALUE))
    assertSame(updated, updated.updateMeasure(handle(2), 10))
    assertSame(updated, updated.updateMeasure(handle(99), 10))

    val shifted = applyPatch(updated, "abcdef", textPatch(startOffset = 0, endOffset = 0, newFragment = "xx"))
    assertEquals(0, shifted.getPrefixAggregate(2))
    assertEquals(2, shifted.getPrefixAggregate(3))

    val invalidated = applyPatch(shifted, "xxabcdef", textPatch(startOffset = 4, endOffset = 6, newFragment = ""))
    assertEquals(10, invalidated.getPrefixAggregate(Int.MAX_VALUE))

    val removed = invalidated.remove(handle(1))
    assertEquals(8, removed.getPrefixAggregate(Int.MAX_VALUE))

    val other = PMarkerRootImpl.empty().insert(handle(4), 0, 0, nonGreedySpec(), flavorFlags = 0, measure = 16)
    val merged = removed.mergeValidMarkersFrom(other)
    assertEquals(24, merged.getPrefixAggregate(Int.MAX_VALUE))
  }

  @Test
  fun `root marker measures survive a full rebuild`() {
    val oldText = "alpha\ntarget\nomega"
    val newText = "prefix\nalpha\ntarget\nomega\nsuffix"
    val markerStart = oldText.indexOf("target") + 1
    val root = PMarkerRootImpl.empty()
      .insert(handle(1), markerStart, markerStart, nonGreedySpec(), flavorFlags = 0, measure = 3, policy = PersistentMarkerPolicy)
      .insert(handle(2), markerStart, markerStart, nonGreedySpec(), flavorFlags = 0, measure = 5)

    val rebuilt = applyPatch(root, oldText, textPatch(startOffset = 0, endOffset = oldText.length, newFragment = newText))
    val expectedStart = newText.indexOf("target") + 1

    assertEquals(0, rebuilt.getPrefixAggregate(expectedStart - 1))
    assertEquals(3, rebuilt.getPrefixAggregate(expectedStart))
    assertEquals(3, rebuilt.getPrefixAggregate(Int.MAX_VALUE))
  }

  @Test
  fun `root aggregates survive random marker and text operations`() {
    val seed = 0x5EED
    val random = Random(seed)
    val maxMeasure = 10
    val maxCharsPerOperation = 10
    val document = DocumentImpl("abcdefghij", true)
    val rootStore = document.rangeMarkers.rootStore()
    var markerIds = mutableListOf<Long>()
    var nextMarkerId = 1L
    handleRoot = rootStore.rootReference(document.core.snapshot()).get()

    repeat(10_000) { iteration ->
      when (random.nextInt(6)) {
        0 -> {
          val markerId = nextMarkerId++
          val offset = random.nextInt(document.textLength + 1)
          val measure = random.nextInt(maxMeasure)
          val spec = MarkerSpec.withStickyToRight(nonGreedySpec(), random.nextBoolean())
          rootStore.updateRoot(document.core.snapshot()) {
            it.insert(handle(markerId), offset, offset, spec, flavorFlags = 0, measure = measure)
          }
        }
        1 -> if (markerIds.isNotEmpty()) {
          val markerId = markerIds.removeAt(random.nextInt(markerIds.size))
          rootStore.updateRoot(document.core.snapshot()) { it.remove(handle(markerId)) }
        }
        2 -> if (markerIds.isNotEmpty()) {
          val markerId = markerIds[random.nextInt(markerIds.size)]
          val measure = random.nextInt(maxMeasure)
          rootStore.updateRoot(document.core.snapshot()) { it.updateMeasure(handle(markerId), measure) }
        }
        3 -> {
          val offset = random.nextInt(document.textLength + 1)
          document.insertString(offset, " ".repeat(random.nextInt(maxCharsPerOperation + 1)))
        }
        4 -> if (document.textLength > 0) {
          val startOffset = random.nextInt(document.textLength)
          val endOffset = minOf(document.textLength, startOffset + 1 + random.nextInt(maxCharsPerOperation))
          document.deleteString(startOffset, endOffset)
        }
        else -> if (document.textLength > 0) {
          val startOffset = random.nextInt(document.textLength)
          val endOffset = minOf(document.textLength, startOffset + 1 + random.nextInt(maxCharsPerOperation))
          val targetOffset = random.nextInt(document.textLength + 1)
          if (targetOffset !in startOffset..endOffset) {
            document.moveText(startOffset, endOffset, targetOffset)
          }
        }
      }

      val root = rootStore.rootReference(document.core.snapshot()).get()
      markerIds = assertPrefixAggregates(root, document.textLength, "Seed $seed, iteration $iteration")
    }
  }

  @Test
  fun `marker entry delegates transformation to its policy`() {
    var receivedPatch: DocumentPatch? = null
    val invalidatedMarkerIds = ArrayList<Long>()
    val policy = MarkerPolicy { _, patch, _, _ ->
      receivedPatch = patch
      MarkerTransformResult(MarkerTransformError.INVALIDATED_BY_EDIT)
    }
    val root = PMarkerRootImpl.empty().insert(
      handle = handle(1),
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec(),
      flavorFlags = 0,
      policy = policy,
    )

    val patch = DocumentPatch.complex(
      startOffset = 2,
      endOffset = 2,
      newFragment = "x",
      newModStamp = 1,
      clearLineFlags = false,
      originStartOffset = 1,
      originEndOffset = 2,
      moveOffset = 3,
    )
    val updated = root.updateSpec(handle(1), MarkerSpec.withGreedyToLeft(nonGreedySpec(), true))
    val edited = applyPatch(updated, "abcd", patch, { invalidatedMarkerIds.add(label(it)) })

    val invalid = edited.resolve(handle(1))
    assertFalse(PMarkerResolution.isValid(invalid))
    assertEquals(MarkerTransformError.INVALIDATED_BY_EDIT.message, edited.invalidationReason(handle(1)))
    assertEquals(listOf(1L), invalidatedMarkerIds)
    assertSame(patch, receivedPatch)
  }

  @Test
  fun `root reports only affected valid markers`() {
    val invalidatedMarkerIds = ArrayList<Long>()
    val affectedMarkerIds = ArrayList<Long>()
    val invalidatingPolicy = MarkerPolicy { _, _, _, _ ->
      MarkerTransformResult(MarkerTransformError.INVALIDATED_BY_EDIT)
    }
    val root = PMarkerRootImpl.empty()
      .insert(handle(1), 0, 1, nonGreedySpec(), flavorFlags = 0)
      .insert(handle(2), 1, 6, nonGreedySpec(), flavorFlags = 0)
      .insert(handle(3), 3, 5, nonGreedySpec(), flavorFlags = 0)
      .insert(handle(4), 7, 9, nonGreedySpec(), flavorFlags = 0)
      .insert(handle(5), 3, 4, nonGreedySpec(), flavorFlags = 0, policy = invalidatingPolicy)

    applyPatch(
      root,
      "abcdefghij",
      textPatch(startOffset = 3, endOffset = 4, newFragment = ""),
      { invalidatedMarkerIds.add(label(it)) },
      { affectedMarkerIds.add(label(it)) },
    )

    assertEquals(listOf(5L), invalidatedMarkerIds)
    assertEquals(listOf(2L, 3L), affectedMarkerIds.sorted())
  }

  @Test
  fun `root flavor filtering survives edits removals and flavor updates`() {
    val firstFlavor: Byte = 1
    val secondFlavor: Byte = 2
    val combinedFlavor: Byte = 3
    val highFlavor = 0x80.toByte()
    val rootWithoutCombined = PMarkerRootImpl.empty()
      .insert(handle(1), 0, 4, nonGreedySpec(), firstFlavor)
      .insert(handle(2), 1, 4, nonGreedySpec(), secondFlavor)

    assertEquals(listOf(1L, 2L), markerIds(rootWithoutCombined, tastePreference = 0))
    assertEquals(listOf(1L), markerIds(rootWithoutCombined, tastePreference = firstFlavor.toInt()))
    assertEquals(listOf(2L), markerIds(rootWithoutCombined, tastePreference = secondFlavor.toInt()))
    assertTrue(markerIds(rootWithoutCombined, tastePreference = combinedFlavor.toInt()).isEmpty())

    val root = rootWithoutCombined
      .insert(handle(3), 2, 4, nonGreedySpec(), combinedFlavor)
      .insert(handle(4), 3, 4, nonGreedySpec(), highFlavor)
    assertEquals(listOf(1L, 3L), markerIds(root, tastePreference = firstFlavor.toInt()))
    assertEquals(listOf(2L, 3L), markerIds(root, tastePreference = secondFlavor.toInt()))
    assertEquals(listOf(3L), markerIds(root, tastePreference = combinedFlavor.toInt()))
    assertEquals(listOf(4L), markerIds(root, tastePreference = 0x80))
    assertEquals(listOf(4L), markerIds(root, tastePreference = highFlavor.toInt()))

    val edited = applyPatch(root, "abcd", textPatch(startOffset = 0, endOffset = 0, newFragment = "x"))
    assertEquals(listOf(3L), markerIds(edited, tastePreference = combinedFlavor.toInt()))

    val removed = edited.remove(handle(3))
    assertTrue(markerIds(removed, tastePreference = combinedFlavor.toInt()).isEmpty())

    val updated = removed.updateFlavor(handle(1), combinedFlavor)
    assertTrue(markerIds(removed, tastePreference = combinedFlavor.toInt()).isEmpty())
    assertEquals(listOf(1L), markerIds(updated, tastePreference = combinedFlavor.toInt()))
  }

  @Test
  fun `root processing reports non-strict overlaps`() {
    val flavor: Byte = 1
    val otherFlavor: Byte = 2
    val root = PMarkerRootImpl.empty()
      .insert(handle(1), 0, 0, nonGreedySpec(), flavor)
      .insert(handle(2), 0, 2, nonGreedySpec(), flavor)
      .insert(handle(3), 2, 4, nonGreedySpec(), flavor)
      .insert(handle(4), 2, 2, nonGreedySpec(), otherFlavor)
      .insert(handle(5), 3, 3, nonGreedySpec(), flavor)

    assertEquals(listOf(1L, 2L), markerIds(root, flavor.toInt(), startOffset = 0, endOffset = 0))
    assertEquals(listOf(2L, 3L), markerIds(root, flavor.toInt(), startOffset = 2, endOffset = 2))
  }

  @Test
  fun `root overlapping iterator reports non-strict overlaps`() {
    val flavor: Byte = 1
    val otherFlavor: Byte = 2
    val root = PMarkerRootImpl.empty()
      .insert(handle(1), 0, 0, nonGreedySpec(), flavor)
      .insert(handle(2), 0, 2, nonGreedySpec(), flavor)
      .insert(handle(3), 2, 4, nonGreedySpec(), flavor)
      .insert(handle(4), 2, 2, nonGreedySpec(), otherFlavor)
      .insert(handle(5), 3, 3, nonGreedySpec(), flavor)

    assertEquals(listOf(1L, 2L), overlappingMarkerIds(root, flavor.toInt(), startOffset = 0, endOffset = 0))
    assertEquals(listOf(2L, 3L), overlappingMarkerIds(root, flavor.toInt(), startOffset = 2, endOffset = 2))
  }

  @Test
  fun `root overlapping iterator respects flavors and lazy shifts`() {
    val firstFlavor: Byte = 1
    val secondFlavor: Byte = 2
    val root = PMarkerRootImpl.empty()
      .insert(handle(1), 0, 2, nonGreedySpec(), firstFlavor)
      .insert(handle(2), 3, 5, nonGreedySpec(), secondFlavor)
      .insert(handle(3), 6, 8, nonGreedySpec(), firstFlavor)
    val edited = applyPatch(root, "abcdefgh", textPatch(startOffset = 0, endOffset = 0, newFragment = "xx"))

    val entries = edited.overlappingIterator(3, 9, firstFlavor.toInt()).asSequence().toList()

    assertEquals(listOf(1L, 3L), entries.map { label(it.handle) })
    assertEquals(listOf(2, 8), entries.map { it.nodeStart })
    assertEquals(listOf(4, 10), entries.map { it.nodeEnd })
  }

  @Test
  fun `concurrent disposal has one successful remover`() {
    val fixture = Fixture("abcdef")
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec(),
    )
    fixture.document.insertString(0, "XX")
    val start = CountDownLatch(1)
    val results = ConcurrentLinkedQueue<Boolean>()
    val threads = List(4) {
      thread(start = true) {
        start.await()
        results.add(SnapshotMarkerEngineImpl.removeRangeMarker(marker))
      }
    }

    start.countDown()
    threads.forEach { it.join() }

    assertEquals(1, results.count { it })
    assertEquals(3, results.count { !it })
    assertEquals(TextRange(4, 6), marker.textRange)
  }

  @Test
  fun `edit may collapse different starts to one offset`() {
    val fixture = Fixture("abcdefgh")
    val initialSnapshot = fixture.initialSnapshot

    // This marker receives the smaller ID but initially has the larger start offset.
    val firstMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 3,
      endOffset = 6,
      spec = nonGreedySpec()
    )
    val secondMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 5,
      spec = nonGreedySpec()
    )

    val child = fixture.edit(initialSnapshot, startOffset = 1, endOffset = 4, newFragment = "")

    assertRange(firstMarker, child, startOffset = 1, endOffset = 3)
    assertRange(secondMarker, child, startOffset = 1, endOffset = 2)
  }

  @Test
  fun `many markers survive AVL rotations and lazy suffix shift`() {
    val fixture = Fixture("x".repeat(4096))
    val initialSnapshot = fixture.initialSnapshot
    val markers = List(512) { index ->
      val startOffset = index * 6
      SnapshotMarkerEngineImpl.createRangeMarker(
        document = fixture.document,
        startOffset = startOffset,
        endOffset = startOffset + 2,
        spec = nonGreedySpec()
      )
    }

    val child = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "abc")

    for (index in listOf(0, 1, 31, 127, 255, 511)) {
      val originalStart = index * 6
      assertRange(markers[index], initialSnapshot, originalStart, originalStart + 2)
      assertRange(markers[index], child, originalStart + 3, originalStart + 5)
    }
  }

  @Test
  fun `RangeMarker methods use current snapshot`() {
    val fixture = Fixture("abcdef")
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    ) as SnapshotRangeMarkerImpl
    fixture.document.insertString(0, "XX")
    val child = fixture.editWithNaturalModSequence(fixture.initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "XX")

    assertSame(fixture.document, marker.document)
    val initialResolution = SnapshotMarkerEngineImpl.resolveRangeMarker(marker, rootFor(marker, fixture.initialSnapshot))
    assertEquals(2, PMarkerResolution.startOffset(initialResolution))
    assertEquals(4, PMarkerResolution.endOffset(initialResolution))

    val childResolution = SnapshotMarkerEngineImpl.resolveRangeMarker(marker, rootFor(marker, child))
    assertEquals(4, PMarkerResolution.startOffset(childResolution))
    assertEquals(6, PMarkerResolution.endOffset(childResolution))
  }

  private fun assertCurrentResolution(marker: SnapshotRangeMarkerImpl, message: String = "", disposedRange: TextRange? = null) {
    val resolution = SnapshotMarkerEngineImpl.resolveRangeMarker(marker, marker.currentRootReference().get())
    val expected = if (PMarkerResolution.isAbsent(resolution)) checkNotNull(disposedRange) { message }
    else PMarkerResolution.toTextRange(resolution)
    val actual = marker.textRange
    assertEquals(expected, actual, message)
    assertEquals(PMarkerResolution.isValid(resolution), marker.isValid, message)
    assertEquals(expected.startOffset, marker.startOffset, message)
    assertEquals(expected.endOffset, marker.endOffset, message)
  }

  private fun assertRange(marker: SnapshotMarker, snapshot: DocumentSnapshot, startOffset: Int, endOffset: Int) {
    val storedMarker = marker as SnapshotRangeMarkerImpl
    val resolution = SnapshotMarkerEngineImpl.resolveRangeMarker(storedMarker, rootFor(storedMarker, snapshot))
    assertEquals(startOffset, PMarkerResolution.startOffset(resolution))
    assertEquals(endOffset, PMarkerResolution.endOffset(resolution))
  }

  private fun assertAbsent(marker: SnapshotMarker, snapshot: DocumentSnapshot) {
    val storedMarker = marker as SnapshotRangeMarkerImpl
    val resolution = SnapshotMarkerEngineImpl.resolveRangeMarker(storedMarker, rootFor(storedMarker, snapshot))
    assertEquals(PMarkerResolution.ABSENT, resolution)
  }

  private fun assertDisposed(marker: SnapshotMarker, snapshot: DocumentSnapshot, startOffset: Int, endOffset: Int) {
    val storedMarker = marker as SnapshotRangeMarkerImpl
    val root = rootFor(storedMarker, snapshot)
    val resolution = SnapshotMarkerEngineImpl.resolveRangeMarker(storedMarker, root)
    assertEquals("Marker is disposed", SnapshotMarkerEngineImpl.invalidationReason(storedMarker, root))
    assertEquals(PMarkerResolution.invalid(TextRangeScalarUtil.toScalarRange(startOffset, endOffset)), resolution)
  }

  private fun rootFor(marker: SnapshotRangeMarkerImpl, snapshot: DocumentSnapshot): PMarkerRoot {
    val document = marker.document as DocumentImpl
    return document.rangeMarkers.rootStore().rootReference(snapshot).get()
  }

  private fun countOverlappingMarkers(
    rootStore: SnapshotMarkerRootStore<*>,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
  ): Int {
    var count = 0
    SnapshotMarkerEngineImpl.processRangeMarkersOverlappingWith(
      rootStore,
      snapshot,
      startOffset,
      endOffset,
      tastePreference = 0,
    ) {
      count++
      true
    }
    return count
  }

  private fun markerIds(
    root: PMarkerRoot,
    tastePreference: Int,
    startOffset: Int = 0,
    endOffset: Int = 100,
  ): List<Long> {
    val result = mutableListOf<Long>()
    root.processRangeMarkersOverlappingWith(
      startOffset = startOffset,
      endOffset = endOffset,
      tastePreference = tastePreference,
    ) {
      result.add(label(it.handle))
      true
    }
    return result
  }

  private fun overlappingMarkerIds(
    root: PMarkerRoot,
    tastePreference: Int,
    startOffset: Int,
    endOffset: Int,
  ): List<Long> = root.overlappingIterator(startOffset, endOffset, tastePreference).asSequence().map { label(it.handle) }.toList()

  private fun assertPrefixAggregates(root: PMarkerRoot, textLength: Int, failureMessage: String): MutableList<Long> {
    val entries = root.overlappingIterator(0, textLength, tastePreference = 0).asSequence().toList()
    var expectedSum = 0
    var index = 0

    while (index < entries.size) {
      val offset = entries[index].nodeStart
      assertEquals(expectedSum, root.getPrefixAggregate(offset - 1), failureMessage)
      while (index < entries.size && entries[index].nodeStart == offset) {
        expectedSum += entries[index].measure
        index++
      }
      assertEquals(expectedSum, root.getPrefixAggregate(offset), failureMessage)
    }
    assertEquals(expectedSum, root.getPrefixAggregate(textLength + 1), failureMessage)
    return entries.mapTo(mutableListOf()) { label(it.handle) }
  }

  private fun currentRootContains(fixture: Fixture, handle: SnapshotMarkerReference<*>): Boolean =
    fixture.rootStore.containsMarker(fixture.document.core.snapshot(), handle)

  private fun createWeakMarker(fixture: Fixture, startOffset: Int, endOffset: Int): WeakMarker {
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = startOffset,
      endOffset = endOffset,
      spec = nonGreedySpec(),
    ) as SnapshotRangeMarkerImpl
    return WeakMarker(marker.markerHandle, WeakReference(marker))
  }

  private fun createStrongMarker(document: DocumentImpl, startOffset: Int, endOffset: Int): WeakReference<SnapshotRangeMarkerImpl> {
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = document,
      startOffset = startOffset,
      endOffset = endOffset,
      spec = nonGreedySpec(),
      retainStrong = true,
    ) as SnapshotRangeMarkerImpl
    return WeakReference(marker)
  }

  private fun createInvalidStrongMarker(document: DocumentImpl, startOffset: Int, endOffset: Int): WeakReference<SnapshotRangeMarkerImpl> {
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = document,
      startOffset = startOffset,
      endOffset = endOffset,
      spec = nonGreedySpec(),
      retainStrong = true,
    ) as SnapshotRangeMarkerImpl
    val result = WeakReference(marker)
    document.deleteString(1, 5)
    check(!marker.isValid)
    return result
  }

  private fun disposeMarker(marker: WeakReference<SnapshotRangeMarkerImpl>) {
    marker.get()!!.dispose()
  }

  private fun createWeakInvalidMarker(fixture: Fixture, startOffset: Int, endOffset: Int): WeakMarker {
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      startOffset = startOffset,
      endOffset = endOffset,
      spec = nonGreedySpec(),
    ) as SnapshotRangeMarkerImpl
    fixture.document.deleteString(1, 5)
    check(!marker.isValid)
    return WeakMarker(marker.markerHandle, WeakReference(marker))
  }

  private fun createWeakDocumentWithMarker(): WeakReference<DocumentImpl> {
    val document = DocumentImpl("abcdef", true)
    document.createRangeMarker(2, 4)
    return WeakReference(document)
  }

  private fun createWeakDocumentWithStrongMarker(): WeakReference<DocumentImpl> {
    val document = DocumentImpl("abcdef", true)
    SnapshotMarkerEngineImpl.createRangeMarker(
      document = document,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec(),
      retainStrong = true,
    )
    return WeakReference(document)
  }

  private data class WeakMarker(
    val handle: SnapshotMarkerReference<*>,
    val reference: WeakReference<SnapshotRangeMarkerImpl>,
  )

  private fun nonGreedySpec(): Byte = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)

  private fun textPatch(startOffset: Int, endOffset: Int, newFragment: String): DocumentPatch {
    return DocumentPatch.simple(
      startOffset = startOffset,
      endOffset = endOffset,
      newFragment = newFragment,
      newModStamp = 1,
      clearLineFlags = false,
    )
  }

  private fun applyPatch(
    root: PMarkerRoot,
    before: String,
    patch: DocumentPatch,
    invalidatedMarkerConsumer: Consumer<SnapshotMarkerReference<*>> = PMarkerRoot.EMPTY_HANDLE_CONSUMER,
    affectedMarkerConsumer: Consumer<SnapshotMarkerReference<*>> = PMarkerRoot.EMPTY_HANDLE_CONSUMER,
  ): PMarkerRoot {
    val beforeText = DocumentImpl(before, true).core.snapshot().text()
    val afterText = beforeText.replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
    return root.applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)
  }

  private class Fixture(initialText: String) {
    val document = DocumentImpl(initialText, true)
    val initialSnapshot: DocumentSnapshot = document.core.snapshot()
    val rootStore: SnapshotMarkerRootStore<*> = document.rangeMarkers.rootStore()

    private val markerStores: SnapshotMarkerStores = document.snapshotMarkerStores

    private var nextModSequence: Int = initialSnapshot.modState().sequence() + 1
    private var nextModStamp: Long = initialSnapshot.modState().stamp() + 1

    fun editWithNaturalModSequence(
      parent: DocumentSnapshot,
      startOffset: Int,
      endOffset: Int,
      newFragment: String,
    ): DocumentSnapshot {
      require(startOffset in 0..endOffset)
      require(endOffset <= parent.text().length())

      return applyTextEdit(parent, startOffset, endOffset, newFragment, nextModStamp++)
    }

    fun edit(parent: DocumentSnapshot, startOffset: Int, endOffset: Int, newFragment: String): DocumentSnapshot {
      require(startOffset in 0..endOffset)
      require(endOffset <= parent.text().length())

      var newModStamp = nextModStamp++

      var child = applyTextEdit(parent, startOffset, endOffset, newFragment, newModStamp)
      val targetModSequence = maxOf(nextModSequence, child.modState().sequence())

      while (child.modState().sequence() < targetModSequence) {
        newModStamp = nextModStamp++
        val op = DocumentOp.modStampOp(newModStamp, true)
        val nextChild = child.applyOp(op)
        markerStores.applyPatch(child, nextChild, null)
        child = nextChild
      }

      check(child.modState().sequence() == targetModSequence) {
        "Expected modSequence $targetModSequence, got ${child.modState().sequence()}"
      }
      nextModSequence = targetModSequence + 1

      return child
    }

    private fun applyTextEdit(
      parent: DocumentSnapshot,
      startOffset: Int,
      endOffset: Int,
      newFragment: String,
      newModStamp: Long,
    ): DocumentSnapshot {
      return markerStores.applyPatch(
        parent,
        DocumentPatch.simple(
          startOffset = startOffset,
          endOffset = endOffset,
          newFragment = newFragment,
          newModStamp = newModStamp,
          clearLineFlags = false,
        )
      )
    }
  }
}
