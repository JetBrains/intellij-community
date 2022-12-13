// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.AbstractAttributesStorage
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSConnection
import com.intellij.util.io.storage.IAppenderStream
import com.intellij.util.io.storage.IStorageDataOutput
import com.intellij.util.io.storage.RefCountingContentStorage

object InterceptorInjection {
  fun injectInContents(storage: RefCountingContentStorage, interceptors: List<ContentsInterceptor>): RefCountingContentStorage =
    object : RefCountingContentStorage by storage {
      private val LOG = Logger.getInstance(RefCountingContentStorage::class.java)

      override fun writeBytes(record: Int, bytes: ByteArraySequence, fixedSize: Boolean) {
        interceptors.forEach { it.onWriteBytes(record, bytes, fixedSize) }
        storage.writeBytes(record, bytes, fixedSize)
      }

      override fun writeStream(record: Int): IStorageDataOutput = storage.writeStream(record).also {
        LOG.warn("writeStream $record")
      }

      override fun writeStream(record: Int, fixedSize: Boolean): IStorageDataOutput = storage.writeStream(record, fixedSize).also {
        LOG.warn("writeStream $record $fixedSize")
      }

      override fun appendStream(record: Int): IAppenderStream = storage.appendStream(record).also {
        LOG.warn("appendStream $record")
      }

      override fun replaceBytes(record: Int, offset: Int, bytes: ByteArraySequence) =
        storage.replaceBytes(record, offset, bytes).also {
          LOG.warn("replaceBytes $record $offset len ${bytes.length}")
        }

      override fun acquireNewRecord(): Int = storage.acquireNewRecord().also {
        LOG.warn("acquireNewRecord -> $it")
      }

      override fun setVersion(expectedVersion: Int) = storage.setVersion(expectedVersion).also {
        LOG.warn("setVersion $expectedVersion")
      }

      override fun acquireRecord(record: Int) = storage.acquireRecord(record).also {
        LOG.warn("acquireRecord $record")
      }

      override fun releaseRecord(record: Int) = storage.releaseRecord(record).also {
        LOG.warn("releaseRecord $record")
      }
    }

  fun injectInAttributes(storage: AbstractAttributesStorage, interceptors: List<AttributesInterceptor>): AbstractAttributesStorage =
    object : AbstractAttributesStorage by storage {
      private val LOG = Logger.getInstance(AbstractAttributesStorage::class.java)

      override fun deleteAttributes(connection: PersistentFSConnection?, fileId: Int) {
        interceptors.forEach { it.onDeleteAttributes(fileId) }
        storage.deleteAttributes(connection, fileId)
      }

      override fun setVersion(version: Int) = storage.setVersion(version).also {
        LOG.warn("setVersion $version")
      }

      // leftmost interceptor in the list is the outer interceptor
      override fun writeAttribute(connection: PersistentFSConnection?, fileId: Int, attribute: FileAttribute): AttributeOutputStream =
        interceptors.foldRight(storage.writeAttribute(connection, fileId, attribute)) { interceptor, aos ->
          interceptor.onWriteAttribute(aos, fileId, attribute)
        }
    }
}