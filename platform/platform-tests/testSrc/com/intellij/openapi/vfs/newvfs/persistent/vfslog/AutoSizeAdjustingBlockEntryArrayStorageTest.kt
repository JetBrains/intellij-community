// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.vfslog

import com.intellij.openapi.vfs.newvfs.persistent.log.io.*
import com.intellij.openapi.vfs.newvfs.persistent.log.io.EntryArrayStorage.ConstSizeEntryExternalizer
import com.intellij.util.io.ResilientFileChannel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.math.min
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
class AutoSizeAdjustingBlockEntryArrayStorageTest {
  private val printStats = false

  @Test
  fun `const size entry`(@TempDir tempDir: Path) {
    val valuesSeries = mutableListOf<ArrayList<Pair<Int, Int>>>()
    for (i in 1..10) {
      valuesSeries.add(ArrayList(List(i * 400) { it to it * 2 }))
      valuesSeries.add(ArrayList(List(i * 200) { if (it % 5 == 0) it to it * 3 else it to it * 2 }))
    }

    for (desiredBlockSize in listOf<Long>(10240, 102400, 1024000)) {
      for (maxEntriesPerBlock in listOf(250, 1000, 5000)) {
        if (printStats) println("blockSize $desiredBlockSize, maxEntries $maxEntriesPerBlock")
        val time = measureTime {
          runModifications(tempDir, desiredBlockSize, maxEntriesPerBlock, PairExternalizer, valuesSeries, printStats)
        }
        if (printStats) println("took $time")
      }
    }
  }

  @Test
  fun `dynamic size entry`(@TempDir tempDir: Path) {
    val valuesSeries = mutableListOf<ArrayList<String>>()
    for (i in 1..10) {
      valuesSeries.add(ArrayList(List(i * 400) { "abc".repeat(it) }))
      valuesSeries.add(ArrayList(List(i * 200) { if (it % 5 == 0) "dfg".repeat(it / 2) else "abc".repeat(it) }))
    }


    for (desiredBlockSize in listOf<Long>(512 * 1024, 2 * 1024 * 1024, 8 * 1024 * 1024)) {
      for (maxEntriesPerBlock in listOf(250, 1000, 4000)) {
        if (printStats) println("blockSize $desiredBlockSize, maxEntries $maxEntriesPerBlock")
        val time = measureTime {
          runModifications(tempDir, desiredBlockSize, maxEntriesPerBlock, StringExternalizer, valuesSeries, printStats)
        }
        if (printStats) println("took $time")
      }
    }
  }

  private fun <E> runModifications(
    tempDir: Path,
    desiredBlockSize: Long, maxEntriesPerBlock: Int, entryExternalizer: EntryArrayStorage.EntryExternalizer<E>,
    valueSeries: List<ArrayList<E>>,
    printCompactionStats: Boolean = true
  ) {
    val blocksDir = tempDir / "blocks"
    val statePath = tempDir / "state.data"

    val storage = AutoSizeAdjustingBlockEntryArrayStorage(
      blocksDir,
      desiredBlockSize,
      maxEntriesPerBlock,
      entryExternalizer = entryExternalizer
    )

    storage.persistState(statePath, storage.emptyState())
    var state = storage.loadState(statePath)
    check(state.size == 0)
    var previousValues: ArrayList<E> = ArrayList()

    for (values in valueSeries) {
      val updateMap = mutableMapOf<Int, E>()
      for (i in 0 until min(previousValues.size, values.size)) {
        if (previousValues[i] != values[i]) updateMap[i] = values[i]
      }
      for (i in previousValues.size until values.size) {
        updateMap[i] = values[i]
      }
      run {
        val newState = storage.performUpdate(state, values.size, updateMap)
        storage.persistState(statePath, newState)
        val compaction = storage.clearObsoleteFiles(newState)
        if (printCompactionStats) println(compaction)
      }
      state = storage.loadState(statePath)
      check(state.size == values.size)
      for (i in 0 until values.size) {
        check(state.getEntry(i) == values[i])
      }
      previousValues = values
    }
    if (printCompactionStats) println()
  }

  private fun <E> AutoSizeAdjustingBlockEntryArrayStorage<E>.persistState(statePath: Path,
                                                                          state: AutoSizeAdjustingBlockEntryArrayStorage<E>.State) {
    val updateFile = statePath.resolveSibling(statePath.name + ".upd")
    ResilientFileChannel(updateFile, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE)).use {
      stateExternalizer.serialize(it.asStorageIO(), state)
    }
    updateFile.moveTo(statePath, overwrite = true)
  }

  private fun <E> AutoSizeAdjustingBlockEntryArrayStorage<E>.loadState(statePath: Path): AutoSizeAdjustingBlockEntryArrayStorage<E>.State {
    return with(stateExternalizer) {
      ResilientFileChannel(statePath, EnumSet.of(StandardOpenOption.READ)).use {
        deserialize(it.asStorageIO())
      }
    }
  }

  companion object {
    object PairExternalizer : ConstSizeEntryExternalizer<Pair<Int, Int>> {
      override val entrySize: Long = 8

      override fun deserialize(readBuffer: RandomAccessReadBuffer): Pair<Int, Int> {
        val buf = ByteArray(8)
        readBuffer.read(0, buf)
        return ByteBuffer.wrap(buf).run { getInt() to getInt() }
      }

      override fun serialize(writeBuffer: RandomAccessWriteBuffer, entry: Pair<Int, Int>) {
        val buf = ByteArray(8)
        ByteBuffer.wrap(buf).run { this.putInt(entry.first); this.putInt(entry.second) }
        writeBuffer.write(0, buf)
      }
    }

    object StringExternalizer : EntryArrayStorage.EntryExternalizer<String> {
      override fun getEntrySize(entry: String): Long = 4L + entry.encodeToByteArray().size

      override fun getEntrySize(readBuffer: RandomAccessReadBuffer): Long {
        val buf = ByteArray(4)
        readBuffer.read(0, buf)
        return ByteBuffer.wrap(buf).run { 4L + getInt() }
      }

      override fun deserialize(readBuffer: RandomAccessReadBuffer): String {
        val buf = ByteArray(4)
        readBuffer.read(0, buf)
        val size = ByteBuffer.wrap(buf).getInt()
        val data = ByteArray(size)
        readBuffer.read(4, data)
        return data.decodeToString()
      }

      override fun serialize(writeBuffer: RandomAccessWriteBuffer, entry: String) {
        val data = entry.encodeToByteArray()
        val buf = ByteArray(4)
        ByteBuffer.wrap(buf).putInt(data.size)
        writeBuffer.write(0, buf)
        writeBuffer.write(4, data)
      }
    }
  }
}