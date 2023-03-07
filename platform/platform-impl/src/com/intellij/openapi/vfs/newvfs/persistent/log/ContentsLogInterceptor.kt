// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ContentsInterceptor
import com.intellij.util.io.storage.IAppenderStream
import com.intellij.util.io.storage.IStorageDataOutput

class ContentsLogInterceptor(
  private val executor: VfsLogContextExecutor
) : ContentsInterceptor {

  override fun onWriteBytes(underlying: (record: Int, bytes: ByteArraySequence, fixedSize: Boolean) -> Unit) =
    { record: Int, bytes: ByteArraySequence, fixedSize: Boolean ->
      { underlying(record, bytes, fixedSize) } catchResult { result ->
        val data = bytes.toBytes()
        executor.enqueueDescriptorWrite(VfsOperationTag.CONTENT_WRITE_BYTES) {
          val payloadRef = payloadStorage.writePayload(data.size.toLong()) {
            write(data, 0, data.size)
          }
          VfsOperation.ContentsOperation.WriteBytes(record, fixedSize, payloadRef, result)
        }
      }
    }

  override fun onWriteStream(underlying: (record: Int) -> IStorageDataOutput): (record: Int) -> IStorageDataOutput =
    { record ->
      val sdo = underlying(record)
      object : IStorageDataOutput by sdo {
        override fun close() {
          { sdo.close() } catchResult ::interceptClose
        }

        private fun interceptClose(result: OperationResult<Unit>) {
          val data = sdo.asByteArraySequence().toBytes()
          executor.enqueueDescriptorWrite(VfsOperationTag.CONTENT_WRITE_STREAM) {
            val payloadRef = payloadStorage.writePayload(data.size.toLong()) {
              write(data, 0, data.size)
            }
            VfsOperation.ContentsOperation.WriteStream(recordId, payloadRef, result)
          }
        }
      }
    }

  override fun onWriteStream(underlying: (record: Int, fixedSize: Boolean) -> IStorageDataOutput): (record: Int, fixedSize: Boolean) -> IStorageDataOutput =
    { record, fixedSize ->
      val sdo = underlying(record, fixedSize)
      object : IStorageDataOutput by sdo {
        override fun close() {
          { sdo.close() } catchResult ::interceptClose
        }

        private fun interceptClose(result: OperationResult<Unit>) {
          val data = sdo.asByteArraySequence().toBytes()
          executor.enqueueDescriptorWrite(VfsOperationTag.CONTENT_WRITE_STREAM_2) {
            val payloadRef = payloadStorage.writePayload(data.size.toLong()) {
              write(data, 0, data.size)
            }
            VfsOperation.ContentsOperation.WriteStream2(recordId, fixedSize, payloadRef, result)
          }
        }
      }
    }

  override fun onAppendStream(underlying: (record: Int) -> IAppenderStream): (record: Int) -> IAppenderStream =
    { record ->
      val ias = underlying(record)
      object : IAppenderStream by ias {
        override fun close() {
          { ias.close() } catchResult ::interceptClose
        }

        private fun interceptClose(result: OperationResult<Unit>) {
          val data = ias.asByteArraySequence().toBytes()
          executor.enqueueDescriptorWrite(VfsOperationTag.CONTENT_APPEND_STREAM) {
            val payloadRef = payloadStorage.writePayload(data.size.toLong()) {
              write(data, 0, data.size)
            }
            VfsOperation.ContentsOperation.AppendStream(record, payloadRef, result)
          }
        }
      }
    }

  override fun onReplaceBytes(underlying: (record: Int, offset: Int, bytes: ByteArraySequence) -> Unit): (record: Int, offset: Int, bytes: ByteArraySequence) -> Unit =
    { record, offset, bytes ->
      { underlying(record, offset, bytes) } catchResult { result ->
        val data = bytes.toBytes()
        executor.enqueueDescriptorWrite(VfsOperationTag.CONTENT_REPLACE_BYTES) {
          val payloadRef = payloadStorage.writePayload(data.size.toLong()) {
            write(data, 0, data.size)
          }
          VfsOperation.ContentsOperation.ReplaceBytes(record, offset, payloadRef, result)
        }
      }
    }

  override fun onAcquireNewRecord(underlying: () -> Int): () -> Int =
    {
      { underlying() } catchResult { result ->
        executor.enqueueDescriptorWrite(VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD) {
          VfsOperation.ContentsOperation.AcquireNewRecord(result)
        }
      }
    }

  override fun onAcquireRecord(underlying: (record: Int) -> Unit): (record: Int) -> Unit =
    { record ->
      { underlying(record) } catchResult { result ->
        executor.enqueueDescriptorWrite(VfsOperationTag.CONTENT_ACQUIRE_RECORD) {
          VfsOperation.ContentsOperation.AcquireRecord(record, result)
        }
      }
    }


  override fun onReleaseRecord(underlying: (record: Int) -> Unit): (record: Int) -> Unit =
    { record ->
      { underlying(record) } catchResult { result ->
        executor.enqueueDescriptorWrite(VfsOperationTag.CONTENT_RELEASE_RECORD) {
          VfsOperation.ContentsOperation.ReleaseRecord(record, result)
        }
      }
    }

  override fun onSetVersion(underlying: (version: Int) -> Unit): (version: Int) -> Unit =
    { version ->
      { underlying(version) } catchResult { result ->
        executor.enqueueDescriptorWrite(VfsOperationTag.CONTENT_SET_VERSION) {
          VfsOperation.ContentsOperation.SetVersion(version, result)
        }
      }
    }
}