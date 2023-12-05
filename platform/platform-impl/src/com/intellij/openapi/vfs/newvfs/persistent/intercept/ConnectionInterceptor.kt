// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.intercept

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSConnection
import com.intellij.util.io.storage.IAppenderStream
import com.intellij.util.io.storage.IStorageDataOutput

sealed interface ConnectionInterceptor

interface ContentsInterceptor : ConnectionInterceptor {
  fun onWriteBytes(underlying: (record: Int, bytes: ByteArraySequence, fixedSize: Boolean) -> Unit): (record: Int, bytes: ByteArraySequence, fixedSize: Boolean) -> Unit = underlying
  fun onWriteStream(underlying: (record: Int) -> IStorageDataOutput): (record: Int) -> IStorageDataOutput = underlying
  fun onWriteStream(underlying: (record: Int, fixedSize: Boolean) -> IStorageDataOutput): (record: Int, fixedSize: Boolean) -> IStorageDataOutput = underlying
  fun onAppendStream(underlying: (record: Int) -> IAppenderStream): (record: Int) -> IAppenderStream = underlying
  fun onReplaceBytes(underlying: (record: Int, offset: Int, bytes: ByteArraySequence) -> Unit): (record: Int, offset: Int, bytes: ByteArraySequence) -> Unit = underlying
  fun onAcquireNewRecord(underlying: () -> Int): () -> Int = underlying
  fun onAcquireRecord(underlying: (record: Int) -> Unit): (record: Int) -> Unit = underlying
  fun onReleaseRecord(underlying: (record: Int) -> Unit): (record: Int) -> Unit = underlying
  fun onSetVersion(underlying: (version: Int) -> Unit): (version: Int) -> Unit = underlying
}

interface RecordsInterceptor : ConnectionInterceptor {
  fun onAllocateRecord(underlying: () -> Int): () -> Int = underlying
  fun onSetAttributeRecordId(underlying: (fileId: Int, recordId: Int) -> Unit): (fileId: Int, recordId: Int) -> Unit = underlying
  fun onSetContentRecordId(underlying: (fileId: Int, recordId: Int) -> Boolean): (fileId: Int, recordId: Int) -> Boolean = underlying
  fun onSetParent(underlying: (fileId: Int, parentId: Int) -> Unit): (fileId: Int, parentId: Int) -> Unit = underlying
  fun onSetNameId(underlying: (fileId: Int, nameId: Int) -> Int): (fileId: Int, nameId: Int) -> Int = underlying
  fun onSetFlags(underlying: (fileId: Int, flags: Int) -> Boolean): (fileId: Int, flags: Int) -> Boolean = underlying
  fun onSetLength(underlying: (fileId: Int, length: Long) -> Boolean): (fileId: Int, length: Long) -> Boolean = underlying
  fun onSetTimestamp(underlying: (fileId: Int, timestamp: Long) -> Boolean): (fileId: Int, timestamp: Long) -> Boolean = underlying
  fun onMarkRecordAsModified(underlying: (fileId: Int) -> Unit): (fileId: Int) -> Unit = underlying
  fun onFillRecord(underlying: (fileId: Int, timestamp: Long, length: Long, flags: Int,
                                nameId: Int, parentId: Int, overwriteAttrRef: Boolean) -> Unit): (fileId: Int, timestamp: Long, length: Long, flags: Int, nameId: Int, parentId: Int, overwriteAttrRef: Boolean) -> Unit = underlying
  fun onCleanRecord(underlying: (fileId: Int) -> Unit): (fileId: Int) -> Unit = underlying
  fun onSetVersion(underlying: (version: Int) -> Unit): (version: Int) -> Unit = underlying
}

interface AttributesInterceptor : ConnectionInterceptor {
  fun onWriteAttribute(underlying: (connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute) -> AttributeOutputStream): (connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute) -> AttributeOutputStream = underlying
  fun onDeleteAttributes(underlying: (connection: PersistentFSConnection, fileId: Int) -> Unit): (connection: PersistentFSConnection, fileId: Int) -> Unit = underlying
  fun onSetVersion(underlying: (version: Int) -> Unit): (version: Int) -> Unit = underlying
}