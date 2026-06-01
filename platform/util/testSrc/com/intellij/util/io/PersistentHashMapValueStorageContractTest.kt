// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import java.io.DataOutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.Random

/**
 * Exercises the value-storage append/read contract directly, without `PersistentHashMap` hiding value-chain details.
 */
internal class PersistentHashMapValueStorageContractTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `randomized append read and reopen contract for value chains`() {
    testConfigurations(hasNoChunks = false).forEach { config ->
      SEEDS.forEach { seed ->
        ValueStorageScenario(tempDir.resolve("${config.name}-seed-$seed.values"), config, seed).run()
      }
    }
  }

  @Test
  fun `randomized append read and reopen contract without value chains`() {
    testConfigurations(hasNoChunks = true).forEach { config ->
      SEEDS.forEach { seed ->
        ValueStorageScenario(tempDir.resolve("${config.name}-seed-$seed.values"), config, seed).run()
      }
    }
  }

  @Test
  fun `append after writable reopen does not overwrite existing records`() {
    allTestConfigurations().forEach { config ->
      val storageFile = tempDir.resolve("${config.name}-append-after-reopen.values")
      val firstPayload = byteArrayOf(1, 2, 3, 4)
      val secondPayload = ByteArray(4096) { index -> (index % 251).toByte() }

      val firstAddress = withStorage(storageFile, config, readOnly = false) { storage ->
        storage.appendPayload(firstPayload, previousTailAddress = 0).also {
          storage.flush()
        }
      }

      val secondAddress = withStorage(storageFile, config, readOnly = false) { storage ->
        storage.assertRecord(firstAddress, firstPayload, 1, "${config.name}: first record must survive writable reopen before next append")

        storage.appendPayload(secondPayload, previousTailAddress = 0).also { secondAddress ->
          assertTrue(
            secondAddress > firstAddress,
            "${config.name}: append after reopen must allocate after the already persisted first record"
          )
          storage.flush()
        }
      }

      withStorage(storageFile, config, readOnly = true) { storage ->
        storage.assertRecord(firstAddress, firstPayload, 1, "${config.name}: first record must survive append after reopen")
        storage.assertRecord(secondAddress, secondPayload, 1, "${config.name}: second record must be readable after read-only reopen")
      }
    }
  }

  @Test
  fun `read-only storage rejects appends but keeps existing records readable`() {
    allTestConfigurations().forEach { config ->
      val storageFile = tempDir.resolve("${config.name}-readonly-append.values")
      val payload = byteArrayOf(42, 43, 44)

      val address = withStorage(storageFile, config, readOnly = false) { storage ->
        storage.appendPayload(payload, previousTailAddress = 0).also {
          storage.flush()
        }
      }

      withStorage(storageFile, config, readOnly = true) { storage ->
        assertTrue(storage.isReadOnly, "${config.name}: precondition, storage must be read-only")
        storage.assertRecord(address, payload, 1, "${config.name}: read-only storage must read records written before reopen")

        assertThrows<AssertionError>("${config.name}: read-only storage must reject append attempts") {
          storage.appendPayload(byteArrayOf(45), previousTailAddress = 0)
        }
      }
    }
  }

  @Test
  fun `compaction mode reader keeps value chains readable and rejects appends`() {
    testConfigurations(hasNoChunks = false).forEach { config ->
      val storageFile = tempDir.resolve("${config.name}-compaction-mode-reader.values")
      val firstChunk = ByteArray(1024) { index -> index.toByte() }
      val secondChunk = ByteArray(2048) { index -> (index * 3).toByte() }
      val expectedBytes = firstChunk + secondChunk

      withStorage(storageFile, config, readOnly = false) { storage ->
        val firstAddress = storage.appendPayload(firstChunk, previousTailAddress = 0)
        val secondAddress = storage.appendPayload(secondChunk, previousTailAddress = firstAddress)
        storage.flush()

        storage.switchToCompactionModeForTest()

        storage.assertRecord(secondAddress, expectedBytes, 2, "${config.name}: compaction-mode reader must read existing chunk chains")
        assertThrows<AssertionError>("${config.name}: compaction mode must reject appends") {
          storage.appendPayload(byteArrayOf(1), previousTailAddress = 0)
        }
      }
    }
  }

  @Test
  fun `compactChunks rewrites value chain into a single equivalent chunk`() {
    testConfigurations(hasNoChunks = false).forEach { config ->
      val storageFile = tempDir.resolve("${config.name}-compact-chunks.values")
      val firstChunk = "first-chunk".toByteArray()
      val secondChunk = "second-chunk".toByteArray()
      val expectedBytes = firstChunk + secondChunk

      withStorage(storageFile, config, readOnly = false) { storage ->
        val firstAddress = storage.appendPayload(firstChunk, previousTailAddress = 0)
        val secondAddress = storage.appendPayload(secondChunk, previousTailAddress = firstAddress)
        val oldReadResult = storage.readBytesForTest(secondAddress)

        assertEquals(2, oldReadResult.chunksCount, "${config.name}: precondition, value must be stored as two chunks")
        assertArrayEquals(expectedBytes, oldReadResult.buffer, "${config.name}: precondition, old value chain must contain both chunks")

        val compactedAddress = storage.compactChunksForTest(oldReadResult)

        assertTrue(compactedAddress > secondAddress, "${config.name}: compacted chunk must be appended after the old chain")
        assertNotEquals(secondAddress, compactedAddress, "${config.name}: compaction must return a new value address")
        storage.assertRecord(compactedAddress, expectedBytes, 1, "${config.name}: compacted value must be a single equivalent chunk")
        storage.assertRecord(secondAddress,
                             expectedBytes,
                             2,
                             "${config.name}: old value chain remains readable until map metadata is updated")
      }
    }
  }

  @Test
  fun `value storage receives builder StorageLockContext`() {
    val lockContext = StorageLockContext(false)

    withPersistentMap(tempDir.resolve("builder-context-map"), lockContext) { map ->
      val valueStorage = PersistentMapImpl.unwrap(map).valueStorage
      assertSame(lockContext, valueStorage.storageLockContext, "Value storage must use the StorageLockContext from PersistentMapBuilder")
    }
  }

  @Test
  fun `value storage receives thread-local StorageLockContext`() {
    val lockContext = StorageLockContext(false)
    val previousContext = PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.get()
    PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.set(lockContext)
    try {
      withPersistentMap(tempDir.resolve("thread-local-context-map"), lockContext = null) { map ->
        val valueStorage = PersistentMapImpl.unwrap(map).valueStorage
        assertSame(lockContext, valueStorage.storageLockContext, "Value storage must use the resolved thread-local StorageLockContext")
      }
    }
    finally {
      if (previousContext == null) {
        PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.remove()
      }
      else {
        PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.set(previousContext)
      }
    }
  }

  @Test
  fun `value storage reads and writes through StorageLockContext ChannelsAccessor`() {
    val storageFile = tempDir.resolve("channel-accessor-backed-file-accessor.values")
    val firstPayload = accessorFirstPayload()
    val secondPayload = accessorSecondPayload()
    val channelsAccessor = RecordingChannelsAccessor()
    val lockContext = StorageLockContext(false, channelsAccessor.readOnlyAccessor, channelsAccessor.writableAccessor)
    val config = TestConfig(name = "plain", hasNoChunks = false, useCompression = false)

    val firstAddress = withStorage(storageFile, config, readOnly = false, lockContext = lockContext) { storage ->
      storage.appendPayload(firstPayload, previousTailAddress = 0).also {
        storage.flush()
      }
    }
    assertEquals(
      1,
      channelsAccessor.forceOperations(storageFile).size,
      "Initial append must preserve the single historical first-header force on the main value file"
    )
    channelsAccessor.clearChannelOperations()

    val secondAddress = withStorage(storageFile, config, readOnly = false, lockContext = lockContext) { storage ->
      storage.assertRecord(firstAddress, firstPayload, 1, "Value written through the custom accessor must be readable after reopen")
      storage.appendPayload(secondPayload, previousTailAddress = firstAddress).also {
        storage.flush()
      }
    }

    withStorage(storageFile, config, readOnly = true, lockContext = lockContext) { storage ->
      storage.assertRecord(secondAddress,
                           firstPayload + secondPayload,
                           2,
                           "Value storage must read value chains through the custom accessor")
    }
    assertEquals(
      0,
      channelsAccessor.forceOperations(storageFile).size,
      "Main value file reads and logical flushes after the header workaround must not call FileChannel.force()"
    )

    assertEquals(0, channelsAccessor.activeChannels, "Recording accessor must not keep channels open after operations")
    assertEquals(0, channelsAccessor.idempotentOperations, "Adapter append protocol must not use retryable idempotent operations")
    assertTrue(channelsAccessor.operations.any { !it.readOnly }, "Adapter must use the supplied accessor for write operations")
    assertTrue(channelsAccessor.operations.any { it.readOnly }, "Adapter must use the supplied accessor for read operations")
    assertTrue(
      channelsAccessor.channelOperations.any { it.path == storageFile && it.name == "read" && it.readOnly == false },
      "Writable value storage must read value bytes through the writable accessor channel"
    )
    assertTrue(
      channelsAccessor.channelOperations.any { it.path == storageFile && it.name == "read" && it.readOnly == true },
      "Read-only value storage must read value bytes through the read-only accessor channel"
    )
    assertTrue(channelsAccessor.closedPaths.contains(storageFile), "Adapter disposal must close channels through the supplied accessor")
  }

  @Test
  fun `compressed value storage side files use StorageLockContext ChannelsAccessor`() {
    val storageFile = tempDir.resolve("compressed-channel-accessor-side-files.values")
    val chunkLengthFile = storageFile.resolveSibling("${storageFile.fileName}.s")
    val incompleteChunkFile = storageFile.resolveSibling("${storageFile.fileName}.at")
    val payload = ByteArray(COMPRESSED_ACCESSOR_PAYLOAD_SIZE) { index -> (index % 251).toByte() }
    val channelsAccessor = RecordingChannelsAccessor()
    val lockContext = StorageLockContext(false, channelsAccessor.readOnlyAccessor, channelsAccessor.writableAccessor)
    val config = TestConfig(name = "compressed", hasNoChunks = false, useCompression = true)

    val address = withStorage(storageFile, config, readOnly = false, lockContext = lockContext) { storage ->
      storage.appendPayload(payload, previousTailAddress = 0).also {
        storage.flush()
      }
    }
    assertEquals(
      1,
      channelsAccessor.forceOperations(storageFile).size,
      "Compressed storage must preserve only the historical first-header force on the main value file"
    )
    assertEquals(
      0,
      channelsAccessor.forceOperations(chunkLengthFile).size,
      "Compressed chunk-length appender flush must not force the chunk-length side-file"
    )
    assertEquals(
      0,
      channelsAccessor.forceOperations(incompleteChunkFile).size,
      "Compressed incomplete tail write must not force the incomplete chunk side-file"
    )
    channelsAccessor.clearChannelOperations()

    withStorage(storageFile, config, readOnly = true, lockContext = lockContext) { storage ->
      storage.assertRecord(address, payload, 1, "Compressed value storage must read side files through the custom accessor")
    }
    assertEquals(
      0,
      channelsAccessor.forceOperations(storageFile).size,
      "Compressed chunk reads must flush the main appender without forcing the main value file"
    )
    assertEquals(
      0,
      channelsAccessor.forceOperations(chunkLengthFile).size,
      "Compressed chunk length reads must not force the chunk-length side-file"
    )

    assertTrue(
      channelsAccessor.operations.any { it.path == chunkLengthFile && !it.readOnly },
      "Compressed storage must append chunk lengths through the supplied accessor"
    )
    assertTrue(
      channelsAccessor.operations.any { it.path == chunkLengthFile && it.readOnly },
      "Compressed storage must read chunk length table through the supplied accessor"
    )
    assertTrue(
      channelsAccessor.operations.any { it.path == incompleteChunkFile && !it.readOnly },
      "Compressed storage must write incomplete chunk file through the supplied accessor"
    )
    assertTrue(
      channelsAccessor.operations.any { it.path == incompleteChunkFile && it.readOnly },
      "Compressed storage must read incomplete chunk file through the supplied accessor"
    )
    assertTrue(channelsAccessor.closedPaths.contains(chunkLengthFile),
               "Compressed storage must close chunk length channels through the accessor")
    assertTrue(channelsAccessor.closedPaths.contains(incompleteChunkFile),
               "Compressed storage must close incomplete chunk channels through the accessor")
    assertEquals(0, channelsAccessor.idempotentOperations, "Compressed side-file access must not use retryable idempotent operations")
  }

  @Test
  fun `compressed incomplete chunk clear does not force when side file becomes empty`() {
    val storageFile = tempDir.resolve("compressed-unforced-incomplete-tail-clear.values")
    val incompleteChunkFile = storageFile.resolveSibling("${storageFile.fileName}.at")
    val channelsAccessor = RecordingChannelsAccessor()
    val lockContext = StorageLockContext(false, channelsAccessor.readOnlyAccessor, channelsAccessor.writableAccessor)
    val config = TestConfig(name = "compressed-hasNoChunks-true", hasNoChunks = true, useCompression = true)

    withStorage(storageFile, config, readOnly = false, lockContext = lockContext) { storage ->
      storage.appendPayload(byteArrayOf(1), previousTailAddress = 0)
      storage.flush()

      val payloadLength = payloadLengthToFillCompressedPage(storage.size, config.hasNoChunks)
      storage.appendPayload(ByteArray(payloadLength) { index -> (index % 251).toByte() }, previousTailAddress = 0)
      storage.flush()

      assertEquals(
        CompressedAppendableFile.PAGE_LENGTH.toLong(),
        storage.size,
        "precondition: second append must finish the compressed page and clear the incomplete tail"
      )
    }

    val incompleteTailOperations = channelsAccessor.channelOperations.filter { it.path == incompleteChunkFile }
    val truncateIndex = incompleteTailOperations.indexOfFirst { it.name == "truncate" && it.size == 0L }
    assertTrue(truncateIndex >= 0, "Compressed storage must truncate the incomplete chunk side-file when a page is completed")
    assertFalse(
      incompleteTailOperations.drop(truncateIndex + 1).any { it.name == "force" },
      "Incomplete chunk side-file clear must not force after truncating the side-file to 0"
    )
  }

  /** Keeps the generated operation stream reproducible and reports enough context when a generated case fails. */
  private class ValueStorageScenario(
    private val storageFile: Path,
    private val config: TestConfig,
    private val seed: Long,
  ) {
    private val random = Random(seed)
    private val records = ArrayList<Record>()
    private var storage = openStorage(readOnly = false)
    private var payloadNo = 0

    fun run() {
      try {
        repeat(OPERATION_COUNT) { operationNo ->
          try {
            performOperation(operationNo)
          }
          catch (error: Throwable) {
            throw AssertionError("${caseName(operationNo)}: generated operation failed", error)
          }
        }

        flushAndReopen("final writable reopen")
        reopenReadOnlyAndVerify("final read-only reopen")
      }
      finally {
        storage.dispose()
      }
    }

    private fun performOperation(operationNo: Int) {
      val operation = if (records.isEmpty()) 0 else random.nextInt(100)
      when {
        operation < 40 -> appendNewRecord(operationNo)
        operation < 65 && !config.hasNoChunks -> appendChunk(operationNo)
        operation < 85 -> readBackRandomRecord(operationNo)
        operation < 95 -> flushAndReopen("operation $operationNo")
        else -> reopenReadOnlyAndVerify("operation $operationNo")
      }
    }

    private fun appendNewRecord(operationNo: Int) {
      val payload = nextPayload()
      val tailAddress = storage.appendBytes(payload, 0, payload.size, 0)
      val record = Record(tailAddress = tailAddress, expectedBytes = payload, expectedChunksCount = 1)
      records.add(record)
      assertRecord(records.lastIndex, record, "${caseName(operationNo)} after appendNew")
    }

    private fun appendChunk(operationNo: Int) {
      val recordIndex = random.nextInt(records.size)
      val previous = records[recordIndex]
      val payload = nextPayload()
      val tailAddress = storage.appendBytes(payload, 0, payload.size, previous.tailAddress)

      val updated = Record(
        tailAddress = tailAddress,
        expectedBytes = previous.expectedBytes + payload,
        expectedChunksCount = previous.expectedChunksCount + 1,
      )
      records[recordIndex] = updated
      assertRecord(recordIndex, updated, "${caseName(operationNo)} after appendChunk")
    }

    private fun readBackRandomRecord(operationNo: Int) {
      val recordIndex = random.nextInt(records.size)
      assertRecord(recordIndex, records[recordIndex], "${caseName(operationNo)} readBack")
    }

    private fun flushAndReopen(checkpoint: String) {
      //TODO RC: do we need separate .flush() before .dispose()?
      //         shouldn't .dispose() do flush() inside?
      storage.flush()
      storage.dispose()
      storage = openStorage(readOnly = false)
      assertAllRecords("${caseName()} after $checkpoint")
    }

    private fun reopenReadOnlyAndVerify(checkpoint: String) {
      //TODO RC: do we need separate .flush() before .dispose()?
      //         shouldn't .dispose() do flush() inside?
      storage.flush()
      storage.dispose()

      storage = openStorage(readOnly = true)
      assertTrue(storage.isReadOnly, "${caseName()} $checkpoint: storage must be opened in read-only mode")
      assertAllRecords("${caseName()} during $checkpoint")

      storage.dispose()
      storage = openStorage(readOnly = false)
      assertAllRecords("${caseName()} after returning from $checkpoint")
    }

    private fun assertAllRecords(message: String) {
      records.forEachIndexed { recordIndex, record ->
        assertRecord(recordIndex, record, message)
      }
    }

    private fun assertRecord(recordIndex: Int, record: Record, message: String) {
      val result = storage.readBytesForTest(record.tailAddress)
      assertArrayEquals(
        record.expectedBytes,
        result.buffer,
        "$message: record[$recordIndex] bytes must match bytes appended to its chunk chain"
      )
      assertEquals(
        record.expectedChunksCount,
        result.chunksCount,
        "$message: record[$recordIndex] chunk count must match number of appended chunks"
      )
    }

    private fun openStorage(readOnly: Boolean): PersistentHashMapValueStorage {
      return PersistentHashMapValueStorage.create(storageFile, config.options(readOnly))
    }

    private fun nextPayload(): ByteArray {
      val size = if (payloadNo < INTERESTING_PAYLOAD_SIZES.size) {
        INTERESTING_PAYLOAD_SIZES[payloadNo]
      }
      else {
        when (random.nextInt(10)) {
          0 -> 0
          1 -> 1
          2 -> 1024 + random.nextInt(128)
          3 -> 4096 + random.nextInt(256)
          4 -> 32768 + random.nextInt(128)
          else -> random.nextInt(512)
        }
      }
      payloadNo++

      val bytes = ByteArray(size)
      random.nextBytes(bytes)
      return bytes
    }

    private fun caseName(operationNo: Int? = null): String {
      return buildString {
        append("[")
        append(config.name)
        append(", seed=")
        append(seed)
        if (operationNo != null) {
          append(", operation=")
          append(operationNo)
        }
        append("]")
      }
    }
  }

  /** Holds the current tail address and model value for one generated logical record. */
  private class Record(
    val tailAddress: Long,
    val expectedBytes: ByteArray,
    val expectedChunksCount: Int,
  )

  /** Captures storage creation flags that materially affect value-storage layout and append protocol. */
  private class TestConfig(
    val name: String,
    val hasNoChunks: Boolean,
    val useCompression: Boolean,
  ) {
    fun options(readOnly: Boolean): PersistentHashMapValueStorage.CreationTimeOptions {
      return PersistentHashMapValueStorage.CreationTimeOptions(
        readOnly,
        /*compactChunksWithValueDeserialization = */false,
        hasNoChunks,
        useCompression,
      )
    }
  }

  /** Read result mirror for the package-private `PersistentHashMapValueStorage.ReadResult`. */
  private class ReadResultForTest(
    val rawResult: Any,
    val buffer: ByteArray,
    val chunksCount: Int,
  )

  private class RecordingChannelsAccessor {
    val readOnlyAccessor: ChannelsAccessor = Accessor(/*readOnly = */true)
    val writableAccessor: ChannelsAccessor = Accessor(/*readOnly = */false)

    val operations = ArrayList<Operation>()
    val channelOperations = ArrayList<ChannelOperation>()
    val closedPaths = ArrayList<Path>()
    var activeChannels = 0
      private set
    var idempotentOperations = 0
      private set

    private inner class Accessor(private val readOnly: Boolean) : ChannelsAccessor {
      override fun isReadOnly(): Boolean = readOnly

      override fun <T> executeOp(path: Path, operation: ChannelsAccessor.FileChannelOperation<T>): T {
        operations.add(Operation(path, readOnly))
        if (!readOnly) {
          Files.createDirectories(path.parent)
        }

        val options = if (readOnly) arrayOf(READ) else arrayOf(READ, WRITE, CREATE)
        activeChannels++
        try {
          FileChannel.open(path, *options).use { channel ->
            return operation.execute(RecordingFileChannel(path, readOnly, channel, channelOperations))
          }
        }
        finally {
          activeChannels--
        }
      }

      override fun <T> executeIdempotentOp(
        path: Path,
        operation: FileChannelInterruptsRetryer.FileChannelIdempotentOperation<T>,
      ): T {
        idempotentOperations++
        return executeOp(path) { channel -> operation.execute(channel) }
      }

      override fun closeChannel(path: Path) {
        closedPaths.add(path)
      }
    }

    data class Operation(val path: Path, val readOnly: Boolean)

    data class ChannelOperation(val path: Path, val name: String, val size: Long? = null, val readOnly: Boolean? = null)

    /** Starts a new assertion window while preserving accessor lifecycle counters. */
    fun clearChannelOperations() {
      channelOperations.clear()
    }

    /** Selects physical force calls for one file so tests can keep main-file and side-file expectations separate. */
    fun forceOperations(path: Path): List<ChannelOperation> {
      return channelOperations.filter { it.path == path && it.name == "force" }
    }

    private class RecordingFileChannel(
      private val path: Path,
      private val readOnly: Boolean,
      private val delegate: FileChannel,
      private val operations: MutableList<ChannelOperation>,
    ) : FileChannel() {
      override fun read(dst: ByteBuffer): Int {
        operations.add(ChannelOperation(path, "read", readOnly = readOnly))
        return delegate.read(dst)
      }

      override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long = delegate.read(dsts, offset, length)

      override fun read(dst: ByteBuffer, position: Long): Int {
        operations.add(ChannelOperation(path, "read", readOnly = readOnly))
        return delegate.read(dst, position)
      }

      override fun write(src: ByteBuffer): Int = delegate.write(src)

      override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long = delegate.write(srcs, offset, length)

      override fun write(src: ByteBuffer, position: Long): Int = delegate.write(src, position)

      override fun position(): Long = delegate.position()

      override fun position(newPosition: Long): FileChannel {
        delegate.position(newPosition)
        return this
      }

      override fun size(): Long = delegate.size()

      override fun truncate(size: Long): FileChannel {
        operations.add(ChannelOperation(path, "truncate", size))
        delegate.truncate(size)
        return this
      }

      override fun force(metaData: Boolean) {
        operations.add(ChannelOperation(path, "force"))
        delegate.force(metaData)
      }

      override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long {
        return delegate.transferTo(position, count, target)
      }

      override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long {
        return delegate.transferFrom(src, position, count)
      }

      override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer {
        return delegate.map(mode, position, size)
      }

      override fun lock(position: Long, size: Long, shared: Boolean): FileLock {
        return delegate.lock(position, size, shared)
      }

      override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? {
        return delegate.tryLock(position, size, shared)
      }

      override fun implCloseChannel() {
        delegate.close()
      }
    }
  }

  private companion object {
    private const val OPERATION_COUNT = 120
    private const val ACCESSOR_SECOND_PAYLOAD_SIZE = 4096
    private const val COMPRESSED_ACCESSOR_PAYLOAD_SIZE = 32768 + 128

    private val SEEDS = listOf(1L, 2L, 5L, 20260528L)

    private val INTERESTING_PAYLOAD_SIZES = intArrayOf(
      0,
      1,
      2,
      15,
      127,
      1023,
      1024,
      1025,
      4095,
      4096,
      4097,
      32767,
      32768,
      32769,
    )

    private val readBytesMethod: Method = PersistentHashMapValueStorage::class.java
      .getDeclaredMethod("readBytes", java.lang.Long.TYPE)
      .apply { isAccessible = true }

    private val readResultClass: Class<*> =
      PersistentHashMapValueStorage::class.java.declaredClasses.single { it.simpleName == "ReadResult" }

    private val compactChunksMethod: Method = PersistentHashMapValueStorage::class.java
      .getDeclaredMethod("compactChunks", AppendablePersistentMap.ValueDataAppender::class.java, readResultClass)
      .apply { isAccessible = true }

    private val switchToCompactionModeMethod: Method = PersistentHashMapValueStorage::class.java
      .getDeclaredMethod("switchToCompactionMode")
      .apply { isAccessible = true }

    private fun testConfigurations(hasNoChunks: Boolean): List<TestConfig> {
      return listOf(
        TestConfig(name = "plain-hasNoChunks-$hasNoChunks", hasNoChunks = hasNoChunks, useCompression = false),
        TestConfig(name = "compressed-hasNoChunks-$hasNoChunks", hasNoChunks = hasNoChunks, useCompression = true),
      )
    }

    private fun allTestConfigurations(): List<TestConfig> {
      return testConfigurations(hasNoChunks = false) + testConfigurations(hasNoChunks = true)
    }

    private fun accessorFirstPayload(): ByteArray {
      return byteArrayOf(1, 2, 3, 4)
    }

    private fun accessorSecondPayload(): ByteArray {
      return ByteArray(ACCESSOR_SECOND_PAYLOAD_SIZE) { index -> (index % 251).toByte() }
    }

    private fun payloadLengthToFillCompressedPage(currentSize: Long, hasNoChunks: Boolean): Int {
      val bytesUntilPageEnd = CompressedAppendableFile.PAGE_LENGTH - (currentSize % CompressedAppendableFile.PAGE_LENGTH).toInt()
      for (payloadLength in 0..bytesUntilPageEnd) {
        if (valueStorageRecordSize(payloadLength, hasNoChunks) == bytesUntilPageEnd) {
          return payloadLength
        }
      }
      error("Unable to choose payload size to fill compressed page from currentSize=$currentSize")
    }

    private fun valueStorageRecordSize(payloadLength: Int, hasNoChunks: Boolean): Int {
      val stream = BufferExposingByteArrayOutputStream()
      DataOutputStream(stream).use { output ->
        DataInputOutputUtil.writeINT(output, payloadLength)
        if (!hasNoChunks) {
          DataInputOutputUtil.writeLONG(output, 0)
        }
      }
      return stream.size() + payloadLength
    }

    private inline fun <T> withStorage(
      storageFile: Path,
      config: TestConfig,
      readOnly: Boolean,
      lockContext: StorageLockContext? = null,
      action: (PersistentHashMapValueStorage) -> T,
    ): T {
      val storage = if (lockContext == null) {
        PersistentHashMapValueStorage.create(storageFile, config.options(readOnly))
      }
      else {
        PersistentHashMapValueStorage.create(storageFile, config.options(readOnly), lockContext)
      }
      try {
        return action(storage)
      }
      finally {
        storage.dispose()
      }
    }

    private inline fun <T> withPersistentMap(
      storageFile: Path,
      lockContext: StorageLockContext?,
      action: (PersistentHashMap<String, String>) -> T,
    ): T {
      val map = PersistentMapBuilder.newBuilder(
        storageFile,
        EnumeratorStringDescriptor.INSTANCE,
        EnumeratorStringDescriptor.INSTANCE,
      ).withStorageLockContext(lockContext).build()
      return map.use(action)
    }

    private fun PersistentHashMapValueStorage.appendPayload(payload: ByteArray, previousTailAddress: Long): Long {
      return appendBytes(payload, 0, payload.size, previousTailAddress)
    }

    private fun PersistentHashMapValueStorage.assertRecord(
      tailAddress: Long,
      expectedBytes: ByteArray,
      expectedChunksCount: Int,
      message: String,
    ) {
      val result = readBytesForTest(tailAddress)
      assertArrayEquals(expectedBytes, result.buffer, "$message: bytes must match bytes appended to the value chain")
      assertEquals(expectedChunksCount, result.chunksCount, "$message: chunk count must match the expected value layout")
    }

    private fun PersistentHashMapValueStorage.readBytesForTest(tailAddress: Long): ReadResultForTest {
      val result = try {
        readBytesMethod.invoke(this, tailAddress)
      }
      catch (error: InvocationTargetException) {
        throw error.targetException
      }

      // readBytes() and ReadResult are package-private in the production module; keep that API closed for now.
      val resultClass = result.javaClass
      val bufferField = resultClass.getDeclaredField("buffer").apply { isAccessible = true }
      val chunksCountField = resultClass.getDeclaredField("chunksCount").apply { isAccessible = true }
      return ReadResultForTest(
        rawResult = result,
        buffer = bufferField.get(result) as ByteArray,
        chunksCount = chunksCountField.getInt(result),
      )
    }

    private fun PersistentHashMapValueStorage.compactChunksForTest(readResult: ReadResultForTest): Long {
      val appender = AppendablePersistentMap.ValueDataAppender { out -> out.write(readResult.buffer) }
      return try {
        compactChunksMethod.invoke(this, appender, readResult.rawResult) as Long
      }
      catch (error: InvocationTargetException) {
        throw error.targetException
      }
    }

    private fun PersistentHashMapValueStorage.switchToCompactionModeForTest() {
      try {
        switchToCompactionModeMethod.invoke(this)
      }
      catch (error: InvocationTargetException) {
        throw error.targetException
      }
    }
  }
}
