// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.DigestUtil;
import com.intellij.util.io.storage.IStorageDataOutput;
import com.intellij.util.io.storage.RefCountingContentStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class PersistentFSContentAccessor {
  private static final Logger LOG = Logger.getInstance(PersistentFSContentAccessor.class);

  private static final boolean USE_CONTENT_HASHES = true;

  private final @NotNull PersistentFSConnection connection;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  private long totalContents;
  private long totalReuses;
  private long time;
  private int contents;
  private int reuses;

  PersistentFSContentAccessor(@NotNull PersistentFSConnection connection) {
    this.connection = connection;
  }

  @Nullable
  DataInputStream readContent(int fileId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    lock.readLock().lock();
    try {
      int page = connection.getRecords().getContentRecordId(fileId);
      if (page == 0) return null;
      return readContentDirectly(page);
    }
    finally {
      lock.readLock().unlock();
    }
  }

  @NotNull
  DataInputStream readContentDirectly(int contentId) throws IOException {
    lock.readLock().lock();
    try {
      return connection.getContents().readStream(contentId);
    }
    finally {
      lock.readLock().unlock();
    }
  }

  void deleteContent(int fileId) throws IOException {
    lock.writeLock().lock();
    try {
      final int contentRecordId = connection.getRecords().getContentRecordId(fileId);
      if (contentRecordId != 0) {
        releaseContentRecord(contentRecordId);
      }
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  void releaseContentRecord(int contentRecordId) throws IOException {
    if (USE_CONTENT_HASHES) {
      return;
    }
    lock.writeLock().lock();
    try {
      RefCountingContentStorage contentStorage = connection.getContents();
      //IDEA-302595: Don't decrement counter if already 0.
      // Ideally, it is a bug if we ever reach here with refCount==0 -- attempt to release something
      // that was already released. But in practice, refCount mechanic currently is not smooth enough
      // to rely on the invariant 'contentStorage.refCount == count of contentId references across the
      // app'. Ref-counting is prone to unexpected app shutdowns, and with contentHashes introduction,
      // contentStorage works mostly as an append-only log anyway. So I see no reason to blow logs with
      // useless warnings -- better just make releaseContentRecord() idempotent, allowing to release
      // the same contentId as many times as one wants:
      final int refCount = contentStorage.getRefCount(contentRecordId);
      if (refCount > 0) {
        contentStorage.releaseRecord(contentRecordId);
      }
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  boolean writeContent(int fileId, @NotNull ByteArraySequence bytes, @SuppressWarnings("ParameterCanBeLocal") boolean fixedSize)
    throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    lock.writeLock().lock();
    try {
      PersistentFSConnection connection = this.connection;

      boolean modified;
      RefCountingContentStorage contentStorage = connection.getContents();

      int contentRecordId;
      if (USE_CONTENT_HASHES) {
        contentRecordId = findOrCreateContentRecord(bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength());

        modified = connection.getRecords().setContentRecordId(fileId, (contentRecordId > 0 ? contentRecordId : -contentRecordId));

        if (contentRecordId > 0) return modified;
        contentRecordId = -contentRecordId;
        fixedSize = true;
      }
      else {
        contentRecordId = connection.getRecords().getContentRecordId(fileId);
        if (contentRecordId == 0 || contentStorage.getRefCount(contentRecordId) > 1) {
          contentRecordId = contentStorage.acquireNewRecord();
          connection.getRecords().setContentRecordId(fileId, contentRecordId);
        }
      }

      contentStorage.writeBytes(contentRecordId, bytes, fixedSize);
      return true;
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  int allocateContentRecordAndStore(byte @NotNull [] bytes) throws IOException {
    lock.writeLock().lock();
    try {
      int recordId;
      if (USE_CONTENT_HASHES) {
        recordId = findOrCreateContentRecord(bytes, 0, bytes.length);
        if (recordId > 0) return recordId;
        recordId = -recordId;
      }
      else {
        recordId = connection.getContents().acquireNewRecord();
      }
      try (IStorageDataOutput output = connection.getContents().writeStream(recordId, true)) {
        output.write(bytes);
      }
      return recordId;
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  byte @Nullable [] getContentHash(int fileId) throws IOException {
    if (!USE_CONTENT_HASHES) return null;

    int contentId = connection.getRecords().getContentRecordId(fileId);
    return contentId <= 0 ? null : connection.getContentHashesEnumerator().valueOf(contentId);
  }

  /**
   * @return -recordId for newly created record, or recordId (>0) of existent record with matching hash
   */
  private int findOrCreateContentRecord(byte[] bytes, int offset, int length) throws IOException {
    assert USE_CONTENT_HASHES;

    long started = System.nanoTime();
    byte[] contentHash = calculateHash(bytes, offset, length);
    long done = System.nanoTime() - started;
    time += done;

    ++contents;
    totalContents += length;

    if ((contents & 0x3FFF) == 0) {
      LOG.info("Contents:" + contents + " of " + totalContents + ", reuses:" + reuses + " of " + totalReuses + " for " + time / 1000000);
    }

    ContentHashEnumerator hashesEnumerator = connection.getContentHashesEnumerator();
    int hashId = hashesEnumerator.enumerateEx(contentHash);

    if (hashId < 0) {//already known hash -> already stored content
      int contentRecordId = -hashId;
      ++reuses;
      connection.getContents().acquireRecord(contentRecordId);
      totalReuses += length;

      return contentRecordId;
    }
    else {
      int contentRecordId = hashId;
      int newRecord = connection.getContents().acquireNewRecord();
      //We assume we call contents.acquireNewRecord() when and only when
      //hashesEnumerator.enumerateEx() returns positive value (which means 'new hash')
      assert contentRecordId == newRecord
        : "Unexpected content storage modification: contentHashId=" + hashId + "; newContentRecord=" + newRecord;
      return -contentRecordId;
    }
  }

  int acquireContentRecord(int fileId) throws IOException {
    lock.writeLock().lock();
    try {
      int record = connection.getRecords().getContentRecordId(fileId);
      if (record > 0) {
        connection.getContents().acquireRecord(record);
      }
      return record;
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  private static @NotNull MessageDigest getContentHashDigest() {
    // MAYBE: consider replace it with sha-256? It is 20%-30% slower than sha1, but more secure.
    //        For now I think this is not really a priority -- but we may consider the move at some point.

    //        Git has been used sha1 for years, and still uses it for legacy repos.
    //        If the question is about casual collisions probability, then sha1 is still good -- typically
    //        we have much less data in our content storage than a typical git-repository has, so it is
    //        likely we'll be fine with sha1 collisions probability.
    //        Malicious collisions are another topic: it is easier to generate a collision for sha1 than for
    //        sha256, but it is still quite expensive. And if the Evil is able to inject a carefully-crafted
    //        source file in my IDE source tree -- this itself is a huge security breach, because I could run
    //        a code from that file -- which seems much more dangerous than hash-collision.
    return DigestUtil.sha1();
  }

  public static byte @NotNull [] calculateHash(byte[] bytes, int offset, int length) {
    // Probably we don't need to hash the length and "\0000".
    MessageDigest digest = getContentHashDigest();
    digest.update(String.valueOf(length).getBytes(StandardCharsets.UTF_8));
    digest.update("\u0000".getBytes(StandardCharsets.UTF_8));
    digest.update(bytes, offset, length);
    return digest.digest();
  }

  final class ContentOutputStream extends DataOutputStream {
    private final int myFileId;
    private final boolean myFixedSize;
    boolean myModified;

    ContentOutputStream(int fileId, boolean fixedSize) {
      super(new BufferExposingByteArrayOutputStream());
      PersistentFSConnection.ensureIdIsValid(fileId);
      myFileId = fileId;
      myFixedSize = fixedSize;
    }

    @Override
    public void close() throws IOException {
      super.close();
      final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;

      if (writeContent(myFileId, _out.toByteArraySequence(), myFixedSize)) {
        myModified = true;
      }
    }

    boolean isModified() {
      return myModified;
    }
  }
}
