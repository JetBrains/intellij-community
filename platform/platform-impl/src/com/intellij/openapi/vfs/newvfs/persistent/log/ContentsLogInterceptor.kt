// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ContentsInterceptor
import com.intellij.util.io.storage.IAppenderStream
import com.intellij.util.io.storage.IStorageDataOutput

class ContentsLogInterceptor(
  private val context: VfsLogOperationWriteContext,
  private val interceptMask: VfsOperationTagsMask = VfsOperationTagsMask.ContentsMask
) : ContentsInterceptor {

  override fun onWriteBytes(underlying: (record: Int, bytes: ByteArraySequence, fixedSize: Boolean) -> Unit): (Int, ByteArraySequence, Boolean) -> Unit =
    if (VfsOperationTag.CONTENT_WRITE_BYTES !in interceptMask) underlying
    else { record: Int, bytes: ByteArraySequence, fixedSize: Boolean ->
      { underlying(record, bytes, fixedSize) } catchResult { result ->
        val data = bytes.toBytes()
        context.enqueueOperationWithPayloadWrite(VfsOperationTag.CONTENT_WRITE_BYTES, data) { payloadRef ->
          VfsOperation.ContentsOperation.WriteBytes(record, fixedSize, payloadRef, result)
        }
      }
    }

  override fun onWriteStream(underlying: (record: Int) -> IStorageDataOutput): (record: Int) -> IStorageDataOutput =
    if (VfsOperationTag.CONTENT_WRITE_STREAM !in interceptMask) underlying
    else { record ->
      val sdo = underlying(record)
      object : IStorageDataOutput by sdo {
        override fun close() {
          val data = sdo.asByteArraySequence().toBytes();
          { sdo.close() } catchResult { interceptClose(data, it) }
        }

        private fun interceptClose(data: ByteArray, result: OperationResult<Unit>) {
          context.enqueueOperationWithPayloadWrite(VfsOperationTag.CONTENT_WRITE_STREAM, data) { payloadRef ->
            VfsOperation.ContentsOperation.WriteStream(record, payloadRef, result)
          }
        }
      }
    }

  override fun onWriteStream(underlying: (record: Int, fixedSize: Boolean) -> IStorageDataOutput): (record: Int, fixedSize: Boolean) -> IStorageDataOutput =
    if (VfsOperationTag.CONTENT_WRITE_STREAM_2 !in interceptMask) underlying
    else { record, fixedSize ->
      val sdo = underlying(record, fixedSize)
      object : IStorageDataOutput by sdo {
        override fun close() {
          val data = sdo.asByteArraySequence().toBytes();
          { sdo.close() } catchResult { interceptClose(data, it) }
        }

        private fun interceptClose(data: ByteArray, result: OperationResult<Unit>) {
          context.enqueueOperationWithPayloadWrite(VfsOperationTag.CONTENT_WRITE_STREAM_2, data) { payloadRef ->
            VfsOperation.ContentsOperation.WriteStream2(record, fixedSize, payloadRef, result)
          }
        }
      }
    }

  override fun onAppendStream(underlying: (record: Int) -> IAppenderStream): (record: Int) -> IAppenderStream =
    if (VfsOperationTag.CONTENT_APPEND_STREAM !in interceptMask) underlying
    else { record ->
      val ias = underlying(record)
      object : IAppenderStream by ias {
        override fun close() {
          val data = ias.asByteArraySequence().toBytes();
          { ias.close() } catchResult { interceptClose(data, it) }
        }

        private fun interceptClose(data: ByteArray, result: OperationResult<Unit>) {
          context.enqueueOperationWithPayloadWrite(VfsOperationTag.CONTENT_APPEND_STREAM, data) { payloadRef ->
            VfsOperation.ContentsOperation.AppendStream(record, payloadRef, result)
          }
        }
      }
    }

  override fun onReplaceBytes(underlying: (record: Int, offset: Int, bytes: ByteArraySequence) -> Unit): (record: Int, offset: Int, bytes: ByteArraySequence) -> Unit =
    if (VfsOperationTag.CONTENT_REPLACE_BYTES !in interceptMask) underlying
    else { record, offset, bytes ->
      { underlying(record, offset, bytes) } catchResult { result ->
        val data = bytes.toBytes()
        context.enqueueOperationWithPayloadWrite(VfsOperationTag.CONTENT_REPLACE_BYTES, data) { payloadRef ->
          VfsOperation.ContentsOperation.ReplaceBytes(record, offset, payloadRef, result)
        }
      }
    }

  override fun onAcquireNewRecord(underlying: () -> Int): () -> Int =
    if (VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD !in interceptMask) underlying
    else {
      {
        { underlying() } catchResult { result ->
          context.enqueueOperationWrite(VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD) {
            VfsOperation.ContentsOperation.AcquireNewRecord(result)
          }
        }
      }
    }

  override fun onAcquireRecord(underlying: (record: Int) -> Unit): (record: Int) -> Unit =
    if (VfsOperationTag.CONTENT_ACQUIRE_RECORD !in interceptMask) underlying
    else { record ->
      { underlying(record) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.CONTENT_ACQUIRE_RECORD) {
          VfsOperation.ContentsOperation.AcquireRecord(record, result)
        }
      }
    }


  override fun onReleaseRecord(underlying: (record: Int) -> Unit): (record: Int) -> Unit =
    if (VfsOperationTag.CONTENT_RELEASE_RECORD !in interceptMask) underlying
    else { record ->
      { underlying(record) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.CONTENT_RELEASE_RECORD) {
          VfsOperation.ContentsOperation.ReleaseRecord(record, result)
        }
      }
    }

  override fun onSetVersion(underlying: (version: Int) -> Unit): (version: Int) -> Unit =
    if (VfsOperationTag.CONTENT_SET_VERSION !in interceptMask) underlying
    else { version ->
      { underlying(version) } catchResult { result ->
        context.enqueueOperationWrite(VfsOperationTag.CONTENT_SET_VERSION) {
          VfsOperation.ContentsOperation.SetVersion(version, result)
        }
      }
    }
}