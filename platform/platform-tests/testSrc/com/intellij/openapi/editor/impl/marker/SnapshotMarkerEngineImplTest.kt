// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentNewOps
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.RangeMarkerStorageImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.ref.GCUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class SnapshotMarkerEngineImplTest {
  @Test
  fun `persistent marker uses snapshot engine`() {
    RangeMarkerStorageImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("text")

      val marker = document.createRangeMarker(1, 3, true)

      assertTrue(marker is PMarker)
    }
  }

  @Test
  fun `clean snapshot merge keeps markers created in both branches`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val primary = initialSnapshot.applyOp(DocumentNewOps.getInstance().createModStampOp(1, true))
    val metadata = initialSnapshot.applyOp(DocumentNewOps.getInstance().createModStampOp(2, true))
    val primaryMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = primary,
      startOffset = 1,
      endOffset = 4,
      spec = nonGreedySpec(),
    )
    val metadataMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = metadata,
      startOffset = 5,
      endOffset = 5,
      spec = nonGreedySpec(),
    )

    val merged = SnapshotMarkerEngineImpl.mergeMarkerRoots(primary, metadata)

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
      snapshot = initialSnapshot,
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
      snapshot = initialSnapshot,
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
      snapshot = initialSnapshot,
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
      snapshot = initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )

    val futureChild = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "YY")

    assertRange(marker, initialSnapshot, startOffset = 2, endOffset = 4)
    assertAbsent(marker, existingChild, startOffset = 2, endOffset = 4)
    assertRange(marker, futureChild, startOffset = 4, endOffset = 6)
  }

  @Test
  fun `engine removal disposes marker across snapshots`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )
    val existingChild = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "X")

    assertTrue(SnapshotMarkerEngineImpl.removeRangeMarker(initialSnapshot, marker))
    assertFalse(SnapshotMarkerEngineImpl.removeRangeMarker(initialSnapshot, marker))

    val futureChild = fixture.edit(initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "YY")

    assertDisposed(marker, initialSnapshot, startOffset = 2, endOffset = 4)
    assertDisposed(marker, existingChild, startOffset = 3, endOffset = 5)
    assertDisposed(marker, futureChild, startOffset = 2, endOffset = 4)
  }

  @Test
  fun `left greediness controls insertion at marker start`() {
    val fixture = Fixture("abcdef")
    val initialSnapshot = fixture.initialSnapshot
    val greedy = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = MarkerSpec(isGreedyToLeft = true, isGreedyToRight = false)
    )
    val nonGreedy = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = false)
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
      snapshot = initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = true)
    )
    val nonGreedy = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = false)
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
      snapshot = initialSnapshot,
      startOffset = 2,
      endOffset = 2,
      spec = nonGreedySpec()
    )
    val sticking = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
      startOffset = 2,
      endOffset = 2,
      spec = nonGreedySpec()
    ) as SnapshotRangeMarkerImpl
    val greedy = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
      startOffset = 2,
      endOffset = 2,
      spec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = true)
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
      snapshot = initialSnapshot,
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
    val initialSnapshot = document.core.snapshot()
    val markerStart = oldText.indexOf("target") + 1
    val persistentMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      initialSnapshot,
      markerStart,
      markerStart + 3,
      persistentSpec(),
    )
    val ordinaryMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      initialSnapshot,
      markerStart,
      markerStart + 3,
      nonGreedySpec(),
    )

    document.replaceString(0, oldText.length, newText)
    val replacedSnapshot = document.core.snapshot()
    val expectedStart = newText.indexOf("target") + 1

    assertRange(persistentMarker, replacedSnapshot, expectedStart, expectedStart + 3)
    assertTrue(ordinaryMarker.resolve(replacedSnapshot) is PMarkerResolution.Invalid)
  }

  @Test
  fun `large partial replacement translates persistent marker through line diff`() {
    val oldText = "x\nold-a\ntarget\nold-b\ny"
    val replacement = "new-a\nextra\npadding\ntarget\nnew-b\n"
    val document = DocumentImpl(oldText, true)
    val initialSnapshot = document.core.snapshot()
    val markerStart = oldText.indexOf("target") + 1
    val persistentMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      initialSnapshot,
      markerStart,
      markerStart + 3,
      persistentSpec(),
    )
    val ordinaryMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      initialSnapshot,
      markerStart,
      markerStart + 3,
      nonGreedySpec(),
    )
    val replacementStart = oldText.indexOf("old-a")
    val replacementEnd = oldText.indexOf('y')

    document.replaceString(replacementStart, replacementEnd, replacement)
    val replacedSnapshot = document.core.snapshot()
    val expectedStart = document.text.indexOf("target") + 1

    assertRange(persistentMarker, replacedSnapshot, expectedStart, expectedStart + 3)
    assertTrue(ordinaryMarker.resolve(replacedSnapshot) is PMarkerResolution.Invalid)
  }

  @Test
  fun `small partial replacement uses ordinary marker transformation`() {
    val oldText = "stable0\nstable1\nold-a\ntarget\nold-b\nstable2\nstable3\nstable4"
    val replacement = "new-a\ntarget\nnew-b\n"
    val document = DocumentImpl(oldText, true)
    val initialSnapshot = document.core.snapshot()
    val markerStart = oldText.indexOf("target") + 1
    val persistentMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document,
      initialSnapshot,
      markerStart,
      markerStart + 3,
      persistentSpec(),
    )
    val replacementStart = oldText.indexOf("old-a")
    val replacementEnd = oldText.indexOf("stable2")

    document.replaceString(replacementStart, replacementEnd, replacement)

    assertTrue(persistentMarker.resolve(document.core.snapshot()) is PMarkerResolution.Invalid)
  }

  @Test
  fun `deletion before marker shifts marker left only in child`() {
    val fixture = Fixture("abcdefgh")
    val initialSnapshot = fixture.initialSnapshot
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
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
    val initialSnapshot = fixture.initialSnapshot
    val pointMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
      startOffset = 5,
      endOffset = 5,
      spec = nonGreedySpec()
    )
    val rangeMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
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
    val initialSnapshot = fixture.initialSnapshot
    val firstMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
      startOffset = 1,
      endOffset = 3,
      spec = nonGreedySpec()
    )
    val secondMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
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
      snapshot = initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )

    val child = fixture.edit(initialSnapshot, startOffset = 1, endOffset = 5, newFragment = "")

    assertRange(marker, initialSnapshot, startOffset = 2, endOffset = 4)
    val invalid = SnapshotMarkerEngineImpl.resolveRangeMarker(marker, child) as PMarkerResolution.Invalid
    assertEquals(TextRange(2, 4), invalid)
    assertTrue(SnapshotMarkerEngineImpl.removeRangeMarker(child, marker))
    assertDisposed(marker, child, startOffset = 2, endOffset = 4)
  }

  @Test
  fun `disposed marker retains its range from the current root`() {
    val fixture = Fixture("abcdef")
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = fixture.initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )

    assertEquals(1, countOverlappingMarkers(fixture.initialSnapshot, startOffset = 0, endOffset = 6))

    marker.dispose()

    val resolution = marker.resolve(fixture.initialSnapshot) as PMarkerResolution.Invalid
    assertEquals("Marker is disposed", resolution.reason)
    assertEquals(TextRange(2, 4), resolution)
    assertFalse(marker.isValid)
    assertEquals(TextRange(2, 4), marker.textRange)
    assertEquals(0, countOverlappingMarkers(fixture.initialSnapshot, startOffset = 0, endOffset = 6))
  }

  @Test
  fun `document removal disposes marker and removes it from the current root`() {
    val fixture = Fixture("abcdef")
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = fixture.initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )
    val markerId = marker.id

    assertTrue(fixture.document.removeRangeMarker(marker))

    val resolution = marker.resolve(fixture.initialSnapshot) as PMarkerResolution.Invalid
    assertEquals("Marker is disposed", resolution.reason)
    assertFalse(marker.isValid)
    assertEquals(0, countOverlappingMarkers(fixture.initialSnapshot, startOffset = 0, endOffset = 6))
    assertFalse(fixture.document.removeRangeMarker(marker))
  }

  @Test
  fun `enumeration returns the original marker with its user data`() {
    val fixture = Fixture("abcdef")
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = fixture.initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec(),
    ) as SnapshotRangeMarkerImpl
    val key = Key.create<String>("snapshot.marker.test.data")
    marker.putUserData(key, "value")

    var processedMarker: RangeMarkerEx? = null
    assertTrue(
      SnapshotMarkerEngineImpl.processRangeMarkersOverlappingWith(
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
      snapshot = fixture.initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec(),
    )

    var processedMarker: RangeMarkerEx? = null
    assertTrue(
      SnapshotMarkerEngineImpl.processRangeMarkersOverlappingWith(
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
  fun `garbage collected marker is purged from the current root`() {
    val fixture = Fixture("abcdef")
    val weakMarker = createWeakMarker(fixture, startOffset = 2, endOffset = 4)
    assertTrue(currentRootContains(fixture, weakMarker.markerId))

    gcMarkerAndWaitForProcessQueues(weakMarker)

    assertEquals(0, countOverlappingMarkers(fixture.initialSnapshot, startOffset = 0, endOffset = 6))
    assertFalse(currentRootContains(fixture, weakMarker.markerId))
  }

  @Test
  fun `garbage collected marker is purged before an edit is inherited`() {
    val fixture = Fixture("abcdef")
    val weakMarker = createWeakMarker(fixture, startOffset = 2, endOffset = 4)

    gcMarkerAndWaitForProcessQueues(weakMarker)
    fixture.document.insertString(0, "X")

    assertFalse(currentRootContains(fixture, weakMarker.markerId))
  }

  @Test
  fun `garbage collected invalid marker is purged`() {
    val fixture = Fixture("abcdef")
    val weakMarker = createWeakInvalidMarker(fixture, 2, 4)
    assertTrue(currentRootContains(fixture, weakMarker.markerId))

    gcMarkerAndWaitForProcessQueues(weakMarker)
    assertEquals(0, countOverlappingMarkers(fixture.document.core.snapshot(), startOffset = 0, endOffset = 2))

    assertFalse(currentRootContains(fixture, weakMarker.markerId))
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
  fun `root purge removes state while disposal removal retains it`() {
    val root = PMarkerRootImpl.empty().insert(1, 2, 4, nonGreedySpec())

    val removed = root.remove(1) as PMarkerRootImpl
    val purged = root.purge(1) as PMarkerRootImpl

    assertTrue(removed.containsMarkerId(1))
    assertFalse(purged.containsMarkerId(1))
  }

  @Test
  fun `marker spec delegates transformation to its policy`() {
    var receivedPatch: DocumentTextPatch? = null
    val policy = MarkerPolicy { _, patch, _, _ ->
      receivedPatch = patch
      MarkerTransformResult.Invalid("Invalidated by test marker policy")
    }
    val root = PMarkerRootImpl.empty().insert(
      markerId = 1,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec().copy(policy = policy),
    )

    val patch = DocumentTextPatch.complex(
      startOffset = 2,
      endOffset = 2,
      newFragment = "x",
      newModStamp = 1,
      clearLineFlags = false,
      originStartOffset = 1,
      originEndOffset = 2,
      moveOffset = 3,
    )
    val edited = applyPatch(root, "abcd", patch)

    val invalid = edited.resolve(1, TextRange(0, 0)) as PMarkerResolution.Invalid
    assertEquals("Invalidated by test marker policy", invalid.reason)
    assertSame(patch, receivedPatch)
  }

  @Test
  fun `root flavor filtering survives edits removals and flavor updates`() {
    val firstFlavor: Byte = 1
    val secondFlavor: Byte = 2
    val combinedFlavor: Byte = 3
    val highFlavor = 0x80.toByte()
    val rootWithoutCombined = PMarkerRootImpl.empty()
      .insert(1, 0, 4, nonGreedySpec(), firstFlavor)
      .insert(2, 1, 4, nonGreedySpec(), secondFlavor)

    assertEquals(listOf(1L, 2L), markerIds(rootWithoutCombined, tastePreference = 0))
    assertEquals(listOf(1L), markerIds(rootWithoutCombined, tastePreference = firstFlavor.toInt()))
    assertEquals(listOf(2L), markerIds(rootWithoutCombined, tastePreference = secondFlavor.toInt()))
    assertTrue(markerIds(rootWithoutCombined, tastePreference = combinedFlavor.toInt()).isEmpty())

    val root = rootWithoutCombined
      .insert(3, 2, 4, nonGreedySpec(), combinedFlavor)
      .insert(4, 3, 4, nonGreedySpec(), highFlavor)
    assertEquals(listOf(1L, 3L), markerIds(root, tastePreference = firstFlavor.toInt()))
    assertEquals(listOf(2L, 3L), markerIds(root, tastePreference = secondFlavor.toInt()))
    assertEquals(listOf(3L), markerIds(root, tastePreference = combinedFlavor.toInt()))
    assertEquals(listOf(4L), markerIds(root, tastePreference = 0x80))
    assertEquals(listOf(4L), markerIds(root, tastePreference = highFlavor.toInt()))

    val edited = applyPatch(root, "abcd", textPatch(startOffset = 0, endOffset = 0, newFragment = "x"))
    assertEquals(listOf(3L), markerIds(edited, tastePreference = combinedFlavor.toInt()))

    val removed = edited.remove(3)
    assertTrue(markerIds(removed, tastePreference = combinedFlavor.toInt()).isEmpty())

    val updated = removed.updateFlavor(1, combinedFlavor)
    assertTrue(markerIds(removed, tastePreference = combinedFlavor.toInt()).isEmpty())
    assertEquals(listOf(1L), markerIds(updated, tastePreference = combinedFlavor.toInt()))
  }

  @Test
  fun `concurrent disposal has one successful remover`() {
    val fixture = Fixture("abcdef")
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = fixture.initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec(),
    )
    val start = CountDownLatch(1)
    val results = ConcurrentLinkedQueue<Boolean>()
    val threads = List(4) {
      thread(start = true) {
        start.await()
        results.add(SnapshotMarkerEngineImpl.removeRangeMarker(fixture.initialSnapshot, marker))
      }
    }

    start.countDown()
    threads.forEach { it.join() }

    assertEquals(1, results.count { it })
    assertEquals(3, results.count { !it })
  }

  @Test
  fun `edit may collapse different starts to one offset`() {
    val fixture = Fixture("abcdefgh")
    val initialSnapshot = fixture.initialSnapshot

    // This marker receives the smaller ID but initially has the larger start offset.
    val firstMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
      startOffset = 3,
      endOffset = 6,
      spec = nonGreedySpec()
    )
    val secondMarker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = initialSnapshot,
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
        snapshot = initialSnapshot,
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
      snapshot = fixture.initialSnapshot,
      startOffset = 2,
      endOffset = 4,
      spec = nonGreedySpec()
    )
    fixture.document.insertString(0, "XX")
    val child = fixture.editWithNaturalModSequence(fixture.initialSnapshot, startOffset = 0, endOffset = 0, newFragment = "XX")

    assertSame(fixture.document, marker.document)
    assertEquals(2, marker.getStartOffset(fixture.initialSnapshot))
    assertEquals(4, marker.getEndOffset(fixture.initialSnapshot))

    assertEquals(4, marker.getStartOffset(child))
    assertEquals(6, marker.getEndOffset(child))
  }

  private fun assertRange(marker: PMarker, snapshot: DocumentSnapshot, startOffset: Int, endOffset: Int) {
    assertEquals(startOffset, marker.getStartOffset(snapshot))
    assertEquals(endOffset, marker.getEndOffset(snapshot))
  }

  private fun assertAbsent(marker: PMarker, snapshot: DocumentSnapshot, startOffset: Int, endOffset: Int) {
    val resolution = SnapshotMarkerEngineImpl.resolveRangeMarker(marker, snapshot) as PMarkerResolution.Absent
    assertEquals(TextRange(startOffset, endOffset), resolution)
  }

  private fun assertDisposed(marker: PMarker, snapshot: DocumentSnapshot, startOffset: Int, endOffset: Int) {
    val resolution = marker.resolve(snapshot) as PMarkerResolution.Invalid
    assertEquals("Marker is disposed", resolution.reason)
    assertEquals(TextRange(startOffset, endOffset), resolution)
  }

  private fun countOverlappingMarkers(
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
  ): Int {
    var count = 0
    SnapshotMarkerEngineImpl.processRangeMarkersOverlappingWith(
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

  private fun markerIds(root: PMarkerRoot, tastePreference: Int): List<Long> {
    val result = ArrayList<Long>()
    root.processRangeMarkersOverlappingWith(
      startOffset = 0,
      endOffset = 100,
      tastePreference = tastePreference,
    ) {
      result.add(it.markerId)
      true
    }
    return result
  }

  private fun currentRootContains(fixture: Fixture, markerId: Long): Boolean =
    SnapshotMarkerEngineImpl.containsMarkerId(fixture.document.core.snapshot(), markerId)

  private fun createWeakMarker(fixture: Fixture, startOffset: Int, endOffset: Int): WeakMarker {
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = fixture.document.core.snapshot(),
      startOffset = startOffset,
      endOffset = endOffset,
      spec = nonGreedySpec(),
    ) as SnapshotRangeMarkerImpl
    return WeakMarker(marker.id, WeakReference(marker))
  }

  private fun createWeakInvalidMarker(fixture: Fixture, startOffset: Int, endOffset: Int): WeakMarker {
    val marker = SnapshotMarkerEngineImpl.createRangeMarker(
      document = fixture.document,
      snapshot = fixture.document.core.snapshot(),
      startOffset = startOffset,
      endOffset = endOffset,
      spec = nonGreedySpec(),
    ) as SnapshotRangeMarkerImpl
    fixture.document.deleteString(1, 5)
    check(!marker.isValid)
    return WeakMarker(marker.id, WeakReference(marker))
  }

  private fun createWeakDocumentWithMarker(): WeakReference<DocumentImpl> {
    val document = DocumentImpl("abcdef", true)
    document.createRangeMarker(2, 4)
    return WeakReference(document)
  }

  private data class WeakMarker(
    val markerId: Long,
    val reference: WeakReference<SnapshotRangeMarkerImpl>,
  )

  private fun nonGreedySpec(): MarkerSpec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = false)

  private fun persistentSpec(): MarkerSpec = nonGreedySpec().copy(policy = PersistentMarkerPolicy)

  private fun textPatch(startOffset: Int, endOffset: Int, newFragment: String): DocumentTextPatch {
    return DocumentTextPatch.simple(
      startOffset = startOffset,
      endOffset = endOffset,
      newFragment = newFragment,
      newModStamp = 1,
      clearLineFlags = false,
    )
  }

  private fun applyPatch(root: PMarkerRoot, before: String, patch: DocumentTextPatch): PMarkerRoot {
    val beforeText = DocumentImpl(before, true).core.snapshot().text()
    val afterText = beforeText.applyOp(patch)
    return root.applyPatch(patch, beforeText, afterText)
  }

  private class Fixture(initialText: String) {
    val document = DocumentImpl(initialText, true)
    val initialSnapshot: DocumentSnapshot = document.core.snapshot()

    private val newOps = DocumentNewOps.getInstance()
    private var nextModSequence = initialSnapshot.modState().sequence() + 1
    private var nextModStamp = initialSnapshot.modState().stamp() + 1

    fun editWithNaturalModSequence(
      parent: DocumentSnapshot,
      startOffset: Int,
      endOffset: Int,
      newFragment: String
    ): DocumentSnapshot {
      require(startOffset in 0..endOffset)
      require(endOffset <= parent.text().length())

      return applyTextEdit(parent, startOffset, endOffset, newFragment, nextModStamp++)
    }

    fun edit(parent: DocumentSnapshot, startOffset: Int, endOffset: Int, newFragment: String): DocumentSnapshot {
      require(startOffset in 0..endOffset)
      require(endOffset <= parent.text().length())

      val targetModSequence = maxOf(nextModSequence, parent.modState().sequence() + 1)
      var newModStamp = nextModStamp++

      var child = applyTextEdit(parent, startOffset, endOffset, newFragment, newModStamp)

      while (child.modState().sequence() < targetModSequence) {
        newModStamp = nextModStamp++
        child = child.applyOp(newOps.createModStampOp(newModStamp, true))
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
      return parent.applyOp(
        DocumentTextPatch.simple(
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
