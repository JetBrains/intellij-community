// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.intercept.RecordsInterceptor

class RecordsLogInterceptor(
  private val processor: OperationProcessor
) : RecordsInterceptor {
  override fun onAllocateRecord(underlying: () -> Int): () -> Int =
    {
      { underlying() } catchResult { result ->
        processor.enqueue {
          descriptorStorage.writeDescriptor(VfsOperationTag.REC_ALLOC) {
            VfsOperation.RecordsOperation.AllocateRecord(result)
          }
        }
      }
    }

  override fun onSetAttributeRecordId(underlying: (fileId: Int, recordId: Int) -> Unit): (fileId: Int, recordId: Int) -> Unit =
    { fileId, recordId ->
      { underlying(fileId, recordId) } catchResult { result ->
        processor.enqueue {
          descriptorStorage.writeDescriptor(VfsOperationTag.REC_SET_ATTR_REC_ID) {
            VfsOperation.RecordsOperation.SetAttributeRecordId(fileId, recordId, result)
          }
        }
      }
    }

  override fun onSetContentRecordId(underlying: (fileId: Int, recordId: Int) -> Boolean): (fileId: Int, recordId: Int) -> Boolean =
    { fileId, recordId ->
      { underlying(fileId, recordId) } catchResult { result ->
        processor.enqueue {
          descriptorStorage.writeDescriptor(VfsOperationTag.REC_SET_CONTENT_RECORD_ID) {
            VfsOperation.RecordsOperation.SetContentRecordId(fileId, recordId, result)
          }
        }
      }
    }
}