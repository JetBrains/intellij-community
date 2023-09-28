// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.log.io.EntryArrayStorage.EntryExternalizer
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.*
import kotlin.io.path.*
import kotlin.math.min

@ApiStatus.Experimental
@ApiStatus.Internal
class AutoSizeAdjustingBlockEntryArrayStorage<Entry>(
  blockStoragePath: Path,
  internal val desiredBlockSize: Long,
  internal val maxEntriesPerBlock: Int,
  internal val blockSplitLoadFactor: Float = 0.75f,
  internal val entryExternalizer: EntryExternalizer<Entry>,
  maxOpenedBlocks: Int = 8,
) : EntryArrayStorage<Entry, AutoSizeAdjustingBlockEntryArrayStorage<Entry>.State> {
  init {
    check(desiredBlockSize > 0)
    check(maxEntriesPerBlock > 0)
  }

  protected val blocksDir: Path = blockStoragePath
  protected val blocksFileChannelCache: FileChannelCache = FileChannelCache(maxOpenedBlocks, EnumSet.of(READ, WRITE, CREATE))

  init {
    FileUtil.ensureExists(blocksDir.toFile())
  }

  override fun close() {
    blocksFileChannelCache.close()
  }

  override fun clearObsoleteFiles(currentState: State): EntryArrayStorage.StorageSpaceConsumptionStatistics {
    var obsoleteFilesRemoved = 0
    var filesInUse = 0
    var spaceFreed = 0L
    var spaceInUse = 0L
    val idsToKeep = currentState.blocks.map { it.descriptor.blockId }.toHashSet()
    blocksFileChannelCache.close()
    blocksDir.forEachDirectoryEntry("*" + BlockDescriptor.BLOCK_SUFFIX) { blockPath ->
      val id = try {
        blockPath.name.removeSuffix(BlockDescriptor.BLOCK_SUFFIX).toInt(16)
      }
      catch (e: Throwable) {
        LOG.warn("alien block file: ${blockPath.absolute()}")
        return@forEachDirectoryEntry
      }
      if (!idsToKeep.contains(id)) {
        try {
          val size = blockPath.fileSize()
          blockPath.deleteExisting()
          obsoleteFilesRemoved++
          spaceFreed += size
        }
        catch (e: Throwable) {
          LOG.error("failed to clear obsolete block file: ${blockPath.absolute()}")
        }
      }
      else {
        filesInUse++
        spaceInUse += blockPath.fileSize()
      }
    }
    return EntryArrayStorage.StorageSpaceConsumptionStatistics(obsoleteFilesRemoved, spaceFreed, filesInUse, spaceInUse)
  }

  override fun emptyState(): State {
    return State(0, 0, ArrayList())
  }

  override val stateExternalizer: EntryExternalizer<State> get() = StateExternalizer(this)

  override fun performUpdate(
    fromState: State,
    newSize: Int,
    updatedEntryIds: Set<Int>,
    getUpdatedEntry: (Int) -> Entry,
    checkCancelled: () -> Unit
  ): State {
    val stateBuilder = StateBuilder(
      newSize,
      fromState.lastAllocatedBlockId,
      ArrayList(fromState.blocks.map { it.descriptor }),
      fromState,
      updatedEntryIds,
      getUpdatedEntry
    )
    stateBuilder.adjustBlocksToMatchSize().also { checkCancelled() }
    stateBuilder.applySizeChanges().also { checkCancelled() }
    // at this point information about blocks is valid except for block size/entry count policy
    stateBuilder.splitTooLargeBlocks().also { checkCancelled() }
    stateBuilder.mergeSmallBlocks().also { checkCancelled() }
    // success, write new blocks and make a new state
    return stateBuilder.finalizeState(checkCancelled)
  }

  private inner class StateBuilder(
    val size: Int,
    var lastAllocatedBlockId: Int,
    val blocks: ArrayList<BlockDescriptor>,
    val fromState: State,
    val updatedEntryIds: Set<Int>,
    val getUpdatedEntry: (Int) -> Entry
  ) {
    private fun issueBlock(entryIdBegin: Int, entryIdEnd: Int, blockSizeBytes: Long) =
      BlockDescriptor(lastAllocatedBlockId + 1, entryIdBegin, entryIdEnd, blockSizeBytes).also {
        lastAllocatedBlockId += 1
      }

    fun adjustBlocksToMatchSize() {
      if (size < fromState.size) {
        while (blocks.isNotEmpty() && size < blocks.last().entryIdEnd) {
          val lastBlock = blocks.last()
          if (size <= lastBlock.entryIdBegin) {
            blocks.removeLast()
          }
          else {
            assert(size > 0)
            val newBlockSize = fromState.findResponsibleBlock(size - 1).getEntryOffset(size)
            blocks[blocks.size - 1] = issueBlock(lastBlock.entryIdBegin, size, newBlockSize)
          }
        }
      }
      else if (size > fromState.size) { // put all new elements into one last block
        var newBlockSize = 0L
        if (entryExternalizer is EntryArrayStorage.ConstSizeEntryExternalizer) {
          newBlockSize = (size - fromState.size) * entryExternalizer.entrySize
        }
        else {
          for (entryId in fromState.size until size) {
            newBlockSize += entryExternalizer.getEntrySize(
              getUpdatedEntry(entryId) ?: throw IllegalArgumentException("updatedEntries does not contain update for new entry $entryId")
            )
          }
        }
        blocks.add(issueBlock(fromState.size, size, newBlockSize))
      }
    }

    private lateinit var updatedEntryNewSize: Map<Int, Long> // set only if entryExternalizer is non-const size
    fun applySizeChanges() {
      val affectedExistingBlocks = hashSetOf<Int>()
      val existingBlocksNewSizes = hashMapOf<Int, Long>()
      blocks.forEach {
        existingBlocksNewSizes[it.blockId] = it.blockSizeBytes
      }
      if (entryExternalizer is EntryArrayStorage.ConstSizeEntryExternalizer) {
        updatedEntryIds.forEach { entryId ->
          if (entryId < fromState.size) {
            val blockId = findResponsibleBlock(entryId).blockId
            affectedExistingBlocks.add(blockId)
          }
        }
      }
      else {
        updatedEntryNewSize = updatedEntryIds.associateWith { entryId ->
          val newEntrySize = entryExternalizer.getEntrySize(getUpdatedEntry(entryId))
          if (entryId < fromState.size) {
            val oldEntrySize = fromState.getEntrySize(entryId)
            val blockId = findResponsibleBlock(entryId).blockId
            affectedExistingBlocks.add(blockId)
            existingBlocksNewSizes[blockId] = existingBlocksNewSizes[blockId]!! + newEntrySize - oldEntrySize
          }
          newEntrySize
        }
      }

      for (i in 0 until blocks.size) {
        val desc = blocks[i]
        if (desc.blockId in affectedExistingBlocks) {
          blocks[i] = issueBlock(desc.entryIdBegin, desc.entryIdEnd, existingBlocksNewSizes[desc.blockId]!!)
        }
      }
    }

    private fun buildVirtualIndex(blockDescriptor: BlockDescriptor): BlockIndex {
      if (entryExternalizer is EntryArrayStorage.ConstSizeEntryExternalizer) {
        return entryExternalizer.buildIndex(blockDescriptor)
      }
      val offsets = Array<Long>(blockDescriptor.entryCount + 1) { 0 }
      var currentOffset = 0L
      for (entryId in blockDescriptor.entryRange) {
        offsets[entryId - blockDescriptor.entryIdBegin] = currentOffset
        currentOffset += updatedEntryNewSize[entryId] ?: fromState.getEntrySize(entryId)
      }
      offsets[blockDescriptor.entryCount] = currentOffset
      return OffsetsIndex(offsets, blockDescriptor)
    }

    fun splitTooLargeBlocks() {
      val newBlocks = arrayListOf<BlockDescriptor>()
      blocks.forEach { currentBlock ->
        if (currentBlock.blockSizeBytes > desiredBlockSize || currentBlock.entryCount > maxEntriesPerBlock) {
          val index = buildVirtualIndex(currentBlock)
          val splitResult = arrayListOf<BlockDescriptor>()
          var entryIdBegin = currentBlock.entryIdBegin
          var splitPoint = currentBlock.entryIdBegin + 1 // splitPoint is a new entryIdEnd
          val stepInit = min(currentBlock.entryCount.takeHighestOneBit(), maxEntriesPerBlock.takeHighestOneBit())
          while (splitPoint < currentBlock.entryIdEnd) {
            var step = stepInit
            while (step > 0) {
              val candidate = splitPoint + step
              if (candidate <= currentBlock.entryIdEnd && candidate - entryIdBegin <= maxEntriesPerBlock * blockSplitLoadFactor &&
                  index.getEntryOffset(candidate) - index.getEntryOffset(entryIdBegin) <= desiredBlockSize * blockSplitLoadFactor) {
                splitPoint = candidate
              }
              step /= 2
            }
            val blockSize = index.getEntryOffset(splitPoint) - index.getEntryOffset(entryIdBegin)
            splitResult.add(issueBlock(entryIdBegin, splitPoint, blockSize))
            entryIdBegin = splitPoint
            splitPoint += 1
          }
          if (entryIdBegin < currentBlock.entryIdEnd) { // last one didn't fit
            val blockSize = index.getEntryOffset(currentBlock.entryIdEnd) - index.getEntryOffset(entryIdBegin)
            splitResult.add(issueBlock(entryIdBegin, currentBlock.entryIdEnd, blockSize))
          }
          newBlocks.addAll(splitResult)
        }
        else {
          newBlocks.add(currentBlock)
        }
      }
      blocks.clear()
      blocks.addAll(newBlocks)
    }

    fun mergeSmallBlocks() {
      // merge small blocks
      val newBlocks = arrayListOf<BlockDescriptor>()
      newBlocks.clear()
      blocks.forEach {
        if (newBlocks.isNotEmpty() &&
            newBlocks.last().entryCount + it.entryCount <= maxEntriesPerBlock * blockSplitLoadFactor &&
            newBlocks.last().blockSizeBytes + it.blockSizeBytes <= desiredBlockSize * blockSplitLoadFactor
        ) {
          val last = newBlocks.removeLast()
          check(last.entryIdEnd == it.entryIdBegin)
          newBlocks.add(issueBlock(last.entryIdBegin, it.entryIdEnd, last.blockSizeBytes + it.blockSizeBytes))
        }
        else {
          newBlocks.add(it)
        }
      }
      blocks.clear()
      blocks.addAll(newBlocks)
    }

    fun finalizeState(checkCancelled: () -> Unit): State {
      val newState = State(
        size,
        lastAllocatedBlockId,
        ArrayList(blocks.map {
          if (it.blockId <= fromState.lastAllocatedBlockId) fromState.getBlockById(it.blockId)
          else Block(it)
        })
      )
      newState.blocks.forEach { block ->
        checkCancelled()
        if (block.descriptor.blockId > fromState.lastAllocatedBlockId) {
          if (entryExternalizer is EntryArrayStorage.ConstSizeEntryExternalizer &&
              block.descriptor.entryIdBegin < fromState.size &&
              block.descriptor.entryRange == fromState.findResponsibleBlock(block.descriptor.entryIdBegin).descriptor.entryRange) {
            val previousBlock = fromState.findResponsibleBlock(block.descriptor.entryIdBegin)
            previousBlock.descriptor.blockPath.copyTo(block.descriptor.blockPath, overwrite = true)
            block.rewriteModifiedConstSizeEntries(updatedEntryIds, getUpdatedEntry)
          }
          else {
            block.writeBlock { entryId -> if (entryId in updatedEntryIds) getUpdatedEntry(entryId) else fromState.getEntry(entryId) }
          }
        }
      }
      return newState
    }

    fun findResponsibleBlock(entryId: Int): BlockDescriptor {
      val index = blocks.binarySearch {
        if (entryId in it.entryRange) return@binarySearch 0
        if (entryId < it.entryIdBegin) return@binarySearch 1
        assert(entryId >= it.entryIdEnd)
        return@binarySearch -1
      }
      check(index >= 0) { "entry $entryId is not contained in $this" }
      return blocks[index]
    }
  }

  inner class State internal constructor(
    override val size: Int,
    internal val lastAllocatedBlockId: Int,
    internal val blocks: ArrayList<Block>,
  ) : EntryArrayStorage.PersistentState<Entry> {
    init {
      check(
        size == 0 || (size > 0 && blocks.isNotEmpty() && blocks.first().descriptor.entryIdBegin == 0 &&
                      blocks.zipWithNext().all { it.first.descriptor.entryIdEnd == it.second.descriptor.entryIdBegin } &&
                      blocks.last().descriptor.entryIdEnd == size &&
                      blocks.all { it.descriptor.entryCount > 0 })
      ) { "$this is incorrect" }
    }

    @Volatile
    private var lastRequestedBlock: Block? = null

    internal fun findResponsibleBlock(entryId: Int): Block {
      lastRequestedBlock?.let {
        if (entryId in it.descriptor.entryRange) return it
      }
      val index = blocks.binarySearch {
        if (entryId in it.descriptor.entryRange) return@binarySearch 0
        if (entryId < it.descriptor.entryIdBegin) return@binarySearch 1
        assert(entryId >= it.descriptor.entryIdEnd)
        return@binarySearch -1
      }
      check(index >= 0) { "entry $entryId is not contained in $this" }
      return blocks[index].also {
        lastRequestedBlock = blocks[index]
      }
    }

    internal fun getBlockById(blockId: Int): Block =
      blocks.find { it.descriptor.blockId == blockId }
      ?: throw IllegalArgumentException("block $blockId is not found in $this")

    override fun getEntry(entryId: Int): Entry {
      val block = findResponsibleBlock(entryId)
      val offset = block.getEntryOffset(entryId)
      return block.fileChannel.access {
        entryExternalizer.deserialize(asStorageIO().offsetView(offset))
      }
    }

    override fun getEntrySize(entryId: Int): Long = findResponsibleBlock(entryId).getEntrySize(entryId)

    override fun toString(): String {
      return "State(size=$size, blocks=${blocks.map { it.descriptor }})"
    }
  }

  internal fun BlockDescriptor.getCachedFileChannel(): FileChannelCache.CachedFileChannel =
    blocksFileChannelCache.get(blockPath)

  internal val BlockDescriptor.blockPath: Path get() = blocksDir / blockFileName

  internal inner class Block(
    val descriptor: BlockDescriptor,
    precomputedIndex: BlockIndex? = null
  ) : BlockIndex {
    val fileChannel: FileChannelCache.CachedFileChannel by lazy { descriptor.getCachedFileChannel() }
    private val index: BlockIndex by lazy { precomputedIndex ?: buildBlockIndex() }

    private fun buildBlockIndex(): BlockIndex {
      if (entryExternalizer is EntryArrayStorage.ConstSizeEntryExternalizer) {
        return entryExternalizer.buildIndex(descriptor)
      }
      return fileChannel.access {
        val io = this.asStorageIO()
        val offsets = Array(descriptor.entryCount + 1) { 0L }
        var currentOffset = 0L
        for (localId in 0 until descriptor.entryCount) {
          offsets[localId] = currentOffset
          val entrySize = entryExternalizer.getEntrySize(io.offsetView(currentOffset))
          check(entrySize > 0) { "entry can't be of 0 size" }
          currentOffset += entrySize
        }
        check(currentOffset == descriptor.blockSizeBytes) {
          "block size mismatch: expected ${descriptor.blockSizeBytes}, but during index construction got $currentOffset"
        }
        offsets[descriptor.entryCount] = currentOffset
        OffsetsIndex(offsets, descriptor)
      }
    }

    internal fun writeBlock(getEntry: (entryId: Int) -> Entry) {
      fileChannel.access {
        val io = asStorageIO()
        var currentOffset = 0L
        for (entryId in descriptor.entryRange) {
          val entry = getEntry(entryId)
          entryExternalizer.serialize(io.offsetView(currentOffset), entry)
          currentOffset += entryExternalizer.getEntrySize(entry)
        }
        check(currentOffset == descriptor.blockSizeBytes) { "Failed to write block $descriptor: only $currentOffset bytes were written" }
        force(false)
      }
    }

    internal fun rewriteModifiedConstSizeEntries(updatedIds: Set<Int>, getUpdatedEntry: (Int) -> Entry) {
      fileChannel.access {
        entryExternalizer as EntryArrayStorage.ConstSizeEntryExternalizer
        val io = asStorageIO()
        if (updatedIds.size < descriptor.entryCount) {
          updatedIds.forEach { entryId ->
            if (entryId in descriptor.entryRange) {
              entryExternalizer.serialize(io.offsetView(getEntryOffset(entryId)), getUpdatedEntry(entryId))
            }
          }
        }
        else {
          for (entryId in descriptor.entryRange) {
            if (entryId in updatedIds) {
              entryExternalizer.serialize(io.offsetView(getEntryOffset(entryId)), getUpdatedEntry(entryId))
            }
          }
        }
        force(false)
      }
    }

    override fun getEntryOffset(entryId: Int): Long = index.getEntryOffset(entryId)
    override fun getEntrySize(entryId: Int): Long = index.getEntrySize(entryId)
  }

  companion object {
    internal val LOG = Logger.getInstance(AutoSizeAdjustingBlockEntryArrayStorage::class.java)

    internal data class BlockDescriptor(
      val blockId: Int,
      val entryIdBegin: Int,
      val entryIdEnd: Int, // exclusive
      val blockSizeBytes: Long
    ) {
      val blockFileName: String get() = blockId.toString(16) + BLOCK_SUFFIX
      val entryRange: IntRange get() = entryIdBegin until entryIdEnd
      val entryCount: Int get() = entryIdEnd - entryIdBegin

      companion object {
        const val BLOCK_SUFFIX = ".bin"
      }
    }

    internal object BlockDescriptorExternalizer : EntryArrayStorage.ConstSizeEntryExternalizer<BlockDescriptor> {
      override val entrySize: Long = 3L * Int.SIZE_BYTES + Long.SIZE_BYTES

      override fun deserialize(readBuffer: RandomAccessReadBuffer): BlockDescriptor {
        val bin = ByteArray(entrySize.toInt())
        readBuffer.read(0, bin)
        val buf = ByteBuffer.wrap(bin)
        val blockId = buf.getInt()
        val entryIdBegin = buf.getInt()
        val entryIdEnd = buf.getInt()
        val blockSizeBytes = buf.getLong()
        return BlockDescriptor(blockId, entryIdBegin, entryIdEnd, blockSizeBytes)
      }

      override fun serialize(writeBuffer: RandomAccessWriteBuffer,
                             entry: BlockDescriptor) {
        val bin = ByteArray(this@BlockDescriptorExternalizer.entrySize.toInt())
        val buf = ByteBuffer.wrap(bin)
        buf.putInt(entry.blockId)
        buf.putInt(entry.entryIdBegin)
        buf.putInt(entry.entryIdEnd)
        buf.putLong(entry.blockSizeBytes)
        writeBuffer.write(0, bin)
      }
    }

    internal class StateExternalizer<E>(
      private val owningStorage: AutoSizeAdjustingBlockEntryArrayStorage<E>,
    ) : EntryExternalizer<AutoSizeAdjustingBlockEntryArrayStorage<E>.State> {
      // size, lastAllocatedBlockId, blocksCount, blocks...
      override fun getEntrySize(entry: AutoSizeAdjustingBlockEntryArrayStorage<E>.State): Long =
        3L * Int.SIZE_BYTES + entry.blocks.size * BlockDescriptorExternalizer.entrySize

      override fun getEntrySize(readBuffer: RandomAccessReadBuffer): Long {
        val intBuf = ByteArray(Int.SIZE_BYTES)
        readBuffer.read(2 * Int.SIZE_BYTES.toLong(), intBuf)
        val blocksCount = ByteBuffer.wrap(intBuf).getInt()
        return 3L * Int.SIZE_BYTES + blocksCount * BlockDescriptorExternalizer.entrySize
      }

      override fun deserialize(readBuffer: RandomAccessReadBuffer): AutoSizeAdjustingBlockEntryArrayStorage<E>.State {
        val intArr = ByteArray(3 * Int.SIZE_BYTES)
        readBuffer.read(0L, intArr)
        val (size, lastAllocatedBlockId, blocksCount) = ByteBuffer.wrap(intArr).run { Triple(getInt(), getInt(), getInt()) }
        val dataView = readBuffer.offsetView(3L * Int.SIZE_BYTES)
        val blocks = ArrayList<AutoSizeAdjustingBlockEntryArrayStorage<E>.Block>(blocksCount)
        repeat(blocksCount) {
          val descriptor = BlockDescriptorExternalizer.deserialize(dataView.offsetView(it.toLong() * BlockDescriptorExternalizer.entrySize))
          blocks.add(owningStorage.Block(descriptor))
        }
        return owningStorage.State(size, lastAllocatedBlockId, blocks)
      }

      override fun serialize(writeBuffer: RandomAccessWriteBuffer,
                             entry: AutoSizeAdjustingBlockEntryArrayStorage<E>.State) {
        val intArr = ByteArray(3 * Int.SIZE_BYTES)
        ByteBuffer.wrap(intArr).putInt(entry.size).putInt(entry.lastAllocatedBlockId).putInt(entry.blocks.size)
        writeBuffer.write(0, intArr)
        val dataView = writeBuffer.offsetView(3L * Int.SIZE_BYTES)
        entry.blocks.forEachIndexed { index, block ->
          BlockDescriptorExternalizer.serialize(dataView.offsetView(index * BlockDescriptorExternalizer.entrySize), block.descriptor)
        }
      }
    }

    internal interface BlockIndex {
      /**
       * It is allowed to request offset of the entry that comes after last in the block. Such value is equal to the block size.
       */
      fun getEntryOffset(entryId: Int): Long
      fun getEntrySize(entryId: Int): Long
    }

    internal fun <E> EntryArrayStorage.ConstSizeEntryExternalizer<E>.buildIndex(descriptor: BlockDescriptor): BlockIndex = object : BlockIndex {
      override fun getEntryOffset(entryId: Int): Long {
        check(entryId in (descriptor.entryIdBegin..descriptor.entryIdEnd)) { // here range is inclusive
          "entry $entryId is outside block range ${descriptor.entryRange}"
        }
        return entrySize * (entryId - descriptor.entryIdBegin)
      }

      override fun getEntrySize(entryId: Int): Long = entrySize
    }

    internal class OffsetsIndex(val offsets: Array<Long>, val descriptor: BlockDescriptor) : BlockIndex {
      init {
        check(offsets.size == descriptor.entryCount + 1)
      }

      override fun getEntryOffset(entryId: Int): Long {
        check(entryId in (descriptor.entryIdBegin..descriptor.entryIdEnd)) { // here range is inclusive
          "entry $entryId is outside block range ${descriptor.entryRange}"
        }
        return offsets[entryId - descriptor.entryIdBegin]
      }

      override fun getEntrySize(entryId: Int): Long {
        check(entryId in descriptor.entryRange) { "entry $entryId is outside block range ${descriptor.entryRange}" }
        return getEntryOffset(entryId + 1) - getEntryOffset(entryId)
      }
    }
  }
}