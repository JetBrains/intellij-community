// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ContentsInterceptor
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogOperationTrackingContext.Companion.trackOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogOperationTrackingContext.Companion.trackPlainOperation
import com.intellij.util.io.storage.IAppenderStream
import com.intellij.util.io.storage.IStorageDataOutput

class ContentsLogInterceptor(
  private val context: VfsLogOperationTrackingContext,
  private val interceptMask: VfsOperationTagsMask = VfsOperationTagsMask.ContentsMask
) : ContentsInterceptor {

  override fun onWriteBytes(underlying: (record: Int, bytes: ByteArraySequence, fixedSize: Boolean) -> Unit): (Int, ByteArraySequence, Boolean) -> Unit =
    if (VfsOperationTag.CONTENT_WRITE_BYTES !in interceptMask) underlying
    else { record: Int, bytes: ByteArraySequence, fixedSize: Boolean ->
      val data = bytes.toBytes()
      context.trackPlainOperation(VfsOperationTag.CONTENT_WRITE_BYTES, {
        val payloadRef = context.payloadWriter(data)
        VfsOperation.ContentsOperation.WriteBytes(record, fixedSize, payloadRef, it)
      }) { underlying(record, bytes, fixedSize) }
    }

  override fun onWriteStream(underlying: (record: Int) -> IStorageDataOutput): (record: Int) -> IStorageDataOutput =
    if (VfsOperationTag.CONTENT_WRITE_STREAM !in interceptMask) underlying
    else { record ->
      context.trackOperation(VfsOperationTag.CONTENT_WRITE_STREAM) {
        val sdo = underlying(record)
        object : IStorageDataOutput by sdo {
          private var wasClosed = false
          override fun close() {
            if (wasClosed) {
              sdo.close()
              return
            }
            wasClosed = true
            val data = sdo.asByteArraySequence().toBytes();
            { sdo.close() } catchResult { result ->
              completeTracking {
                val payloadRef = context.payloadWriter(data)
                VfsOperation.ContentsOperation.WriteStream(record, payloadRef, result)
              }
            }
          }
        }
      }
    }

  override fun onWriteStream(underlying: (record: Int, fixedSize: Boolean) -> IStorageDataOutput): (record: Int, fixedSize: Boolean) -> IStorageDataOutput =
    if (VfsOperationTag.CONTENT_WRITE_STREAM_2 !in interceptMask) underlying
    else { record, fixedSize ->
      context.trackOperation(VfsOperationTag.CONTENT_WRITE_STREAM_2) {
        val sdo = underlying(record, fixedSize)
        object : IStorageDataOutput by sdo {
          private var wasClosed: Boolean = false
          override fun close() {
            if (wasClosed) {
              sdo.close()
              return
            }
            wasClosed = true
            val data = sdo.asByteArraySequence().toBytes();
            { sdo.close() } catchResult { result ->
              completeTracking {
                val payloadRef = context.payloadWriter(data)
                VfsOperation.ContentsOperation.WriteStream2(record, fixedSize, payloadRef, result)
              }
            }
          }
        }
      }
    }

  override fun onAppendStream(underlying: (record: Int) -> IAppenderStream): (record: Int) -> IAppenderStream =
    if (VfsOperationTag.CONTENT_APPEND_STREAM !in interceptMask) underlying
    else { record ->
      context.trackOperation(VfsOperationTag.CONTENT_APPEND_STREAM) {
        val ias = underlying(record)
        object : IAppenderStream by ias {
          private var wasClosed: Boolean = false
          override fun close() {
            if (wasClosed) {
              ias.close()
              return
            }
            wasClosed = true
            val data = ias.asByteArraySequence().toBytes();
            { ias.close() } catchResult { result ->
              completeTracking {
                val payloadRef = context.payloadWriter(data)
                VfsOperation.ContentsOperation.AppendStream(record, payloadRef, result)
              }
            }
          }
        }
      }
    }

  override fun onReplaceBytes(underlying: (record: Int, offset: Int, bytes: ByteArraySequence) -> Unit): (record: Int, offset: Int, bytes: ByteArraySequence) -> Unit =
    if (VfsOperationTag.CONTENT_REPLACE_BYTES !in interceptMask) underlying
    else { record, offset, bytes ->
      val data = bytes.toBytes()
      context.trackPlainOperation(VfsOperationTag.CONTENT_REPLACE_BYTES, {
        val payloadRef = context.payloadWriter(data)
        VfsOperation.ContentsOperation.ReplaceBytes(record, offset, payloadRef, it)
      }) { underlying(record, offset, bytes) }
    }

  override fun onAcquireNewRecord(underlying: () -> Int): () -> Int =
    if (VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD !in interceptMask) underlying
    else {
      {
        context.trackPlainOperation(VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD, { VfsOperation.ContentsOperation.AcquireNewRecord(it) }) {
          underlying()
        }
      }
    }

  override fun onAcquireRecord(underlying: (record: Int) -> Unit): (record: Int) -> Unit =
    if (VfsOperationTag.CONTENT_ACQUIRE_RECORD !in interceptMask) underlying
    else { record ->
      context.trackPlainOperation(VfsOperationTag.CONTENT_ACQUIRE_RECORD, { VfsOperation.ContentsOperation.AcquireRecord(record, it) }) {
        underlying(record)
      }
    }


  override fun onReleaseRecord(underlying: (record: Int) -> Unit): (record: Int) -> Unit =
    if (VfsOperationTag.CONTENT_RELEASE_RECORD !in interceptMask) underlying
    else { record ->
      context.trackPlainOperation(VfsOperationTag.CONTENT_RELEASE_RECORD, { VfsOperation.ContentsOperation.ReleaseRecord(record, it) }) {
        underlying(record)
      }
    }

  override fun onSetVersion(underlying: (version: Int) -> Unit): (version: Int) -> Unit =
    if (VfsOperationTag.CONTENT_SET_VERSION !in interceptMask) underlying
    else { version ->
      context.trackPlainOperation(VfsOperationTag.CONTENT_SET_VERSION, { VfsOperation.ContentsOperation.SetVersion(version, it) }) {
        underlying(version)
      }
    }
}