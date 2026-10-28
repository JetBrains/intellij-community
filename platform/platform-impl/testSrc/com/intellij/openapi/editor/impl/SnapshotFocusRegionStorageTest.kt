// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.util.Processor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.ref.Reference

internal class SnapshotFocusRegionStorageTest {
  @Test
  fun `queue cleanup purges collected regions in one batch`() {
    Fixture().use { fixture ->
      fixture.references.forEach { assertThat(it.enqueue()).isTrue() }
      val visited = ArrayList<RangeMarkerEx>()

      val completed = fixture.storage.processOverlapping(0, 6) {
        visited.add(it)
        true
      }

      assertThat(completed).isTrue()
      assertThat(visited).containsExactly(fixture.live)
      fixture.assertPurged()
    }
  }

  @Test
  fun `traversal purges one batch even when the processor stops or throws`() {
    for (throwFailure in listOf(false, true)) {
      Fixture().use { fixture ->
        val failure = IllegalStateException("The focus region processor failed")
        val processor = Processor<RangeMarkerEx> {
          assertThat(it).isSameAs(fixture.live)
          assertThat(fixture.batches).isEmpty()
          if (throwFailure) throw failure
          false
        }

        if (throwFailure) {
          assertThatThrownBy { fixture.storage.processOverlapping(0, 6, processor) }.isSameAs(failure)
        }
        else {
          assertThat(fixture.storage.processOverlapping(0, 6, processor)).isFalse()
        }

        fixture.assertPurged()
      }
    }
  }

  private class Fixture : AutoCloseable {
    val storage = SnapshotFocusRegionStorage(DocumentImpl("abcdef", true))
    private val collected: List<SnapshotFocusRegion> = listOf(storage.create(0, 1), storage.create(2, 3))
    val live = storage.create(4, 5)
    private val snapshot: DocumentSnapshot = storage.currentSnapshot()
    val batches = ArrayList<List<SnapshotMarkerReference<*>>>()
    val references: List<Reference<*>>

    init {
      val reference = storage.rootStore.rootReference(snapshot)
      val root = reference.get()
      references = root.overlappingIterator(0, 6, 0).asSequence()
        .filter { it.handle !== live.markerHandle }
        .map { it.handle as Reference<*> }
        .toList()
      references.forEach { it.clear() }
      reference.set(object : PMarkerRoot by root {
        override fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot {
          batches.add(handles.toList())
          return root.purgeMany(handles)
        }
      })
    }

    fun assertPurged() {
      assertThat(batches).hasSize(1)
      assertThat(batches.single()).containsExactlyInAnyOrderElementsOf(collected.map { it.markerHandle })
      for (region in collected) {
        assertThat(storage.rootStore.containsMarker(snapshot, region.markerHandle)).isFalse()
      }
      assertThat(live.isValid).isTrue()
    }

    override fun close() {
      storage.dispose()
    }
  }
}
