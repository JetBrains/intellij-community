// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.intercept.RecordsInterceptor

class RecordsLogInterceptor(
  private val context: VfsLogOperationWriteContext,
  private val interceptMask: VfsOperationTagsMask = VfsOperationTagsMask.RecordsMask,
) : RecordsInterceptor {
  override fun onAllocateRecord(underlying: () -> Int): () -> Int =
    if (VfsOperationTag.REC_ALLOC !in interceptMask) underlying
    else {
      {
        { underlying() } catchResult { result ->
          context.enqueueOperationWrite(VfsOperationTag.REC_ALLOC) {
            VfsOperation.RecordsOperation.AllocateRecord(result)
          }
        }
      }
    }

  override fun onSetAttributeRecordId(underlying: (fileId: Int, recordId: Int) -> Unit): (fileId: Int, recordId: Int) -> Unit =
    if (VfsOperationTag.REC_SET_ATTR_REC_ID !in interceptMask) underlying
    else { fileId, recordId ->
      { underlying(fileId, recordId) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.REC_SET_ATTR_REC_ID) {
          VfsOperation.RecordsOperation.SetAttributeRecordId(fileId, recordId, result)
        }
      }
    }

  override fun onSetContentRecordId(underlying: (fileId: Int, recordId: Int) -> Boolean): (fileId: Int, recordId: Int) -> Boolean =
    if (VfsOperationTag.REC_SET_CONTENT_RECORD_ID !in interceptMask) underlying
    else { fileId, recordId ->
      { underlying(fileId, recordId) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.REC_SET_CONTENT_RECORD_ID) {
          VfsOperation.RecordsOperation.SetContentRecordId(fileId, recordId, result)
        }
      }
    }

  override fun onSetParent(underlying: (fileId: Int, parentId: Int) -> Unit): (fileId: Int, parentId: Int) -> Unit =
    if (VfsOperationTag.REC_SET_PARENT !in interceptMask) underlying
    else { fileId, parentId ->
      { underlying(fileId, parentId) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.REC_SET_PARENT) {
          VfsOperation.RecordsOperation.SetParent(fileId, parentId, result)
        }
      }
    }

  override fun onSetNameId(underlying: (fileId: Int, nameId: Int) -> Unit): (fileId: Int, nameId: Int) -> Unit =
    if (VfsOperationTag.REC_SET_NAME_ID !in interceptMask) underlying
    else { fileId, nameId ->
      { underlying(fileId, nameId) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.REC_SET_NAME_ID) {
          VfsOperation.RecordsOperation.SetNameId(fileId, nameId, result)
        }
      }
    }

  override fun onSetFlags(underlying: (fileId: Int, flags: Int) -> Boolean): (fileId: Int, flags: Int) -> Boolean =
    if (VfsOperationTag.REC_SET_FLAGS !in interceptMask) underlying
    else { fileId, flags ->
      { underlying(fileId, flags) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.REC_SET_FLAGS) {
          VfsOperation.RecordsOperation.SetFlags(fileId, flags, result)
        }
      }
    }

  override fun onSetLength(underlying: (fileId: Int, length: Long) -> Boolean): (fileId: Int, length: Long) -> Boolean =
    if (VfsOperationTag.REC_SET_LENGTH !in interceptMask) underlying
    else { fileId, length ->
      { underlying(fileId, length) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.REC_SET_LENGTH) {
          VfsOperation.RecordsOperation.SetLength(fileId, length, result)
        }
      }
    }

  override fun onSetTimestamp(underlying: (fileId: Int, timestamp: Long) -> Boolean): (fileId: Int, timestamp: Long) -> Boolean =
    if (VfsOperationTag.REC_SET_TIMESTAMP !in interceptMask) underlying
    else { fileId, timestamp ->
      { underlying(fileId, timestamp) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.REC_SET_TIMESTAMP) {
          VfsOperation.RecordsOperation.SetTimestamp(fileId, timestamp, result)
        }
      }
    }

  override fun onMarkRecordAsModified(underlying: (fileId: Int) -> Unit): (fileId: Int) -> Unit =
    if (VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED !in interceptMask) underlying
    else { fileId ->
      { underlying(fileId) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED) {
          VfsOperation.RecordsOperation.MarkRecordAsModified(fileId, result)
        }
      }
    }

  override fun onFillRecord(underlying: (fileId: Int, timestamp: Long, length: Long, flags: Int,
                                         nameId: Int, parentId: Int, overwriteAttrRef: Boolean) -> Unit):
    (fileId: Int, timestamp: Long, length: Long, flags: Int,
     nameId: Int, parentId: Int, overwriteAttrRef: Boolean) -> Unit =
    if (VfsOperationTag.REC_FILL_RECORD !in interceptMask) underlying
    else
      { fileId, timestamp, length, flags, nameId, parentId, overwriteAttrRef ->
        { underlying(fileId, timestamp, length, flags, nameId, parentId, overwriteAttrRef) } catchResult { result ->
          context.enqueueOperationWrite(VfsOperationTag.REC_FILL_RECORD) {
            VfsOperation.RecordsOperation.FillRecord(fileId, timestamp, length, flags, nameId, parentId, overwriteAttrRef, result)
          }
        }
      }


  override fun onCleanRecord(underlying: (fileId: Int) -> Unit): (fileId: Int) -> Unit =
    if (VfsOperationTag.REC_CLEAN_RECORD !in interceptMask) underlying
    else { fileId ->
      { underlying(fileId) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.REC_CLEAN_RECORD) {
          VfsOperation.RecordsOperation.CleanRecord(fileId, result)
        }
      }
    }

  override fun onSetVersion(underlying: (version: Int) -> Unit): (version: Int) -> Unit =
    if (VfsOperationTag.REC_SET_VERSION !in interceptMask) underlying
    else { version ->
      { underlying(version) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.REC_SET_VERSION) {
          VfsOperation.RecordsOperation.SetVersion(version, result)
        }
      }
    }
}