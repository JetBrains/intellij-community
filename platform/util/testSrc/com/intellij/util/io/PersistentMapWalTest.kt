// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.testFramework.TemporaryDirectory
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.nio.file.Path
import kotlin.io.path.fileSize

class PersistentMapWalTest {
  @Rule
  @JvmField
  val tempDirectory: TemporaryDirectory = TemporaryDirectory()

  @Rule
  @JvmField
  val debugWal: TestRule = TestRule { base, _ ->
    object : Statement() {
      override fun evaluate() {
        debugWalRecords = true
        try {
          base.evaluate()
        }
        finally {
          debugWalRecords = false
        }
      }
    }
  }

  @Test
  fun `test single put`() {
    val mapFile = tempDirectory.createDir().resolve("map")

    val key = "b52"
    val value = ByteArraySequence(byteArrayOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()))

    fillMap(mapFile) {
      put(key, value)
    }

    val events = readWal(mapFile)
    Assert.assertEquals(1, events.size)
    val event = events[0] as WalEvent.PutEvent
    Assert.assertEquals(key, event.key)
    Assert.assertEquals(value, event.value)
  }

  @Test
  fun `test single append`() {
    val mapFile = tempDirectory.createDir().resolve("map")

    val key = "b52"
    val value = byteArrayOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte())

    fillMap(mapFile) {
      appendData(key, AppendablePersistentMap.ValueDataAppender { out -> out.write(value) })
    }

    val events = readWal(mapFile)
    Assert.assertEquals(1, events.size)
    val event = events[0] as WalEvent.AppendEvent
    Assert.assertEquals(key, event.key)
    Assert.assertArrayEquals(value, event.data)
  }

  @Test
  fun `test single remove`() {
    val mapFile = tempDirectory.createDir().resolve("map")

    val key = "b52"

    fillMap(mapFile) {
      remove(key)
    }

    val events = readWal(mapFile)
    Assert.assertEquals(1, events.size)
    val event = events[0] as WalEvent.RemoveEvent
    Assert.assertEquals(key, event.key)
  }

  @Test
  fun `test several operations`() {
    val mapFile = tempDirectory.createDir().resolve("map")

    val key1 = "b52"
    val key2 = "b53"
    val value = ByteArraySequence(byteArrayOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()))

    fillMap(mapFile) {
      put(key1, value)
      put(key2, value)
      appendData(key2, AppendablePersistentMap.ValueDataAppender { out -> out.write(value.internalBuffer) })
      remove(key1)
    }

    val events = readWal(mapFile)
    Assert.assertEquals(4, events.size)

    Assert.assertEquals(WalEvent.PutEvent(key1, value), events[0])
    Assert.assertEquals(WalEvent.PutEvent(key2, value), events[1])
    Assert.assertEquals(WalEvent.AppendEvent<String, ByteArraySequence>(key2, value.internalBuffer), events[2])
    Assert.assertEquals(WalEvent.RemoveEvent<String, ByteArraySequence>(key1), events[3])
  }

  @Test
  fun `test restore persistent map`() {
    val tempDir = tempDirectory.createDir()
    val mapFile = tempDir.resolve("map")

    val key1 = "b52"
    val key2 = "b53"
    val value = ByteArraySequence(byteArrayOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()))

    fillMap(mapFile) {
      put(key1, value)
      put(key2, value)
      appendData(key2, AppendablePersistentMap.ValueDataAppender { out -> out.write(value.internalBuffer) })
      remove(key1)
    }

    restorePersistentMapFromWal(findWalFile(mapFile),
                                tempDir.resolve("restored-map"),
                                EnumeratorStringDescriptor.INSTANCE,
                                ByteSequenceDataExternalizer.INSTANCE).use { map ->
      Assert.assertNull(map.get(key1))
      val expectedValue = ByteArraySequence(byteArrayOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte(), 1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()))
      Assert.assertEquals(expectedValue, map.get(key2))
    }
  }

  @Test
  fun `test restore hash map`() {
    val tempDir = tempDirectory.createDir()
    val mapFile = tempDir.resolve("map")

    val key1 = "b52"
    val key2 = "b53"
    val value = ByteArraySequence(byteArrayOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()))

    fillMap(mapFile) {
      put(key1, value)
      put(key2, value)
      appendData(key2, AppendablePersistentMap.ValueDataAppender { out -> out.write(value.internalBuffer) })
      remove(key1)
    }

    val restoredMap = restoreHashMapFromWal(findWalFile(mapFile),
                                            EnumeratorStringDescriptor.INSTANCE,
                                            ByteSequenceDataExternalizer.INSTANCE)
    Assert.assertEquals(1, restoredMap.keys.size)

    val expectedValue = ByteArraySequence(byteArrayOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte(), 1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()))
    Assert.assertEquals(expectedValue, restoredMap.get(key2))
  }

  @Test
  fun `test wal reopen`() {
    val tempDir = tempDirectory.createDir()
    val mapFile = tempDir.resolve("map")

    val key1 = "xxx"
    val key2 = "yyy"
    val key3 = "zzz"
    val key4 = "ttt"

    val value1 = ByteArraySequence(byteArrayOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()))
    val value2 = ByteArraySequence(byteArrayOf(5.toByte(), 6.toByte(), 7.toByte(), 8.toByte()))
    val value3 = ByteArraySequence(byteArrayOf(9.toByte(), 10.toByte(), 11.toByte(), 12.toByte()))
    val walFile = findWalFile(mapFile)

    fillMap(mapFile) {
      put(key1, value1)
      put(key2, value2)
      put(key3, value1)
      remove(key2)
      appendData(key3, AppendablePersistentMap.ValueDataAppender { it.write(value2.internalBuffer) })
    }
    fillMap(mapFile) {
      appendData(key2, AppendablePersistentMap.ValueDataAppender { it.write(value3.internalBuffer) })
      put(key4, value1)
      remove(key4)
      remove(key4)
      remove(key4)
    }

    val restoredMap = restoreHashMapFromWal(walFile,
                                            EnumeratorStringDescriptor.INSTANCE,
                                            ByteSequenceDataExternalizer.INSTANCE)
    Assert.assertEquals(setOf(key1, key2, key3), restoredMap.keys)
    Assert.assertEquals(value1, restoredMap[key1])
    Assert.assertEquals(value3, restoredMap[key2])
    Assert.assertEquals(ByteArraySequence(value1.internalBuffer + value2.internalBuffer), restoredMap[key3])
  }

  @Test
  fun `test wal compaction`() {
    val tempDir = tempDirectory.createDir()
    val mapFile = tempDir.resolve("map")

    val key1 = "xxx"
    val key2 = "yyy"
    val key3 = "zzz"
    val key4 = "ttt"

    val value1 = ByteArraySequence(byteArrayOf(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()))
    val value2 = ByteArraySequence(byteArrayOf(5.toByte(), 6.toByte(), 7.toByte(), 8.toByte()))
    val value3 = ByteArraySequence(byteArrayOf(9.toByte(), 10.toByte(), 11.toByte(), 12.toByte()))
    val walFile = findWalFile(mapFile)

    fillMap(mapFile) {
      put(key1, value1)
      put(key2, value2)
      put(key3, value1)
      remove(key2)
      appendData(key3, AppendablePersistentMap.ValueDataAppender { it.write(value2.internalBuffer) })
      appendData(key2, AppendablePersistentMap.ValueDataAppender { it.write(value3.internalBuffer) })
      put(key4, value1)
      remove(key4)
      remove(key4)
      remove(key4)
    }
    val beforeCompactionSize = walFile.fileSize()
    fillMap(mapFile) {}
    val afterCompactionSize = walFile.fileSize()
    Assert.assertTrue("before compaction size = $beforeCompactionSize, " +
                      "after compaction size = $afterCompactionSize",
                      beforeCompactionSize > afterCompactionSize)


    val restoredMap = restoreHashMapFromWal(walFile,
                                            EnumeratorStringDescriptor.INSTANCE,
                                            ByteSequenceDataExternalizer.INSTANCE)
    Assert.assertEquals(setOf(key1, key2, key3), restoredMap.keys)
    Assert.assertEquals(value1, restoredMap[key1])
    Assert.assertEquals(value3, restoredMap[key2])
    Assert.assertEquals(ByteArraySequence(value1.internalBuffer + value2.internalBuffer), restoredMap[key3])
  }

  private fun fillMap(mapFile: Path, operator: PersistentHashMap<String, ByteArraySequence>.() -> Unit) {
    PersistentMapBuilder
      .newBuilder(mapFile, EnumeratorStringDescriptor.INSTANCE, ByteSequenceDataExternalizer.INSTANCE)
      .withWal(true)
      .build().use { it.operator() }
  }

  private fun readWal(mapFile: Path): List<WalEvent<String, ByteArraySequence>> =
    PersistentMapWalPlayer(EnumeratorStringDescriptor.INSTANCE,
                           ByteSequenceDataExternalizer.INSTANCE,
                           findWalFile(mapFile)).use {
      return@use it.readWal().toList()
  }

  private fun findWalFile(mapFile: Path) = mapFile.resolveSibling(mapFile.fileName.toString() + ".wal")
}