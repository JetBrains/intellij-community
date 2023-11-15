// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.DigestUtil;
import com.intellij.util.io.storage.VFSContentStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class PersistentFSContentAccessor {
  private static final Logger LOG = Logger.getInstance(PersistentFSContentAccessor.class);

  private final @NotNull PersistentFSConnection connection;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  //=========== monitoring: ===========
  //TODO RC: redirect values to OTel.Metrics, instead of logging them
  private long totalContentBytesStored;
  private long totalContentBytesReused;
  private int totalContentRecordsStored;
  private int totalContentRecordsReused;

  private long totalHashCalculationTimeNs;


  PersistentFSContentAccessor(@NotNull PersistentFSConnection connection) {
    this.connection = connection;
  }

  @Nullable
  DataInputStream readContent(int fileId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    lock.readLock().lock();
    try {
      int contentId = connection.getRecords().getContentRecordId(fileId);
      if (contentId == 0) return null;
      return readContentByContentId(contentId);
    }
    finally {
      lock.readLock().unlock();
    }
  }

  @NotNull
  DataInputStream readContentByContentId(int contentId) throws IOException {
    lock.readLock().lock();
    try {
      return connection.getContents().readStream(contentId);
    }
    finally {
      lock.readLock().unlock();
    }
  }

  void writeContent(int fileId,
                    @NotNull ByteArraySequence content,
                    boolean fixedSizeHint)
    throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    PersistentFSRecordsStorage records = connection.getRecords();

    //we don't need a lock here: as soon, as .storeRecord() is thread-safe -- .setContentRecordId() provides
    // linearization point
    lock.writeLock().lock();
    try {
      int contentRecordId = connection.getContents().storeRecord(content, fixedSizeHint);

      records.setContentRecordId(fileId, contentRecordId);
    }
    finally {
      lock.writeLock().unlock();
    }
  }


  int allocateContentRecordAndStore(byte @NotNull [] content) throws IOException {
    lock.writeLock().lock();
    try {
      return connection.getContents().storeRecord(new ByteArraySequence(content), /*fixedSize: */ false);
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  @ApiStatus.Obsolete
  byte @Nullable [] getContentHash(int fileId) throws IOException {
    int contentId = connection.getRecords().getContentRecordId(fileId);
    if (contentId <= 0) {
      return null;
    }
    return connection.getContents().contentHash(contentId);
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
    digest.update(String.valueOf(length).getBytes(UTF_8));
    digest.update("\u0000".getBytes(UTF_8));
    digest.update(bytes, offset, length);
    return digest.digest();
  }

  final class ContentOutputStream extends DataOutputStream {
    private final int myFileId;
    private final boolean myFixedSize;

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

      writeContent(myFileId, _out.toByteArraySequence(), myFixedSize);
    }
  }

  //TODO RC: the group of methods below are remnants of the content-use-counting era. The idea of content records use-counting
  //         was abandoned long ago: today we use content (crypto)hashes to re-use content records, but we never delete
  //         content records -- hence no use-counting is needed, and I plan to remove the methods below, but I dont'
  //         know yet how to reorganize them

  /**
   * Method mark the content record of fileId to have +1 use, and return the apt contentRecordId.
   * Nowadays method just returns contentRecordId, since we abandon the use-counting idea -- I plan to remove
   * it entirely
   */
  int acquireContentRecord(int fileId) throws IOException {
    return connection.getRecords().getContentRecordId(fileId);
  }

  void deleteContent(int fileId) throws IOException {
    //nothing: content records kept forever
  }

  void releaseContentRecord(int contentRecordId) throws IOException {
    //nothing: content records kept forever
  }
}
