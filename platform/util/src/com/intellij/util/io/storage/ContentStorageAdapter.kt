// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.ByteArraySequence
import java.io.DataInputStream
import java.io.IOException

/**
 * Adapter RefCountingContentStorage -> VFSContentStorage
 */
class ContentStorageAdapter(private val adaptee: RefCountingContentStorage) : VFSContentStorage {
  @Throws(IOException::class)
  override fun createRecordIdIterator(): RecordIdIterator = adaptee.createRecordIdIterator()

  @Throws(IOException::class)
  override fun getRecordsCount(): Int = adaptee.recordsCount

  @Throws(IOException::class)
  override fun createNewRecord(): Int = adaptee.acquireNewRecord()

  @Throws(IOException::class)
  override fun getVersion(): Int = adaptee.version

  @Throws(IOException::class)
  override fun setVersion(expectedVersion: Int) {
    adaptee.version = expectedVersion
  }

  @Throws(IOException::class)
  override fun readStream(recordId: Int): DataInputStream = adaptee.readStream(recordId)

  @Throws(IOException::class)
  override fun writeBytes(recordId: Int, bytes: ByteArraySequence, fixedSize: Boolean): Unit = adaptee.writeBytes(recordId, bytes, fixedSize)

  override fun isDirty(): Boolean = adaptee.isDirty

  @Throws(IOException::class)
  override fun force(): Unit = adaptee.force()

  @Throws(IOException::class)
  override fun close(): Unit = Disposer.dispose(adaptee)

  @Throws(IOException::class)
  override fun closeAndClean(): Unit = adaptee.closeAndClean()
}
