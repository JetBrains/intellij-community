// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.compaction

import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.compaction.CompactedVfsModel.Companion.FileModel
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot.AttributeDataMap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot.ExtendedVirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.DefinedState
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsModificationContract
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.util.io.SimpleStringPersistentEnumerator
import java.lang.ref.SoftReference

class CompactedVfsSnapshot(
  private val baseVfsContext: VfsLogBaseContext,
  private val compactedState: CompactedVfsModel.CompactedVfsState,
  private val nameByNameId: (Int) -> DefinedState<String>,
  private val attributeValueEnumerator: () -> SimpleStringPersistentEnumerator,
) : ExtendedVfsSnapshot {
  override val point: () -> OperationLogStorage.Iterator = object : OperationLogStorage.Iterator {
    override fun getPosition(): Long = compactedState.position.operationLogPosition
    override fun copy(): OperationLogStorage.Iterator = this

    private fun notSupported(): Nothing = throw UnsupportedOperationException("this iterator can't be moved")
    override fun nextFiltered(mask: VfsOperationTagsMask): OperationLogStorage.OperationReadResult = notSupported()
    override fun previousFiltered(mask: VfsOperationTagsMask): OperationLogStorage.OperationReadResult = notSupported()
    override fun hasPrevious(): Boolean = notSupported()
    override fun previous(): OperationLogStorage.OperationReadResult = notSupported()
    override fun hasNext(): Boolean = notSupported()
    override fun next(): OperationLogStorage.OperationReadResult = notSupported()
  }.constCopier()

  override val payloadReader: PayloadReader get() = compactedState.payloadReader
  override fun getNameByNameId(nameId: Int): DefinedState<String> = nameByNameId(nameId)
  override fun getAttributeValueEnumerator(): SimpleStringPersistentEnumerator = attributeValueEnumerator()
  override fun enumerateAttribute(fileAttribute: FileAttribute): EnumeratedFileAttribute = baseVfsContext.enumerateAttribute(fileAttribute)

  override fun getFileById(fileId: Int): ExtendedVirtualFileSnapshot {
    if (fileId <= 0 || fileId >= compactedState.filesState.size) return NonExistentFile(fileId, this)
    return VirtualFileSnapshotImpl(fileId, this)
  }

  override fun forEachFile(body: (ExtendedVirtualFileSnapshot) -> Unit) {
    for (fileId in 1 until compactedState.filesState.size) {
      body(getFileById(fileId))
    }
  }

  override fun getContentRestorationSequence(contentRecordId: Int): DefinedState<ContentRestorationSequence> {
    if (contentRecordId < compactedState.contentsSize) {
      val seq = VfsChronicle.ContentRestorationSequenceBuilder()
      seq.setInitial(VfsModificationContract.ContentOperation.Set {
        compactedState.inflateContent(contentRecordId).let(State::Ready)
      })
      return seq.let(State::Ready)
    }
    return State.NotAvailable("content with id $contentRecordId does not exist")
  }

  private class VirtualFileSnapshotImpl(
    override val fileId: Int, override val vfsSnapshot: CompactedVfsSnapshot,
  ) : ExtendedVirtualFileSnapshot {
    private inner class Prop<R>(private val calculate: CompactedVfsModel.CompactedVfsState.() -> R) : Property<R> {
      override fun observeState(): DefinedState<R> = vfsSnapshot.compactedState.calculate().let(State::Ready)
      override fun toString(): String = observeState().toString()
    }

    override val attributeDataMap: Property<AttributeDataMap> = Prop {
      AttributeDataMap.of(
        attributesState
          .getEntry(fileId)
          .mapIndexed { index, it ->
            it.first to packAttributeReference(fileId, index)
          }
          .toMap(),
        true)
    }

    private var fileModelCache: SoftReference<FileModel> = SoftReference(null)
    private fun getFileModel(): FileModel {
      fileModelCache.get()?.let { return it }
      val fileModel = vfsSnapshot.compactedState.filesState.getEntry(fileId)
      fileModelCache = SoftReference(fileModel)
      return fileModel
    }
    override val recordAllocationExists: Property<Boolean> = Prop { true }
    override val nameId: Property<Int> = Prop { getFileModel().nameId }
    override val parentId: Property<Int> = Prop { getFileModel().parentId }
    override val length: Property<Long> = Prop { getFileModel().length }
    override val timestamp: Property<Long> = Prop { getFileModel().timestamp }
    override val flags: Property<Int> = Prop { getFileModel().flags }
    override val contentRecordId: Property<Int> = Prop { getFileModel().contentRecordId }
    override val attributesRecordId: Property<Int> = Prop { getFileModel().attributesRecordId }
  }

  companion object {
    private class NonExistentFile(override val fileId: Int, override val vfsSnapshot: ExtendedVfsSnapshot) : ExtendedVirtualFileSnapshot {
      private val notAvailableProp = object : Property<Nothing> {
        override fun observeState(): DefinedState<Nothing> {
          return State.NotAvailable("file with id $fileId does not exist")
        }
        override fun toString(): String = observeState().toString()
      }
      override val attributeDataMap: Property<AttributeDataMap> = notAvailableProp
      override val recordAllocationExists: Property<Boolean> = notAvailableProp
      override val nameId: Property<Int> = notAvailableProp
      override val parentId: Property<Int> = notAvailableProp
      override val length: Property<Long> = notAvailableProp
      override val timestamp: Property<Long> = notAvailableProp
      override val flags: Property<Int> = notAvailableProp
      override val contentRecordId: Property<Int> = notAvailableProp
      override val attributesRecordId: Property<Int> = notAvailableProp
    }
  }
}