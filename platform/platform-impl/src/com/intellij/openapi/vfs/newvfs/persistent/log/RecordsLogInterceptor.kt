// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.intercept.RecordsInterceptor
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogOperationTrackingContext.Companion.trackPlainOperation

class RecordsLogInterceptor(
  private val context: VfsLogOperationTrackingContext,
  private val interceptMask: VfsOperationTagsMask = VfsOperationTagsMask.RecordsMask,
) : RecordsInterceptor {
  override fun onAllocateRecord(underlying: () -> Int): () -> Int =
    if (VfsOperationTag.REC_ALLOC !in interceptMask) underlying
    else {
      {
        context.trackPlainOperation(VfsOperationTag.REC_ALLOC, { VfsOperation.RecordsOperation.AllocateRecord(it) }) {
          underlying()
        }
      }
    }

  override fun onSetAttributeRecordId(underlying: (fileId: Int, recordId: Int) -> Unit): (fileId: Int, recordId: Int) -> Unit =
    if (VfsOperationTag.REC_SET_ATTR_REC_ID !in interceptMask) underlying
    else { fileId, recordId ->
      context.trackPlainOperation(VfsOperationTag.REC_SET_ATTR_REC_ID,
                                  { VfsOperation.RecordsOperation.SetAttributeRecordId(fileId, recordId, it) }) {
        underlying(fileId, recordId)
      }
    }

  override fun onSetContentRecordId(underlying: (fileId: Int, recordId: Int) -> Boolean): (fileId: Int, recordId: Int) -> Boolean =
    if (VfsOperationTag.REC_SET_CONTENT_RECORD_ID !in interceptMask) underlying
    else { fileId, recordId ->
      context.trackPlainOperation(VfsOperationTag.REC_SET_CONTENT_RECORD_ID,
                                  { VfsOperation.RecordsOperation.SetContentRecordId(fileId, recordId, it) }) {
        underlying(fileId, recordId)
      }
    }

  override fun onSetParent(underlying: (fileId: Int, parentId: Int) -> Unit): (fileId: Int, parentId: Int) -> Unit =
    if (VfsOperationTag.REC_SET_PARENT !in interceptMask) underlying
    else { fileId, parentId ->
      context.trackPlainOperation(VfsOperationTag.REC_SET_PARENT,
                                  { VfsOperation.RecordsOperation.SetParent(fileId, parentId, it) }) {
        underlying(fileId, parentId)
      }
    }

  override fun onUpdateNameId(underlying: (fileId: Int, nameId: Int) -> Int): (fileId: Int, nameId: Int) -> Int =
    if (VfsOperationTag.REC_SET_NAME_ID !in interceptMask) underlying
    else { fileId, nameId ->
      context.trackPlainOperation(VfsOperationTag.REC_SET_NAME_ID,
                                  { VfsOperation.RecordsOperation.SetNameId(fileId, nameId, it) }) {
        return@trackPlainOperation underlying(fileId, nameId)
      }
    }

  override fun onSetFlags(underlying: (fileId: Int, flags: Int) -> Boolean): (fileId: Int, flags: Int) -> Boolean =
    if (VfsOperationTag.REC_SET_FLAGS !in interceptMask) underlying
    else { fileId, flags ->
      context.trackPlainOperation(VfsOperationTag.REC_SET_FLAGS,
                                  { VfsOperation.RecordsOperation.SetFlags(fileId, flags, it) }) {
        underlying(fileId, flags)
      }
    }

  override fun onSetLength(underlying: (fileId: Int, length: Long) -> Boolean): (fileId: Int, length: Long) -> Boolean =
    if (VfsOperationTag.REC_SET_LENGTH !in interceptMask) underlying
    else { fileId, length ->
      context.trackPlainOperation(VfsOperationTag.REC_SET_LENGTH,
                                  { VfsOperation.RecordsOperation.SetLength(fileId, length, it) }) {
        underlying(fileId, length)
      }
    }

  override fun onSetTimestamp(underlying: (fileId: Int, timestamp: Long) -> Boolean): (fileId: Int, timestamp: Long) -> Boolean =
    if (VfsOperationTag.REC_SET_TIMESTAMP !in interceptMask) underlying
    else { fileId, timestamp ->
      context.trackPlainOperation(VfsOperationTag.REC_SET_TIMESTAMP,
                                  { VfsOperation.RecordsOperation.SetTimestamp(fileId, timestamp, it) }) {
        underlying(fileId, timestamp)
      }
    }

  override fun onMarkRecordAsModified(underlying: (fileId: Int) -> Unit): (fileId: Int) -> Unit =
    if (VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED !in interceptMask) underlying
    else { fileId ->
      context.trackPlainOperation(VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED,
                                  { VfsOperation.RecordsOperation.MarkRecordAsModified(fileId, it) }) {
        underlying(fileId)
      }
    }

  override fun onFillRecord(underlying: (fileId: Int, timestamp: Long, length: Long, flags: Int,
                                         nameId: Int, parentId: Int, overwriteAttrRef: Boolean) -> Unit):
    (fileId: Int, timestamp: Long, length: Long, flags: Int,
     nameId: Int, parentId: Int, overwriteAttrRef: Boolean) -> Unit =
    if (VfsOperationTag.REC_FILL_RECORD !in interceptMask) underlying
    else
      { fileId, timestamp, length, flags, nameId, parentId, overwriteAttrRef ->
        context.trackPlainOperation(
          VfsOperationTag.REC_FILL_RECORD,
          { VfsOperation.RecordsOperation.FillRecord(fileId, timestamp, length, flags, nameId, parentId, overwriteAttrRef, it) }
        ) {
          underlying(fileId, timestamp, length, flags, nameId, parentId, overwriteAttrRef)
        }
      }


  override fun onCleanRecord(underlying: (fileId: Int) -> Unit): (fileId: Int) -> Unit =
    if (VfsOperationTag.REC_CLEAN_RECORD !in interceptMask) underlying
    else { fileId ->
      context.trackPlainOperation(VfsOperationTag.REC_CLEAN_RECORD,
                                  { VfsOperation.RecordsOperation.CleanRecord(fileId, it) }) {
        underlying(fileId)
      }
    }

  override fun onSetVersion(underlying: (version: Int) -> Unit): (version: Int) -> Unit =
    if (VfsOperationTag.REC_SET_VERSION !in interceptMask) underlying
    else { version ->
      context.trackPlainOperation(VfsOperationTag.REC_SET_VERSION,
                                  { VfsOperation.RecordsOperation.SetVersion(version, it) }) {
        underlying(version)
      }
    }
}