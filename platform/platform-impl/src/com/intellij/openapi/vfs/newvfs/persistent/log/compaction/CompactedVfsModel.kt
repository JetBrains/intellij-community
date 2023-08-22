// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.compaction

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.TraverseDirection
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.PayloadSource
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.AttributesOperation.Companion.fileId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.ContentsOperation.Companion.contentRecordId
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation.RecordsOperation.Companion.fileId
import com.intellij.openapi.vfs.newvfs.persistent.log.io.*
import com.intellij.openapi.vfs.newvfs.persistent.log.io.EntryArrayStorage.StorageSpaceConsumptionStatistics.Companion.plus
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.get
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence.Companion.isFormed
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence.Companion.restoreContent
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract.isRelevantAndModifies
import com.intellij.openapi.vfs.newvfs.persistent.log.util.ULongPacker
import com.intellij.util.io.ResilientFileChannel
import com.intellij.util.io.UnsyncByteArrayOutputStream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterOutputStream
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.math.max


class CompactedVfsModel(
  private val storagePath: Path,
) : AutoCloseable {
  private val stateFile get() = storagePath / "state"
  private val filesDir get() = storagePath / "files"
  private val contentsDir get() = storagePath / "contents"
  private val attributesDir get() = storagePath / "attributes"

  init {
    FileUtil.ensureExists(storagePath.toFile())
  }

  private val filesStorage = AutoSizeAdjustingBlockEntryArrayStorage<FileModel>(
    filesDir, FILES_PER_BLOCK * FileModel.HEADER_SIZE_BYTES, FILES_PER_BLOCK,
    entryExternalizer = FileModelExternalizer, maxOpenedBlocks = 4,
  )
  /** contents are deflated */
  private val contentsStorage = AutoSizeAdjustingBlockEntryArrayStorage<ByteArray>(
    contentsDir, CONTENTS_BLOCK_SIZE, CONTENTS_PER_BLOCK,
    entryExternalizer = ByteArrayExternalizer, maxOpenedBlocks = 4
  )
  private val attributesStorage = AutoSizeAdjustingBlockEntryArrayStorage<List<Pair<EnumeratedFileAttribute, ByteArray>>>(
    attributesDir, ATTRIBUTES_BLOCK_SIZE, ATTRIBUTES_PER_BLOCK,
    entryExternalizer = AttributeDataMapExternalizer, maxOpenedBlocks = 4
  )

  override fun close() {
    filesStorage.close()
    contentsStorage.close()
    attributesStorage.close()
  }

  /**
   * @param operationLogPosition position, up to which compacted model is calculated
   * @param payloadStoragePosition used as a hint that every payload up to that position can be dropped from the PayloadStorageImpl
   *          (every operation starting from [operationLogPosition] has its payloadRef pointing to at least [payloadStoragePosition]).
   */
  data class CompactionPosition(
    val operationLogPosition: Long,
    val payloadStoragePosition: Long
  )

  private data class CachedReadAttribute(val fileId: Int, val attributes: List<Pair<EnumeratedFileAttribute, ByteArray>>)

  /**
   * @property contentsState stores deflated content
   */
  inner class CompactedVfsState internal constructor(
    val position: CompactionPosition,
    internal val filesState: AutoSizeAdjustingBlockEntryArrayStorage<FileModel>.State,
    internal val contentsState: AutoSizeAdjustingBlockEntryArrayStorage<ByteArray>.State,
    internal val attributesState: AutoSizeAdjustingBlockEntryArrayStorage<List<Pair<EnumeratedFileAttribute, ByteArray>>>.State
  ) {
    val payloadSourceDeclaration: PersistentSet<PayloadSource> =
      persistentSetOf(PayloadSource.CompactedVfsAttributes)

    val contentsSize: Int get() = contentsState.size

    fun inflateContent(contentRecordId: Int): ByteArray = contentsState.getEntry(contentRecordId).let { deflatedContent ->
      val result = UnsyncByteArrayOutputStream()
      InflaterOutputStream(result).use {
        it.write(deflatedContent)
      }
      result.toByteArray()
    }

    @Volatile
    private var lastReadAttributes: CachedReadAttribute? = null

    val payloadReader: PayloadReader = { ref ->
      when (ref.source) {
        PayloadSource.CompactedVfsAttributes -> {
          val (fileId, attrIndex) = unpackAttributesReference(ref)
          if (attributesState.size <= fileId) {
            State.NotAvailable("file $fileId does not exist")
          }
          else {
            val attributes = lastReadAttributes?.let {
              if (it.fileId == fileId) it.attributes
              else null
            } ?: attributesState.getEntry(fileId).also { newAttrs ->
              lastReadAttributes = CachedReadAttribute(fileId, newAttrs)
            }
            attributes[attrIndex].second.let(State::Ready)
          }
        }
        else -> State.NotAvailable("$ref does not belong to Compacted Vfs")
      }
    }

    internal fun packAttributeReference(fileId: Int, attributeIndex: Int): PayloadRef =
      PayloadRef(with(ULongPacker) {
        0.toULong().setInt(fileId, 0, 32).setInt(attributeIndex, 32, 24)
      }.toLong(), PayloadSource.CompactedVfsAttributes)

    private fun unpackAttributesReference(ref: PayloadRef): Pair<Int, Int> {
      require(ref.source == PayloadSource.CompactedVfsAttributes)
      with(ULongPacker) {
        ref.offset.toULong().run {
          return getInt(0, 32) to getInt(32, 24)
        }
      }
    }
  }

  private val stateExternalizer = object : EntryArrayStorage.EntryExternalizer<CompactedVfsState> {
    override fun getEntrySize(entry: CompactedVfsState): Long =
      8 + 8 + 4 + 4 + 4 +
      filesStorage.stateExternalizer.getEntrySize(entry.filesState) +
      contentsStorage.stateExternalizer.getEntrySize(entry.contentsState) +
      attributesStorage.stateExternalizer.getEntrySize(entry.attributesState)

    override fun RandomAccessReadBuffer.getEntrySize(): Long {
      val subStateSizes = ByteArray(4 + 4 + 4)
      read(8 + 8, subStateSizes)
      val (filesStateSize, contentsStateSize, attributesStateSize) = ByteBuffer.wrap(subStateSizes).run {
        Triple(getInt(), getInt(), getInt())
      }
      return 8L + 8 + 4 + 4 + 4 + filesStateSize + contentsStateSize + attributesStateSize
    }

    override fun RandomAccessReadBuffer.deserialize(): CompactedVfsState {
      val headerArr = ByteArray(8 + 8 + 4 + 4 + 4)
      read(0, headerArr)
      val buf = ByteBuffer.wrap(headerArr)
      val operationLogPosition = buf.getLong()
      val payloadStoragePosition = buf.getLong()
      val filesStateSize = buf.getInt()
      val contentsStateSize = buf.getInt()
      // val attributesStateSize = buf.getInt() // not used
      offsetView(8 + 8 + 4 + 4 + 4).run {
        val filesState = with(filesStorage.stateExternalizer) {
          deserialize()
        }
        offsetView(filesStateSize.toLong()).run {
          val contentsState = with(contentsStorage.stateExternalizer) {
            deserialize()
          }
          offsetView(contentsStateSize.toLong()).run {
            val attributesState = with(attributesStorage.stateExternalizer) {
              deserialize()
            }
            return CompactedVfsState(CompactionPosition(operationLogPosition, payloadStoragePosition), filesState, contentsState, attributesState)
          }
        }
      }
    }

    override fun RandomAccessWriteBuffer.serialize(entry: CompactedVfsState) {
      val headerArr = ByteArray(8 + 8 + 4 + 4 + 4)
      val filesStateSize = filesStorage.stateExternalizer.getEntrySize(entry.filesState).toInt()
      val contentsStateSize = contentsStorage.stateExternalizer.getEntrySize(entry.contentsState).toInt()
      val attributesStateSize = attributesStorage.stateExternalizer.getEntrySize(entry.attributesState).toInt()
      ByteBuffer.wrap(headerArr)
        .putLong(entry.position.operationLogPosition)
        .putLong(entry.position.payloadStoragePosition)
        .putInt(filesStateSize)
        .putInt(contentsStateSize)
        .putInt(attributesStateSize)
      write(0, headerArr)
      offsetView(8 + 8 + 4 + 4 + 4).run {
        with(filesStorage.stateExternalizer) {
          serialize(entry.filesState)
        }
        offsetView(filesStateSize.toLong()).run {
          with(contentsStorage.stateExternalizer) {
            serialize(entry.contentsState)
          }
          offsetView(contentsStateSize.toLong()).run {
            with(attributesStorage.stateExternalizer) {
              serialize(entry.attributesState)
            }
          }
        }
      }
    }
  }

  fun loadOrInitState(initialCompactionPosition: () -> CompactionPosition): CompactedVfsState {
    if (!stateFile.exists()) {
      return CompactedVfsState(initialCompactionPosition(), filesStorage.emptyState(), contentsStorage.emptyState(),
                               attributesStorage.emptyState())
    }
    ResilientFileChannel(stateFile, READ).use {
      with(stateExternalizer) {
        return it.asStorageIO().deserialize()
      }
    }
  }

  private fun persistState(newState: CompactedVfsState) {
    val updateFile = stateFile.resolveSibling(stateFile.name + ".upd")
    ResilientFileChannel(updateFile, WRITE, CREATE).use {
      with(stateExternalizer) {
        it.asStorageIO().serialize(newState)
      }
    }
    updateFile.moveTo(stateFile, overwrite = true)
  }

  fun compactUpTo(context: VfsLogCompactionContext, currentState: CompactedVfsState, position: CompactionPosition): CompactedVfsState {
    require(position.operationLogPosition >= currentState.position.operationLogPosition) {
      "provided position is before already compacted position: provided $position vs current ${currentState.position}"
    }
    require(position.operationLogPosition <= context.end().getPosition()) {
      "provided position ${position} is after currently available operations range ${context.end().getPosition()}"
    }
    check(context.begin().getPosition() <= currentState.position.operationLogPosition) {
      "can't compact from position ${currentState.position.operationLogPosition}: " +
      "log start position is greater (=${context.begin().getPosition()})"
    }
    require(position.payloadStoragePosition >= currentState.position.payloadStoragePosition) {
      "provided payload storage position is less than current compacted payload storage position: " +
      "provided $position vs current ${currentState.position}"
    }
    if (position == currentState.position) return currentState

    val checkCancelled = {
      if (context.cancellationWasRequested()) {
        throw ProcessCanceledException(RuntimeException("VfsLog compaction was cancelled by an external request"))
      }
    }

    class AttributesUpdate(var clear: Boolean = false, val entries: MutableMap<EnumeratedFileAttribute, PayloadRef> = mutableMapOf())

    val fileUpdates = mutableMapOf<Int, FileModel>()
    val contentUpdates = mutableMapOf<Int, VfsChronicle.ContentRestorationSequenceBuilder>()
    val attributeUpdates = mutableMapOf<Int, AttributesUpdate>()
    var newFilesSize: Int = currentState.filesState.size
    var newContentsSize: Int = currentState.contentsState.size

    val iterator = context.constrainedIterator(
      currentState.position.operationLogPosition, currentState.position.operationLogPosition, position.operationLogPosition
    )
    VfsChronicle.traverseOperationsLog(iterator, TraverseDirection.PLAY, VfsOperationTagsMask.ALL) { op ->
      checkCancelled()
      if (op is VfsOperation.RecordsOperation) {
        val upd = op.fileId?.let { fileId ->
          fileUpdates.getOrPut(fileId) {
            if (fileId >= currentState.filesState.size) FileModel()
            else currentState.filesState.getEntry(fileId)
          }
        }
        if (upd != null) {
          VfsModificationContract.nameId.isRelevantAndModifies(op) { upd.nameId = it }
          VfsModificationContract.parentId.isRelevantAndModifies(op) { upd.parentId = it }
          VfsModificationContract.length.isRelevantAndModifies(op) { upd.length = it }
          VfsModificationContract.timestamp.isRelevantAndModifies(op) { upd.timestamp = it }
          VfsModificationContract.flags.isRelevantAndModifies(op) { upd.flags = it }
          VfsModificationContract.contentRecordId.isRelevantAndModifies(op) { upd.contentRecordId = it }
          VfsModificationContract.attributeRecordId.isRelevantAndModifies(op) { upd.attributesRecordId = it }
          if (op is VfsOperation.RecordsOperation.AllocateRecord) {
            newFilesSize = max(newFilesSize, op.result.value + 1)
          }
        }
      }
      if (op is VfsOperation.ContentsOperation.AcquireNewRecord) {
        newContentsSize = max(newContentsSize, op.result.value + 1)
      }
      VfsModificationContract.content.isRelevantAndModifies(op) {
        op as VfsOperation.ContentsOperation
        op.contentRecordId?.let { contentId ->
          contentUpdates.compute(contentId) { _, builder ->
            if (it is VfsModificationContract.ContentOperation.Modify) {
              val result = builder ?: VfsChronicle.ContentRestorationSequenceBuilder()
              result.appendModification(it)
              return@compute result
            }
            return@compute VfsChronicle.ContentRestorationSequenceBuilder().also { result ->
              result.setInitial(it as VfsModificationContract.ContentOperation.Set)
            }
          }
        }
      }
      VfsModificationContract.attributeData.isRelevantAndModifies(op) {
        val fileId = (op as? VfsOperation.AttributesOperation)?.fileId ?: (op as? VfsOperation.RecordsOperation)?.fileId
        if (fileId != null) {
          if (it.enumeratedAttributeFilter == null) {
            assert(it.data == null) // deletion
            attributeUpdates.compute(fileId) { _, upd ->
              if (upd == null) return@compute AttributesUpdate(true)
              upd.clear = true
              upd.entries.clear()
              upd
            }
          }
          else {
            assert(it.data != null)
            attributeUpdates.compute(fileId) { _, upd ->
              if (upd == null) return@compute AttributesUpdate(false, mutableMapOf(it.enumeratedAttributeFilter to it.data!!))
              if (it.enumeratedAttributeFilter in upd.entries) {
                upd.entries.remove(it.enumeratedAttributeFilter)
              }
              upd.entries[it.enumeratedAttributeFilter] = it.data!!
              upd
            }
          }
        }
      }
    }
    check(iterator.getPosition() == position.operationLogPosition) {
      "compaction didn't manage to navigate to the provided position"
    }

    if (currentState.filesState.size == 0) {
      fileUpdates.putIfAbsent(0, FileModel())
      attributeUpdates.putIfAbsent(0, AttributesUpdate())
      // super root allocation happens before modification interception, so alloc -> 1 will not be found in the logs
      fileUpdates.putIfAbsent(1, FileModel())
    }
    if (currentState.contentsState.size == 0) {
      val emptyContent = VfsChronicle.ContentRestorationSequenceBuilder()
      emptyContent.setInitial(VfsModificationContract.ContentOperation.Set { _ -> ByteArray(0).let(State::Ready) })
      contentUpdates.putIfAbsent(0, emptyContent)
    }
    for (i in currentState.filesState.size until newFilesSize) {
      check(fileUpdates.contains(i) && attributeUpdates.contains(i))
    }
    for (i in currentState.contentsState.size until newContentsSize) {
      check(contentUpdates.containsKey(i) && contentUpdates[i]!!.isFormed)
    }

    val newFilesState =
      filesStorage.performUpdate(
        currentState.filesState,
        newFilesSize,
        fileUpdates,
        checkCancelled
      ).also {
        fileUpdates.clear()
      }
    val newAttributesState =
      attributesStorage.performUpdate(
        currentState.attributesState,
        newFilesSize,
        attributeUpdates.keys,
        { fileId ->
          val upd = attributeUpdates[fileId]!!
          val result: MutableMap<EnumeratedFileAttribute, ByteArray> =
            if (upd.clear || currentState.attributesState.size <= fileId) mutableMapOf()
            else currentState.attributesState.getEntry(fileId).toMap(mutableMapOf())
          val loadedAttrs = upd.entries.mapValues {
            check(it.value.source != PayloadSource.PayloadStorage || currentState.position.payloadStoragePosition <= it.value.offset) {
              "compaction failed: attribute update refers to a payload that was already compacted before: " +
              "current ${currentState.position}, attribute data ref ${it.value}"
            }
            context.payloadReader(it.value).get()
          }
          for ((attr, data) in loadedAttrs) {
            if (attr in result.keys) {
              result.remove(attr)
            }
            result[attr] = data
          }
          result.toList()
        },
        checkCancelled
      ).also {
        attributeUpdates.clear()
      }
    fun ByteArray.deflate(): ByteArray {
      val result = UnsyncByteArrayOutputStream()
      DeflaterOutputStream(result).use {
        it.write(this)
      }
      return result.toByteArray()
    }
    val newContentsState =
      contentsStorage.performUpdate(
        currentState.contentsState,
        newContentsSize,
        contentUpdates.keys,
        { contentId ->
          val seqBuilder = contentUpdates[contentId]!!
          if (!seqBuilder.isFormed) {
            seqBuilder.setInitial(
              VfsModificationContract.ContentOperation.Set { _ ->
                currentState.inflateContent(contentId).let(State::Ready)
              }
            )
          }
          seqBuilder.restoreContent(context.payloadReader).get().deflate()
        },
        checkCancelled
      ).also {
        contentUpdates.clear()
      }
    val newState = CompactedVfsState(position, newFilesState, newContentsState, newAttributesState)

    persistState(newState)

    val clearStats =
      filesStorage.clearObsoleteFiles(newFilesState) +
      attributesStorage.clearObsoleteFiles(newAttributesState) +
      contentsStorage.clearObsoleteFiles(newContentsState)

    LOG.info("compaction iteration has succeeded. Storage clean up statistics: $clearStats")

    return newState
  }


  companion object {
    private val LOG = Logger.getInstance(CompactedVfsModel::class.java)

    private const val FILES_PER_BLOCK = 250 * 1024

    private const val CONTENTS_BLOCK_SIZE = 8L * 1024 * 1024
    private const val CONTENTS_PER_BLOCK = 5 * 1024

    private const val ATTRIBUTES_BLOCK_SIZE = 16L * 1024 * 1024
    private const val ATTRIBUTES_PER_BLOCK = 1024 * 1024

    internal class FileModel(
      var nameId: Int = 0,
      var parentId: Int = 0,
      var length: Long = 0,
      var timestamp: Long = 0,
      var flags: Int = 0,
      var contentRecordId: Int = 0,
      var attributesRecordId: Int = 0
    ) {
      companion object {
        private const val NAME_ID_OFFSET = 0L
        private const val NAME_ID_BYTES = Int.SIZE_BYTES

        private const val PARENT_ID_OFFSET = NAME_ID_OFFSET + NAME_ID_BYTES
        private const val PARENT_ID_BYTES = Int.SIZE_BYTES

        private const val LENGTH_OFFSET = PARENT_ID_OFFSET + PARENT_ID_BYTES
        private const val LENGTH_BYTES = Long.SIZE_BYTES

        private const val TIMESTAMP_OFFSET = LENGTH_OFFSET + LENGTH_BYTES
        private const val TIMESTAMP_BYTES = Long.SIZE_BYTES

        private const val FLAGS_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_BYTES
        private const val FLAGS_BYTES = Int.SIZE_BYTES

        private const val CONTENT_RECORD_ID_OFFSET = FLAGS_OFFSET + FLAGS_BYTES
        private const val CONTENT_RECORD_ID_BYTES = Int.SIZE_BYTES

        private const val ATTRIBUTES_RECORD_ID_OFFSET = CONTENT_RECORD_ID_OFFSET + CONTENT_RECORD_ID_BYTES
        private const val ATTRIBUTES_RECORD_ID_BYTES = Int.SIZE_BYTES

        const val HEADER_SIZE_BYTES = ATTRIBUTES_RECORD_ID_OFFSET + ATTRIBUTES_RECORD_ID_BYTES // 36 bytes
      }
    }

    private object FileModelExternalizer : EntryArrayStorage.ConstSizeEntryExternalizer<FileModel> {
      override val entrySize: Long = FileModel.HEADER_SIZE_BYTES

      override fun RandomAccessReadBuffer.deserialize(): FileModel {
        val arr = ByteArray(FileModel.HEADER_SIZE_BYTES.toInt())
        read(0, arr)
        ByteBuffer.wrap(arr).run {
          return FileModel(
            getInt(), getInt(), getLong(), getLong(), getInt(), getInt(), getInt()
          )
        }
      }

      override fun RandomAccessWriteBuffer.serialize(entry: FileModel) {
        val arr = ByteArray(FileModel.HEADER_SIZE_BYTES.toInt())
        ByteBuffer.wrap(arr)
          .putInt(entry.nameId)
          .putInt(entry.parentId)
          .putLong(entry.length)
          .putLong(entry.timestamp)
          .putInt(entry.flags)
          .putInt(entry.contentRecordId)
          .putInt(entry.attributesRecordId)
        write(0, arr)
      }
    }

    object ByteArrayExternalizer : EntryArrayStorage.EntryExternalizer<ByteArray> {
      override fun getEntrySize(entry: ByteArray): Long = 4 + entry.size.toLong()

      override fun RandomAccessReadBuffer.getEntrySize(): Long {
        val intArr = ByteArray(4)
        read(0, intArr)
        val size = ByteBuffer.wrap(intArr).getInt()
        return 4 + size.toLong()
      }

      override fun RandomAccessReadBuffer.deserialize(): ByteArray {
        val intArr = ByteArray(4)
        read(0, intArr)
        val size = ByteBuffer.wrap(intArr).getInt()
        val data = ByteArray(size)
        read(4, data)
        return data
      }

      override fun RandomAccessWriteBuffer.serialize(entry: ByteArray) {
        val intArr = ByteArray(4)
        ByteBuffer.wrap(intArr).putInt(entry.size)
        write(0, intArr)
        write(4, entry)
      }
    }

    object AttributeDataMapExternalizer : EntryArrayStorage.EntryExternalizer<List<Pair<EnumeratedFileAttribute, ByteArray>>> {
      init {
        assert(EnumeratedFileAttribute.SIZE_BYTES == 8)
      }

      override fun getEntrySize(entry: List<Pair<EnumeratedFileAttribute, ByteArray>>): Long {
        var totalSize = 4 // entries count
        entry.forEach { (_, data) ->
          totalSize += 8 + 4 + data.size
        }
        return totalSize.toLong()
      }

      override fun RandomAccessReadBuffer.getEntrySize(): Long {
        val intArr = ByteArray(4)
        var totalSize = 4L
        read(0, intArr)
        val entriesCount = ByteBuffer.wrap(intArr).getInt()
        repeat(entriesCount) {
          read(totalSize + 8, intArr)
          val dataSize = ByteBuffer.wrap(intArr).getInt()
          totalSize += 8 + 4 + dataSize
        }
        return totalSize
      }

      override fun RandomAccessReadBuffer.deserialize(): List<Pair<EnumeratedFileAttribute, ByteArray>> {
        val intArr = ByteArray(4)
        read(0, intArr)
        var offset = 4L
        val entriesCount = ByteBuffer.wrap(intArr).getInt()
        val entries = ArrayList<Pair<EnumeratedFileAttribute, ByteArray>>(entriesCount)
        val entryArr = ByteArray(8 + 4)
        repeat(entriesCount) {
          read(offset, entryArr)
          val (compressedInfo, dataSize) = ByteBuffer.wrap(entryArr).run { getLong().toULong() to getInt() }
          offset += 8 + 4
          val data = ByteArray(dataSize)
          read(offset, data)
          entries.add(EnumeratedFileAttribute(compressedInfo) to data)
          offset += data.size
        }
        return entries
      }

      override fun RandomAccessWriteBuffer.serialize(entry: List<Pair<EnumeratedFileAttribute, ByteArray>>) {
        val intArr = ByteArray(4)
        val entryArr = ByteArray(8 + 4)
        var offset = 0L
        ByteBuffer.wrap(intArr).putInt(entry.size)
        write(offset, intArr)
        offset += 4
        entry.forEach { (attr, data) ->
          ByteBuffer.wrap(entryArr)
            .putLong(attr.compressedInfo.toLong())
            .putInt(data.size)
          write(offset, entryArr)
          offset += 8 + 4
          write(offset, data)
          offset += data.size
        }
      }
    }
  }
}