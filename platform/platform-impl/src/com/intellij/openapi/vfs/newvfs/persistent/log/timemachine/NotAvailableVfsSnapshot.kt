// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.RecoveredChildrenIds


class NotAvailableVfsSnapshot(point: OperationLogStorage.Iterator) : VfsSnapshot {
  override val point: () -> OperationLogStorage.Iterator = point.constCopier()

  override fun getFileById(fileId: Int): VirtualFileSnapshot {
    return NotAvailableVirtualFileSnapshot(fileId)
  }

  class NotAvailableVirtualFileSnapshot(override val fileId: Int) : VirtualFileSnapshot {
    override val nameId: Property<Int> = NotAvailableProp()
    override val parentId: Property<Int> = NotAvailableProp()
    override val length: Property<Long> = NotAvailableProp()
    override val timestamp: Property<Long> = NotAvailableProp()
    override val flags: Property<Int> = NotAvailableProp()
    override val contentRecordId: Property<Int> = NotAvailableProp()
    override val attributesRecordId: Property<Int> = NotAvailableProp()
    override val name: Property<String> = NotAvailableProp()
    override val parent: Property<VirtualFileSnapshot?> = NotAvailableProp()
    override fun getContent(): DefinedState<ByteArray> = State.NotAvailable()
    override fun readAttribute(fileAttribute: FileAttribute): DefinedState<AttributeInputStream?> = State.NotAvailable()
    override fun getChildrenIds(): DefinedState<RecoveredChildrenIds> = State.NotAvailable()

    class NotAvailableProp<T> : Property<T>() {
      override fun compute(): DefinedState<T> = State.NotAvailable()
    }
  }
}