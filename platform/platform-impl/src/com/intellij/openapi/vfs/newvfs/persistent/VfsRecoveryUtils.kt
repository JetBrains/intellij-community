// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.io.DataInputOutputUtilRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.MUST_RELOAD_CONTENT
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.MUST_RELOAD_LENGTH
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogContext
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot.ExtendedVirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.NotEnoughInformationCause
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.SinglePassVfsTimeMachine
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsRecoveryException
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Companion.notDeleted
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.*
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.mapCases
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.PersistentStringEnumerator
import com.intellij.util.io.SimpleStringPersistentEnumerator
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

object VfsRecoveryUtils {

  class RecoveryResult {
    var recoveredFiles: Int = 0
      internal set
    var botchedFiles: Int = 0
      internal set
    var botchedAttributesCount: Long = 0
      internal set

    override fun toString(): String =
      "RecoveryResult(recoveredFiles=$recoveredFiles, botchedFiles=$botchedFiles, botchedAttributesCount=$botchedAttributesCount)"
  }

  /**
   * Enumerators must be ok as they are copied into the new VFS caches directory.
   */
  fun recoverFromPoint(point: OperationLogStorage.Iterator,
                       logContext: VfsLogContext,
                       oldStorageDir: Path,
                       newStorageDir: Path) = wrapRecovery {
    check(oldStorageDir.isDirectory())
    FileUtil.ensureExists(newStorageDir.toFile())
    newStorageDir.forEachDirectoryEntry { throw IllegalArgumentException("directory for recovered vfs is not empty") }

    fun recoveryFail(msg: String? = null, cause: Throwable? = null): Nothing = throw VfsRecoveryException(msg, cause)

    // TODO some kind of recovery marker to persist status

    val namesEnumFile = "names"
    val attrEnumFile = "attributes_enums"
    copyFilesStartingWith(namesEnumFile, oldStorageDir, newStorageDir)
    copyFilesStartingWith(attrEnumFile, oldStorageDir, newStorageDir)

    val newStoragePaths = PersistentFSPaths(newStorageDir)
    val namesEnum = PersistentStringEnumerator(newStoragePaths.storagePath(namesEnumFile), true)
    val attributeEnumerator = SimpleStringPersistentEnumerator(newStoragePaths.storagePath(attrEnumFile))

    val payloadReader: (PayloadRef) -> DefinedState<ByteArray> = {
      val data = logContext.payloadStorage.readAt(it)
      if (data == null) NotAvailable(NotEnoughInformationCause("data is not available anymore"))
      else Ready(data)
    }
    val vtm = SinglePassVfsTimeMachine(logContext, namesEnum::valueOf, attributeEnumerator, payloadReader)

    val snapshot = vtm.getSnapshot(point)

    val newVfsLog = VfsLog(newStorageDir / "vfslog", false)
    val newFsRecords = FSRecordsImpl.connect(newStorageDir, newVfsLog.connectionInterceptors, FSRecordsImpl.ErrorHandler { records, error ->
      recoveryFail("Failed to recover VFS due to an error in new FSRecords", error)
    })
    val botchedRecords = BitSet()
    val initializedRecords = BitSet()

    // root record is expected to be initialized already
    val rootId = PersistentFSTreeAccessor.ROOT_RECORD_ID
    initializedRecords[rootId] = true
    var lastAllocatedRecord = rootId
    fun ensureAllocated(id: Int) {
      while (lastAllocatedRecord < id) {
        val newRecord = newFsRecords.createRecord()
        check(newRecord == lastAllocatedRecord + 1)
        lastAllocatedRecord = newRecord
      }
    }

    val root = snapshot.getFileById(rootId)
    root.checkIsAvailable()

    // file.isAvailable() == true for contents
    val recoveryQueue = ArrayDeque<ExtendedVirtualFileSnapshot>()

    fun childrenOf(file: ExtendedVirtualFileSnapshot,
                   onChildrenNotAvailable: (NotEnoughInformationCause) -> Nothing? = { null }): List<ExtendedVirtualFileSnapshot>? =
      file.getChildrenIds().fmap {
        it.map { id -> snapshot.getFileById(id) }.notDeleted().filter { file -> file.isAvailable() }
      }.mapCases(onChildrenNotAvailable) { it }

    val rootChildren = childrenOf(root) { recoveryFail("root children data is unavailable", it.cause) }!!
    recoveryQueue.addAll(rootChildren)
    initializedRecords[rootId] = true

    while (recoveryQueue.isNotEmpty()) {
      val file = recoveryQueue.pop()
      if (botchedRecords[file.fileId] || initializedRecords[file.fileId]) {
        // attempt of initialization already was made -- multiple parents
        if (!botchedRecords[file.fileId]) {
          botchedRecords[file.fileId] = true
          botchedFiles++
          initializedRecords[file.fileId] = false
        }
        continue
      }
      check(file.isAvailable())
      try {
        ensureAllocated(file.fileId)
        // set fields
        newFsRecords.fillRecord(file.fileId, file.timestamp.get(), file.length.get(), file.flags.get(), file.nameId.get(),
                                file.parentId.get(), true)
        // recover content if available
        if (file.contentRecordId.get() != 0) {
          file.getContent().mapCases(onNotAvailable = {
            newFsRecords.setFlags(file.fileId, file.flags.get() or MUST_RELOAD_CONTENT or MUST_RELOAD_LENGTH)
          }) {
            newFsRecords.writeContent(
              file.fileId,
              ByteArraySequence(it),
              false // FIXME doesn't look ok
            )
          }
        }
        // recover available attrs except children
        val childAttrLogId = logContext.enumerateAttribute(PersistentFSTreeAccessor.CHILDREN_ATTR)
        for ((enumeratedAttrId, dataRef) in file.attributeDataMap.get()) {
          if (enumeratedAttrId == childAttrLogId) continue
          val attr = logContext.deenumerateAttribute(enumeratedAttrId) ?: throw IllegalStateException(
            "cannot deenumerate attribute using vfslog enumerator (enumeratedAttribute=$enumeratedAttrId)")
          val attrData = payloadReader(dataRef)
          if (attrData !is Ready) continue; // skip if NotAvailable
          val attrContent = attrData.value.let {
            // FIXME this doesn't look like it should be here
            if (!attr.isVersioned) return@let it
            // cut out prefix version because it will be added anyway inside the attributes accessor
            val buf = BufferExposingByteArrayOutputStream()
            DataInputOutputUtilRt.writeINT(DataOutputStream(buf), attr.version)
            val serializedVer = buf.toByteArray()
            if (it.size >= serializedVer.size && it.copyOfRange(0, serializedVer.size).contentEquals(serializedVer)) {
              return@let it.copyOfRange(serializedVer.size, it.size)
            }
            return@let null // version mismatch or corrupted (?) content
          } ?: continue
          try {
            newFsRecords.writeAttribute(file.fileId, attr).use {
              it.write(attrContent)
            }
          }
          catch (e: Throwable) {
            botchedAttributesCount++
            if (e is IOException) {
              throw e
            }
          }
        }
        // recover children and enqueue their recovery
        // TODO maybe try to recover from CHILD_ATTR if a flag is set?
        childrenOf(file)?.let {
          recoveryQueue.addAll(it)
        }
      }
      catch (e: Throwable) {
        botchedRecords[file.fileId] = true
        botchedFiles++
        continue
      }
      initializedRecords[rootId] = true
    }

    // set children attr
    // TODO check for cycles somewhere here?
    val connectedRecords = BitSet()
    val rootValidChildren = rootChildren.filter { !botchedRecords[it.fileId] }
    newFsRecords.writeAttribute(rootId, PersistentFSTreeAccessor.CHILDREN_ATTR).use { output ->
      // root children attr is a special case
      val (ids, names) = rootValidChildren
        .sortedBy { it.fileId }.map { it.fileId to it.nameId.get() }.unzip()
      PersistentFSTreeAccessor.saveNameIdSequenceWithDeltas(ids.toIntArray(), names.toIntArray(), output)
    }
    recoveryQueue.addAll(rootValidChildren)
    connectedRecords[rootId] = true
    rootValidChildren.forEach {
      connectedRecords[it.fileId] = true
      recoveredFiles++
    }
    while (recoveryQueue.isNotEmpty()) {
      val file = recoveryQueue.pop()
      try {
        val validChildren = childrenOf(file)
          ?.filter { !botchedRecords[it.fileId] && !connectedRecords[it.fileId] }
        validChildren?.let {
          recoveryQueue.addAll(it)
          it.forEach { child ->
            connectedRecords[child.fileId] = true
            recoveredFiles++
          }
        }
      }
      catch (e: Throwable) {
        botchedRecords[file.fileId] = true
        botchedFiles++
        connectedRecords[file.fileId] = false
        recoveredFiles--
        if (e is IOException) throw e
      }
    }
  }

  private inline fun wrapRecovery(body: RecoveryResult.() -> Unit): RecoveryResult =
    try {
      RecoveryResult().also(body)
    }
    catch (e: VfsRecoveryException) {
      throw e
    }
    catch (e: Throwable) {
      throw VfsRecoveryException("Failed to recover VFS", e)
    }

  private val VfsSnapshot.VirtualFileSnapshot.necessaryProps
    get() = listOf(name, parentId, length, timestamp, flags, contentRecordId)

  private fun VfsSnapshot.VirtualFileSnapshot.isAvailable(): Boolean =
    necessaryProps.all { it.observeState() is Ready<*> }

  private fun VfsSnapshot.VirtualFileSnapshot.checkIsAvailable() =
    necessaryProps.map { it.observeState() }.forEach {
      if (it is NotAvailable) {
        throw VfsRecoveryException("Failed to recover VFS because some data is irrecoverable", it.cause)
      }
    }

  private fun copyFilesStartingWith(prefix: String, fromDir: Path, toDir: Path) {
    fromDir.listDirectoryEntries("$prefix*").forEach {
      it.copyTo(toDir / it.fileName, true)
    }
  }
}