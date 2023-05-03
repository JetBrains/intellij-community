// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTagsMask

object VfsChronicle {
  inline fun figureOutNameId(iterator: OperationLogStorage.Iterator,
                             fileId: Int,
                             crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }): Int? {
    var distance = 0
    while (iterator.hasPrevious() && condition(iterator)) {
      distance++
      val read = iterator.previousFiltered(VfsOperationTagsMask(VfsOperationTag.REC_SET_NAME_ID, VfsOperationTag.REC_FILL_RECORD))
      if (read !is OperationLogStorage.OperationReadResult.Valid) continue
      when (read.operation) {
        is VfsOperation.RecordsOperation.SetNameId -> {
          if (read.operation.fileId == fileId) {
            println("nameId distance: $distance")
            return read.operation.nameId
          }
        }
        is VfsOperation.RecordsOperation.FillRecord -> {
          if (read.operation.fileId == fileId) {
            println("nameId distance: $distance")
            return read.operation.nameId
          }
        }
        else -> {}
      }
    }
    return null
  }

  inline fun figureOutParentId(iterator: OperationLogStorage.Iterator,
                               fileId: Int,
                               crossinline condition: (OperationLogStorage.Iterator) -> Boolean = { true }): Int? {
    var distance = 0
    while (iterator.hasPrevious() && condition(iterator)) {
      distance++
      val read = iterator.previousFiltered(VfsOperationTagsMask(VfsOperationTag.REC_SET_PARENT, VfsOperationTag.REC_FILL_RECORD))
      if (read !is OperationLogStorage.OperationReadResult.Valid) continue
      when (read.operation) {
        is VfsOperation.RecordsOperation.SetParent -> {
          if (read.operation.fileId == fileId) {
            println("parentId distance: $distance")
            return read.operation.parentId
          }
        }
        is VfsOperation.RecordsOperation.FillRecord -> {
          if (read.operation.fileId == fileId) {
            println("parentId distance: $distance")
            return read.operation.parentId
          }
        }
        else -> {}
      }
    }
    return null
  }
}