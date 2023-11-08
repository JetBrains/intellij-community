// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.AttributeOutputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSConnection
import com.intellij.openapi.vfs.newvfs.persistent.intercept.AttributesInterceptor
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogOperationTrackingContext.Companion.trackOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogOperationTrackingContext.Companion.trackPlainOperation

class AttributesLogInterceptor(
  private val context: VfsLogOperationTrackingContext,
  private val interceptMask: VfsOperationTagsMask = VfsOperationTagsMask.AttributesMask
) : AttributesInterceptor {
  override fun onWriteAttribute(underlying: (connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute) -> AttributeOutputStream): (connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute) -> AttributeOutputStream =
    if (VfsOperationTag.ATTR_WRITE_ATTR !in interceptMask) underlying
    else { connection, fileId, attribute ->
      context.trackOperation(VfsOperationTag.ATTR_WRITE_ATTR) {
        val aos = underlying(connection, fileId, attribute)
        object : AttributeOutputStream(aos) {
          private var wasClosed: Boolean = false

          override fun writeEnumeratedString(str: String?) = aos.writeEnumeratedString(str)

          override fun close() {
            if (wasClosed) {
              super.close()
              return
            } else {
              wasClosed = true
              val data = aos.asByteArraySequence().toBytes();
              { super.close() } catchResult {
                completeTracking {
                  val payloadRef = context.payloadWriter(data)
                  val attrIdEnumerated = context.enumerateAttribute(attribute)
                  VfsOperation.AttributesOperation.WriteAttribute(fileId, attrIdEnumerated, payloadRef, it)
                }
              }
            }
          }
        }
      }
    }

  override fun onDeleteAttributes(underlying: (connection: PersistentFSConnection, fileId: Int) -> Unit): (connection: PersistentFSConnection, fileId: Int) -> Unit =
    if (VfsOperationTag.ATTR_DELETE_ATTRS !in interceptMask) underlying
    else { connection, fileId ->
      context.trackPlainOperation(VfsOperationTag.ATTR_DELETE_ATTRS, { VfsOperation.AttributesOperation.DeleteAttributes(fileId, it) }) {
        underlying(connection, fileId)
      }
    }

  override fun onSetVersion(underlying: (version: Int) -> Unit): (version: Int) -> Unit =
    if (VfsOperationTag.ATTR_SET_VERSION !in interceptMask) underlying
    else { version ->
      context.trackPlainOperation(VfsOperationTag.ATTR_SET_VERSION, { VfsOperation.AttributesOperation.SetVersion(version, it) }) {
        underlying(version)
      }
    }
}