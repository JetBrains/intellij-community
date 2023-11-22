// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSContentAccessor
import com.intellij.util.hash.ContentHashEnumerator
import com.intellij.util.io.storage.IStorage
import com.intellij.util.io.storage.RecordIdIterator
import com.intellij.util.io.storage.RefCountingContentStorage
import com.intellij.util.io.storage.VFSContentStorage
import java.io.DataInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock


private val LOG = Logger.getInstance(ContentStorageAdapter::class.java)

/**
 * Adapter (RefCountingContentStorage + ContentHashEnumerator) -> VFSContentStorage
 */
class ContentStorageAdapter(contentStorage: RefCountingContentStorage,
                            hashesEnumeratorSupplier: ThrowableComputable<ContentHashEnumerator, IOException>) : VFSContentStorage {

  private val contentStorage = contentStorage
  private val hashesEnumerator: ContentHashEnumerator

  private val lock: ReadWriteLock = ReentrantReadWriteLock()


  //=========== monitoring: ===========
  //TODO RC: redirect values to OTel.Metrics, instead of logging them
  private var totalContentBytesStored: Long = 0
  private var totalContentBytesReused: Long = 0
  private var totalContentRecordsStored: Int = 0
  private var totalContentRecordsReused: Int = 0

  private var totalHashCalculationTimeNs: Long = 0


  init {
    val hashesEnumerator = hashesEnumeratorSupplier.compute()

    val hashRecordsCount = hashesEnumerator.recordsCount()
    val recordsCount = contentStorage.recordsCount
    if (hashRecordsCount != recordsCount) {
      LOG.warn("Content storage is not match content hash enumerator: " +
               "contents.records(=$recordsCount) != contentHashes.records(=$hashRecordsCount) -> trying rebuild hashesEnumerator from content storage")

      hashesEnumerator.closeAndClean()
      this.hashesEnumerator = tryRebuildHashesStorageByContentStorage(hashesEnumeratorSupplier)
      //MAYBE IDEA-334517: re-check contentHashStorage: is it really 'fixed'?
    }
    else {
      this.hashesEnumerator = hashesEnumerator
    }
  }

  @Throws(IOException::class)
  private fun tryRebuildHashesStorageByContentStorage(hashesEnumeratorSupplier: ThrowableComputable<ContentHashEnumerator, IOException>): ContentHashEnumerator {
    val recoveringHashesEnumerator = hashesEnumeratorSupplier.compute()
    try {
      fillHashesEnumeratorByContentStorage(contentStorage, recoveringHashesEnumerator)
      return recoveringHashesEnumerator
    }
    catch (t: Throwable) {
      recoveringHashesEnumerator.closeAndClean()
      throw t
    }
  }

  @Throws(IOException::class)
  private fun fillHashesEnumeratorByContentStorage(contentStorage: IStorage,
                                                   hashesEnumeratorToFill: ContentHashEnumerator) {
    //Try to fill hashesEnumerator from contentStorage records (and check contentIds match)
    // (along the way we also checks contentStorage is OK -- i.e. all the records could be read)
    val it = contentStorage.createRecordIdIterator()
    while (it.hasNextId()) {
      val contentRecordId = it.nextId()
      contentStorage.readStream(contentRecordId).use { stream ->
        val content = stream.readAllBytes()
        val hash = PersistentFSContentAccessor.calculateHash(content, 0, content.size)
        val contentHashId = hashesEnumeratorToFill.enumerate(hash)
        if (contentHashId != contentRecordId) {
          throw IOException(
            "Content enumerator recovery fails (content id: #$contentRecordId hashed to different id: #$contentHashId)"
          )
        }
      }
    }
  }


  @Throws(IOException::class)
  override fun createRecordIdIterator(): RecordIdIterator = lock.readLock().withLock {
    contentStorage.createRecordIdIterator()
  }

  @Throws(IOException::class)
  override fun isEmpty(): Boolean {
    return contentStorage.recordsCount == 0
  }

  @Throws(IOException::class)
  override fun getRecordsCount(): Int = lock.readLock().withLock {
    //.liveRecordsCount should be == .recordsCount, since we never remove the records -- but abstractly
    //.recordsCount is that we want here: monotonically increasing number
    contentStorage.recordsCount
  }

  @Throws(IOException::class)
  override fun getVersion(): Int = lock.readLock().withLock {
    contentStorage.version
  }

  @Throws(IOException::class)
  override fun setVersion(expectedVersion: Int) = lock.writeLock().withLock {
    contentStorage.version = expectedVersion
  }

  override fun checkRecord(recordId: Int, fastCheck: Boolean) = lock.readLock().withLock {
    val contentHash = hashesEnumerator.valueOf(recordId)
    if (contentHash == null) {
      throw IOException("contentHash[#$recordId] does not exist (null)! " +
                        "-> content hashes enumerator is inconsistent (broken?)")
    }

    if (!fastCheck) {
      //ensure storage has the record and content is readable (e.g. unzip-able)
      contentStorage.readStream(recordId).use { stream -> stream.readAllBytes() }
      //MAYBE RC: evaluate content hash from stream, and check == contentHash?
    }
  }

  override fun contentHash(recordId: Int): ByteArray = lock.readLock().withLock {
    return hashesEnumerator.valueOf(recordId)!!
  }

  @Throws(IOException::class)
  override fun readStream(recordId: Int): DataInputStream = lock.readLock().withLock {
    contentStorage.readStream(recordId)
  }


  @Throws(IOException::class)
  override fun storeRecord(contentBytes: ByteArraySequence): Int = lock.writeLock().withLock {
    val length = contentBytes.length()

    val hashStartedNs = System.nanoTime()
    val contentHash = PersistentFSContentAccessor.calculateHash(contentBytes)
    totalHashCalculationTimeNs += (System.nanoTime() - hashStartedNs)


    if ((totalContentRecordsStored and 0x3FFF) == 0) {
      LOG.info("Contents: " +
               "$totalContentRecordsStored records of ${totalContentBytesStored}b total were actually stored, " +
               "$totalContentRecordsReused records of ${totalContentBytesReused}b were reused, " +
               "for ${TimeUnit.NANOSECONDS.toSeconds(totalHashCalculationTimeNs)} ms spent on hashing")
    }

    val hashId = hashesEnumerator.enumerateEx(contentHash)

    if (hashId < 0) { //already known hash -> already stored content
      totalContentRecordsReused++
      totalContentBytesReused += length

      val alreadyExistingContentRecordId = -hashId
      return alreadyExistingContentRecordId
    }

    //unknown hash -> not-previously-seen content:
    val newContentRecordId = contentStorage.acquireNewRecord()

    assert(hashId == newContentRecordId) {
      "Unexpected content storage modification: contentHashId=$hashId; newContentRecord=$newContentRecordId"
    }

    contentStorage.writeBytes(newContentRecordId, contentBytes,  /*fixedSize: */true)

    totalContentRecordsStored++
    totalContentBytesStored += length

    return newContentRecordId
  }


  //both contentStorage & hashesEnumerator are modified at same time, so enough to check one of them
  override fun isDirty(): Boolean = contentStorage.isDirty

  @Throws(IOException::class)
  override fun force() {
    hashesEnumerator.force()
    contentStorage.force()
  }

  @Throws(IOException::class)
  override fun close() {
    hashesEnumerator.close()
    Disposer.dispose(contentStorage)
  }

  @Throws(IOException::class)
  override fun closeAndClean() {
    hashesEnumerator.closeAndClean()
    contentStorage.closeAndClean()
  }
}