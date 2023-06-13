// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.EnumeratedFileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle.ContentRestorationSequence
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot.VirtualFileSnapshot.Property

interface ExtendedVfsSnapshot : VfsSnapshot {
  override fun getFileById(fileId: Int): ExtendedVirtualFileSnapshot

  interface ExtendedVirtualFileSnapshot : VirtualFileSnapshot {
    val attributeDataMap: Property<Map<EnumeratedFileAttribute, PayloadRef>>
    val recordAllocationExists: Property<Boolean>
    val contentRestorationSequence: Property<ContentRestorationSequence>
  }
}