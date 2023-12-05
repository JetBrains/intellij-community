// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.intercept

import com.intellij.openapi.vfs.newvfs.AttributeOutputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSConnection
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorage
import com.intellij.openapi.vfs.newvfs.persistent.VFSAttributesStorage
import com.intellij.util.io.storage.VFSContentStorage

object InterceptorInjection {
  // leftmost interceptor in the list is the outer interceptor
  private fun <F, T> List<T>.intercept(lambda: F, transformer: (T, F) -> F): F = foldRight(lambda, transformer)

  fun injectInContents(storage: VFSContentStorage,
                       interceptors: List<ContentsInterceptor>): VFSContentStorage = interceptors.run {
    if (isEmpty()) {
      return storage
    }

    //val writeBytes = intercept(storage::writeBytes, ContentsInterceptor::onWriteBytes)
    //val writeStream1: (record: Int) -> IStorageDataOutput = intercept(storage::writeStream, ContentsInterceptor::onWriteStream)
    //val writeStream2: (record: Int, fixedSize: Boolean) -> IStorageDataOutput = intercept(storage::writeStream,
    //                                                                                      ContentsInterceptor::onWriteStream)
    //val appendStream = intercept(storage::appendStream, ContentsInterceptor::onAppendStream)
    //val replaceBytes = intercept(storage::replaceBytes, ContentsInterceptor::onReplaceBytes)
    //val acquireNewRecord = intercept(storage::createNewRecord, ContentsInterceptor::onAcquireNewRecord)
    //val acquireRecord = intercept(storage::acquireRecord, ContentsInterceptor::onAcquireRecord)
    //val releaseRecord = intercept(storage::releaseRecord, ContentsInterceptor::onReleaseRecord)
    //FIXME: intercept storeRecord
    val setVersion = intercept(storage::setVersion, ContentsInterceptor::onSetVersion)

    object : VFSContentStorage by storage {

      //override fun writeBytes(recordId: Int, bytes: ByteArraySequence, fixedSize: Boolean) = writeBytes(recordId, bytes, fixedSize)

      //override fun writeStream(record: Int): IStorageDataOutput = writeStream1(record)

      //override fun writeStream(recordId: Int, fixedSize: Boolean): IStorageDataOutput = writeStream2(recordId, fixedSize)

      //override fun appendStream(record: Int): IAppenderStream = appendStream(record)

      //override fun replaceBytes(record: Int, offset: Int, bytes: ByteArraySequence) = replaceBytes(record, offset, bytes)

      //override fun createNewRecord(): Int = acquireNewRecord()

      //override fun acquireRecord(record: Int) = acquireRecord(record)

      //override fun releaseRecord(record: Int) = releaseRecord(record)

      override fun setVersion(expectedVersion: Int) = setVersion(expectedVersion)
    }
  }

  fun injectInAttributes(storage: VFSAttributesStorage,
                         interceptors: List<AttributesInterceptor>): VFSAttributesStorage = interceptors.run {
    if (isEmpty()) {
      return storage
    }

    val writeAttribute = intercept(storage::writeAttribute, AttributesInterceptor::onWriteAttribute)
    val deleteAttributes = intercept(storage::deleteAttributes, AttributesInterceptor::onDeleteAttributes)
    val setVersion = intercept(storage::setVersion, AttributesInterceptor::onSetVersion)

    object : VFSAttributesStorage by storage {
      override fun deleteAttributes(connection: PersistentFSConnection, fileId: Int) = deleteAttributes(connection, fileId)

      override fun writeAttribute(connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute): AttributeOutputStream =
        writeAttribute(connection, fileId, attribute)

      override fun setVersion(version: Int) = setVersion(version)
    }
  }

  fun injectInRecords(storage: PersistentFSRecordsStorage,
                      interceptors: List<RecordsInterceptor>): PersistentFSRecordsStorage = interceptors.run {
    if (isEmpty()) {
      return storage
    }

    val allocateRecord = intercept(storage::allocateRecord, RecordsInterceptor::onAllocateRecord)
    val setAttributeRecordId = intercept(storage::setAttributeRecordId, RecordsInterceptor::onSetAttributeRecordId)
    val setParent = intercept(storage::setParent, RecordsInterceptor::onSetParent)
    val updateNameId = intercept(storage::updateNameId, RecordsInterceptor::onUpdateNameId)
    val setFlags = intercept(storage::setFlags, RecordsInterceptor::onSetFlags)
    val setLength = intercept(storage::setLength, RecordsInterceptor::onSetLength)
    val setTimestamp = intercept(storage::setTimestamp, RecordsInterceptor::onSetTimestamp)
    val markRecordAsModified = intercept(storage::markRecordAsModified, RecordsInterceptor::onMarkRecordAsModified)
    val setContentRecordId = intercept(storage::setContentRecordId, RecordsInterceptor::onSetContentRecordId)
    val fillRecord = intercept(storage::fillRecord, RecordsInterceptor::onFillRecord)
    val cleanRecord = intercept(storage::cleanRecord, RecordsInterceptor::onCleanRecord)
    val setVersion = intercept(storage::setVersion, RecordsInterceptor::onSetVersion)

    object : PersistentFSRecordsStorage by storage {
      override fun allocateRecord(): Int = allocateRecord()

      override fun setAttributeRecordId(fileId: Int, recordId: Int) = setAttributeRecordId(fileId, recordId)

      override fun setParent(fileId: Int, parentId: Int) = setParent(fileId, parentId)

      override fun updateNameId(fileId: Int, nameId: Int): Int = updateNameId(fileId, nameId)

      override fun setFlags(fileId: Int, flags: Int): Boolean = setFlags(fileId, flags)

      override fun setLength(fileId: Int, length: Long): Boolean = setLength(fileId, length)

      override fun setTimestamp(fileId: Int, timestamp: Long): Boolean = setTimestamp(fileId, timestamp)

      override fun markRecordAsModified(fileId: Int) = markRecordAsModified(fileId)

      override fun setContentRecordId(fileId: Int, recordId: Int): Boolean = setContentRecordId(fileId, recordId)

      override fun fillRecord(fileId: Int,
                              timestamp: Long,
                              length: Long,
                              flags: Int,
                              nameId: Int,
                              parentId: Int,
                              overwriteAttrRef: Boolean) = fillRecord(fileId, timestamp, length, flags, nameId, parentId, overwriteAttrRef)

      override fun cleanRecord(fileId: Int) = cleanRecord(fileId)

      override fun setVersion(version: Int) = setVersion(version)
    }
  }
}