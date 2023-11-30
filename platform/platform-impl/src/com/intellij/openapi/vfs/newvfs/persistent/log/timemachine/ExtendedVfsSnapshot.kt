// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSAttributeAccessor
import com.intellij.openapi.vfs.newvfs.persistent.log.EnumeratedFileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadReader
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence.Companion.restoreContent
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.Companion.getOrNull
import com.intellij.util.io.UnsyncByteArrayInputStream

interface ExtendedVfsSnapshot : VfsSnapshot {
  val payloadReader: PayloadReader
  fun enumerateAttribute(fileAttribute: FileAttribute): EnumeratedFileAttribute

  override fun getFileById(fileId: Int): ExtendedVirtualFileSnapshot
  fun forEachFile(body: (ExtendedVirtualFileSnapshot) -> Unit)

  fun getContentRestorationSequence(contentRecordId: Int): State.DefinedState<ContentRestorationSequence>
  override fun getContent(contentRecordId: Int): State.DefinedState<ByteArray> =
    getContentRestorationSequence(contentRecordId).bind {
      it.restoreContent(payloadReader)
    }

  override fun getChildrenIdsOf(fileId: Int): State.DefinedState<VfsSnapshot.RecoveredChildrenIds> {
    val childrenIds = mutableListOf<Int>()
    var recordAllocationExists = false
    forEachFile {
      if (it.parentId.getOrNull() == fileId) {
        childrenIds.add(it.fileId)
      }
      if (it.fileId == fileId) {
        recordAllocationExists = it.recordAllocationExists.getOrNull() ?: false
      }
    }
    return VfsSnapshot.RecoveredChildrenIds.of(childrenIds, recordAllocationExists).let(State::Ready)
  }

  interface ExtendedVirtualFileSnapshot : VirtualFileSnapshot {
    override val vfsSnapshot: ExtendedVfsSnapshot

    val recordAllocationExists: Property<Boolean>
    val attributeDataMap: Property<AttributeDataMap>

    override fun readAttribute(fileAttribute: FileAttribute): State.DefinedState<AttributeInputStream?> {
      val attrId = vfsSnapshot.enumerateAttribute(fileAttribute)
      val attrDataRef = attributeDataMap.getOrNull()?.get(attrId) ?: return State.NotAvailable()
      return vfsSnapshot.payloadReader(attrDataRef).fmap {
        PersistentFSAttributeAccessor.validateAttributeVersion(
          fileAttribute,
          AttributeInputStream(UnsyncByteArrayInputStream(it), vfsSnapshot.getAttributeValueEnumerator())
        )
      }
    }

    override fun getParent(): State.DefinedState<ExtendedVirtualFileSnapshot?> {
      @Suppress("UNCHECKED_CAST")
      return super.getParent() as State.DefinedState<ExtendedVirtualFileSnapshot?>
    }
  }

  interface AttributeDataMap : Map<EnumeratedFileAttribute, PayloadRef> {
    /**
     * `false` in case there is no evidence that the map contains all attributes
     */
    val isComplete: Boolean

    companion object {
      private class AttributeDataMapImpl(
        val data: Map<EnumeratedFileAttribute, PayloadRef>, override val isComplete: Boolean
      ): AttributeDataMap, Map<EnumeratedFileAttribute, PayloadRef> by data

      fun of(data: Map<EnumeratedFileAttribute, PayloadRef>, isComplete: Boolean): AttributeDataMap = AttributeDataMapImpl(data, isComplete)

      fun AttributeDataMap.overrideWith(rhs: AttributeDataMap): AttributeDataMap {
        val lhs = this@overrideWith
        if (rhs.isComplete) return rhs
        val newData = lhs.toMutableMap()
        rhs.forEach { (attr, ref) ->
          if (attr in newData) {
            newData.remove(attr)
          }
          newData[attr] = ref
        }
        return AttributeDataMap.of(newData, lhs.isComplete)
      }
    }
  }
}