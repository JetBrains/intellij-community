// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.AttributeOutputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSConnection
import com.intellij.openapi.vfs.newvfs.persistent.intercept.AttributesInterceptor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.*

class AttributesLogInterceptor(
  private val processor: OperationProcessor
) : AttributesInterceptor {
  override fun onWriteAttribute(underlying: (connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute) -> AttributeOutputStream): (connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute) -> AttributeOutputStream =
    { connection, fileId, attribute ->
      val aos = underlying(connection, fileId, attribute)
      object : AttributeOutputStream(aos) {
        override fun writeEnumeratedString(str: String?) = aos.writeEnumeratedString(str)

        override fun close() {
          catchResult(::interceptClose) {
            super.close()
          }
        }

        private fun interceptClose(result: OperationResult<Unit>) {
          val resultBuf = aos.getResultingBuffer()
          val data = Arrays.copyOfRange(resultBuf.internalBuffer, resultBuf.offset, resultBuf.offset + resultBuf.length)
          processor.enqueue {
            descriptorStorage.writeDescriptor(VfsOperationTag.ATTR_WRITE_ATTR) {
              coroutineScope {
                val attrIdEnumerated = async { stringEnumerator.enumerate(attribute.id) }
                val payloadRef = async {
                  payloadStorage.writePayload(data.size.toLong()) {
                    write(data, 0, data.size)
                  }
                }
                VfsOperation.AttributesOperation.WriteAttribute(fileId, attrIdEnumerated.await(), payloadRef.await(), result)
              }
            }
          }
        }
      }
    }

  override fun onDeleteAttributes(underlying: (connection: PersistentFSConnection, fileId: Int) -> Unit): (connection: PersistentFSConnection, fileId: Int) -> Unit =
    { connection, fileId ->
      catchResult({

      }) {
        underlying(connection, fileId)
      }
    }
}