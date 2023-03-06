// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.AttributeOutputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSConnection
import com.intellij.openapi.vfs.newvfs.persistent.intercept.AttributesInterceptor

class AttributesLogInterceptor(
  private val executor: VfsLogContextExecutor
) : AttributesInterceptor {
  override fun onWriteAttribute(underlying: (connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute) -> AttributeOutputStream): (connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute) -> AttributeOutputStream =
    { connection, fileId, attribute ->
      val aos = underlying(connection, fileId, attribute)
      object : AttributeOutputStream(aos) {
        override fun writeEnumeratedString(str: String?) = aos.writeEnumeratedString(str)

        override fun close() {
          { super.close() } catchResult ::interceptClose
        }

        private fun interceptClose(result: OperationResult<Unit>) {
          val data = aos.asByteArraySequence().toBytes()
          executor.run {
            descriptorStorage.enqueueDescriptorWrite(VfsOperationTag.ATTR_WRITE_ATTR) {
              val attrIdEnumerated = stringEnumerator.enumerate(attribute.id)
              val payloadRef =
                payloadStorage.writePayload(data.size.toLong()) {
                  write(data, 0, data.size)
                }
              VfsOperation.AttributesOperation.WriteAttribute(fileId, attrIdEnumerated, payloadRef, result)
            }
          }
        }
      }
    }

  override fun onDeleteAttributes(underlying: (connection: PersistentFSConnection, fileId: Int) -> Unit): (connection: PersistentFSConnection, fileId: Int) -> Unit =
    { connection, fileId ->
      { underlying(connection, fileId) } catchResult { result ->
        executor.run {
          descriptorStorage.enqueueDescriptorWrite(VfsOperationTag.ATTR_DELETE_ATTRS) {
            VfsOperation.AttributesOperation.DeleteAttributes(fileId, result)
          }
        }
      }
    }

  override fun onSetVersion(underlying: (version: Int) -> Unit): (version: Int) -> Unit =
    { version ->
      { underlying(version) } catchResult { result ->
        executor.run {
          descriptorStorage.enqueueDescriptorWrite(VfsOperationTag.ATTR_SET_VERSION) {
            VfsOperation.AttributesOperation.SetVersion(version, result)
          }
        }
      }
    }
}