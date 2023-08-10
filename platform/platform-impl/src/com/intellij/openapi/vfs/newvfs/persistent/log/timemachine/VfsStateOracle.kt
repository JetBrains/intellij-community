// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage

/**
 * Symbolises an external knowledge about the state of VFS at a specified point in time. Similar to [VfsTimeMachine], but [getSnapshot]
 * can return null to designate inability to tell anything about VFS at that point.
 */
interface VfsStateOracle {
  fun getSnapshot(point: OperationLogStorage.Iterator): VfsSnapshot?
}