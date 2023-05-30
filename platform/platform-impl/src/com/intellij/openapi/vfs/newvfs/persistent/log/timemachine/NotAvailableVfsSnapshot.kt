// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage


class NotAvailableVfsSnapshot(point: OperationLogStorage.Iterator) : VfsSnapshot {
  override val point: () -> OperationLogStorage.Iterator = point.constCopier()

  override fun getFileById(fileId: Int): VfsSnapshot.VirtualFileSnapshot {
    return NotAvailableVirtualFileSnapshot(fileId)
  }

  class NotAvailableVirtualFileSnapshot(override val fileId: Int) : VfsSnapshot.VirtualFileSnapshot {
    override val nameId: VfsSnapshot.VirtualFileSnapshot.Property<Int> = NotAvailableProp()
    override val parentId: VfsSnapshot.VirtualFileSnapshot.Property<Int> = NotAvailableProp()
    override val length: VfsSnapshot.VirtualFileSnapshot.Property<Long> = NotAvailableProp()
    override val timestamp: VfsSnapshot.VirtualFileSnapshot.Property<Long> = NotAvailableProp()
    override val flags: VfsSnapshot.VirtualFileSnapshot.Property<Int> = NotAvailableProp()
    override val contentRecordId: VfsSnapshot.VirtualFileSnapshot.Property<Int> = NotAvailableProp()
    override val attributesRecordId: VfsSnapshot.VirtualFileSnapshot.Property<Int> = NotAvailableProp()
    override val name: VfsSnapshot.VirtualFileSnapshot.Property<String> = NotAvailableProp()
    override val parent: VfsSnapshot.VirtualFileSnapshot.Property<VfsSnapshot.VirtualFileSnapshot?> = NotAvailableProp()
    override fun getContent(): VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState<ByteArray> = VfsSnapshot.VirtualFileSnapshot.Property.State.NotAvailable()
    override fun readAttribute(fileAttribute: FileAttribute): VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState<AttributeInputStream?> = VfsSnapshot.VirtualFileSnapshot.Property.State.NotAvailable()
    override fun getChildrenIds(): VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState<VfsSnapshot.VirtualFileSnapshot.RecoveredChildrenIds> = VfsSnapshot.VirtualFileSnapshot.Property.State.NotAvailable()

    class NotAvailableProp<T> : VfsSnapshot.VirtualFileSnapshot.Property<T>() {
      override fun compute(): State.DefinedState<T> = State.NotAvailable()
    }
  }
}