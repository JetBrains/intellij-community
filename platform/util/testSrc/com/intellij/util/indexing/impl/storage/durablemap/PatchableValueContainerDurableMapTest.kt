// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap

import com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap.PatchableValueExternalizer
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer
import com.intellij.util.indexing.impl.UpdatableValueContainer
import com.intellij.util.indexing.impl.ValueContainerImpl
import com.intellij.util.io.EnumeratorStringDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Path

/** Checks that the index adapter stores changes without loading the old container */
@Suppress("SuspiciousPackagePrivateAccess") // The tests and the adapter use the same class loader
@Timeout(30)
class PatchableValueContainerDurableMapTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun mergeWritesOnlyChangesAndPreservesDeletionsAfterReopen() {
    val codec = ReadCheckingCodec()
    val factory = DurableDatabaseFactory(1024 * 1024)
    factory.open(directory).use { database ->
      val map = database.openMap("index", 1, stringAsUTF8(), codec)
      ValueContainerDurableMap(map, EnumeratorStringDescriptor.INSTANCE, false).use { adapter ->
        adapter.put("key", container(1 to "old", 2 to "keep"))
        codec.allowReads = false
        val changes = adapter.getModifiableValueContainer("key")
        changes.removeAssociatedValue(1)
        changes.addValue(1, "new")
        adapter.merge("key", changes)
        val deletion = adapter.getModifiableValueContainer("key")
        deletion.removeAssociatedValue(2)
        adapter.merge("key", deletion)
        assertFalse(changes.containsCachedMergedData(), "Writing the diff must not load its initializer")
        assertFalse(deletion.containsCachedMergedData())
        codec.allowReads = true
        assertEquals(mapOf(1 to "new"), contents(map.get("key")))
      }
    }
    factory.open(directory).use { database ->
      val map = database.openMap("index", 1, stringAsUTF8(), codec)
      assertEquals(mapOf(1 to "new"), contents(map.get("key")))
      ValueContainerDurableMap(map, EnumeratorStringDescriptor.INSTANCE, false).use { adapter ->
        val changes = adapter.getModifiableValueContainer("key")
        changes.removeAssociatedValue(1)
        changes.addValue(3, "after reopen")
        adapter.merge("key", changes)
        assertEquals(mapOf(3 to "after reopen"), contents(map.get("key")))
      }
    }
  }

  @Test
  fun deletionOnlyAndEmptyPatchesCreateEmptyContainers() {
    DurableDatabaseFactory(1024 * 1024).open(directory).use { database ->
      val map = database.openMap("index", 1, stringAsUTF8(), ReadCheckingCodec())
      ValueContainerDurableMap(map, EnumeratorStringDescriptor.INSTANCE, false).use { adapter ->
        val deletion = adapter.getModifiableValueContainer("deleted")
        deletion.removeAssociatedValue(1)
        adapter.merge("deleted", deletion)
        adapter.merge("empty", adapter.getModifiableValueContainer("empty"))
        assertTrue(map.containsMapping("deleted"))
        assertTrue(map.containsMapping("empty"))
        assertEquals(emptyMap<Int, String>(), contents(map.get("deleted")))
        assertEquals(emptyMap<Int, String>(), contents(map.get("empty")))
      }
    }
  }

  @Test
  fun compactionAndUniqueKeysUseSnapshots() {
    for (uniqueKey in listOf(false, true)) {
      DurableDatabaseFactory(1024 * 1024).open(directory.resolve(uniqueKey.toString())).use { database ->
        val map = database.openMap("index", 1, stringAsUTF8(), ReadCheckingCodec())
        ValueContainerDurableMap(map, EnumeratorStringDescriptor.INSTANCE, uniqueKey).use { adapter ->
          adapter.put("key", container(1 to "old", 2 to "keep"))
          val changes = adapter.getModifiableValueContainer("key")
          changes.removeAssociatedValue(1)
          changes.addValue(3, "new")
          changes.setNeedsCompacting(!uniqueKey)
          adapter.merge("key", changes)
          assertTrue(changes.containsCachedMergedData(), "The snapshot path must merge the stored value and the changes")
          assertEquals(mapOf(2 to "keep", 3 to "new"), contents(map.get("key")))
        }
      }
    }
  }

  private fun container(vararg entries: Pair<Int, String>): UpdatableValueContainer<String> =
    ValueContainerImpl.createNewValueContainer<String>().apply {
      for ((inputId, value) in entries) addValue(inputId, value)
    }

  private fun contents(container: UpdatableValueContainer<String>?): Map<Int, String> {
    checkNotNull(container)
    val values = mutableMapOf<Int, String>()
    container.forEach { inputId, value -> values[inputId] = value; true }
    return values
  }

  private class ReadCheckingCodec(
    private val delegate: PatchableValueContainerExternalizer<String> = PatchableValueContainerExternalizer(EnumeratorStringDescriptor.INSTANCE),
  ) : PatchableValueExternalizer<UpdatableValueContainer<String>, ChangeTrackingValueContainer<String>> by delegate {
    var allowReads = true

    override fun read(input: ByteBuffer): UpdatableValueContainer<String> {
      check(allowReads) { "Writing a patch must not read the old container" }
      return delegate.read(input)
    }
  }
}
