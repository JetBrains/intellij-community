// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.CompressionUtil;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.DigestUtil;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.io.storage.AbstractStorage;
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
  private final boolean myUseContentHashes;
  private final PersistentFSConnection myFSConnection;
  private final ReadWriteLock myLock = new ReentrantReadWriteLock();
  private long totalContents;
  private long totalReuses;
  private long time;
  private int contents;
  private int reuses;

  PersistentFSContentAccessor(boolean useContentHashes, @NotNull PersistentFSConnection connection) {
    myUseContentHashes = useContentHashes;
    myFSConnection = connection;
  }

  @Nullable
  DataInputStream readContent(int fileId) throws IOException {
    myLock.readLock().lock();
    try {
      PersistentFSConnection.ensureIdIsValid(fileId);
      int page = myFSConnection.getRecords().getContentRecordId(fileId);
      if (page == 0) return null;
      return readContentDirectly(page);
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @NotNull
  DataInputStream readContentDirectly(int contentId) throws IOException {
    myLock.readLock().lock();
    try {
      DataInputStream stream = myFSConnection.getContents().readStream(contentId);
      if (FSRecords.useCompressionUtil) {
        byte[] bytes = CompressionUtil.readCompressed(stream);
        stream = new DataInputStream(new UnsyncByteArrayInputStream(bytes));
      }
      return stream;
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  void deleteContent(int fileId) throws IOException {
    myLock.writeLock().lock();
    try {
      final int contentRecordId = myFSConnection.getRecords().getContentRecordId(fileId);
      if (contentRecordId != 0) {
        //IDEA-302595: we really don't use refCounts for anything now, with contentHashes introduction
        //             contentStorage really works as an append-only log, with no defrag/GC. And also
        //             refCounting is prone to unexpected app shutdowns anyway.
        final int refCount = myFSConnection.getContents().getRefCount(contentRecordId);
        if (refCount > 0) {
          releaseContentRecord(contentRecordId);
        }
      }
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  void releaseContentRecord(int contentId) throws IOException {
    myLock.writeLock().lock();
    try {
      myFSConnection.getContents().releaseRecord(contentId);
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  boolean writeContent(int fileId, @NotNull ByteArraySequence bytes, boolean fixedSize) throws IOException {
    myLock.writeLock().lock();
    try {
      PersistentFSConnection connection = myFSConnection;
      PersistentFSConnection.ensureIdIsValid(fileId);

      boolean modified;
      RefCountingContentStorage contentStorage = connection.getContents();

      int contentRecordId;
      if (myUseContentHashes) {
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

      ByteArraySequence newBytes;
      if (FSRecords.useCompressionUtil) {
        BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
        try (DataOutputStream outputStream = new DataOutputStream(out)) {
          CompressionUtil.writeCompressed(outputStream, bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength());
        }
        newBytes = out.toByteArraySequence();
      }
      else {
        newBytes = bytes;
      }

      contentStorage.writeBytes(contentRecordId, newBytes, fixedSize);
      return true;
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  int allocateContentRecordAndStore(byte @NotNull [] bytes) throws IOException {
    myLock.writeLock().lock();
    try {
      int recordId;
      if (myUseContentHashes) {
        recordId = findOrCreateContentRecord(bytes, 0, bytes.length);
        if (recordId > 0) return recordId;
        recordId = -recordId;
      }
      else {
        recordId = myFSConnection.getContents().acquireNewRecord();
      }
      try (AbstractStorage.StorageDataOutput output = myFSConnection.getContents().writeStream(recordId, true)) {
        output.write(bytes);
      }
      return recordId;
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  byte @Nullable [] getContentHash(int fileId) throws IOException {
    if (!myUseContentHashes) return null;

    int contentId = myFSConnection.getRecords().getContentRecordId(fileId);
    return contentId <= 0 ? null : myFSConnection.getContentHashesEnumerator().valueOf(contentId);
  }

  /**
   * @return -recordId for newly created record, or recordId (>0) of existent record with matching hash
   */
  private int findOrCreateContentRecord(byte[] bytes, int offset, int length) throws IOException {
    assert myUseContentHashes;

    long started = System.nanoTime();
    byte[] contentHash = calculateHash(bytes, offset, length);
    long done = System.nanoTime() - started;
    time += done;

    ++contents;
    totalContents += length;

    if ((contents & 0x3FFF) == 0) {
      LOG.info("Contents:" + contents + " of " + totalContents + ", reuses:" + reuses + " of " + totalReuses + " for " + time / 1000000);
    }

    ContentHashEnumerator hashesEnumerator = myFSConnection.getContentHashesEnumerator();
    final int largestId = hashesEnumerator.getLargestId();
    int contentRecordId = hashesEnumerator.enumerate(contentHash);

    if (contentRecordId <= largestId) {
      ++reuses;
      myFSConnection.getContents().acquireRecord(contentRecordId);
      totalReuses += length;

      return contentRecordId;
    }
    else {
      int newRecord = myFSConnection.getContents().acquireNewRecord();
      assert contentRecordId == newRecord : "Unexpected content storage modification: contentRecordId=" +
                                            contentRecordId +
                                            "; newRecord=" +
                                            newRecord;

      return -contentRecordId;
    }
  }

  int acquireContentRecord(int fileId) throws IOException {
    myLock.writeLock().lock();
    try {
      int record = myFSConnection.getRecords().getContentRecordId(fileId);
      if (record > 0) {
        myFSConnection.getContents().acquireRecord(record);
      }
      return record;
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  void checkContentsStorageSanity(int fileId) throws IOException {
    myLock.readLock().lock();
    try {
      int recordId = myFSConnection.getRecords().getContentRecordId(fileId);
      assert recordId >= 0;
      if (recordId > 0) {
        myFSConnection.getContents().checkSanity(recordId);
      }
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @NotNull
  private static MessageDigest getContentHashDigest() {
    // TODO replace with sha-256
    return DigestUtil.sha1();
  }

  private static byte @NotNull [] calculateHash(byte[] bytes, int offset, int length) {
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

    ContentOutputStream(int fileId, boolean readOnly) {
      super(new BufferExposingByteArrayOutputStream());
      PersistentFSConnection.ensureIdIsValid(fileId);
      myFileId = fileId;
      myFixedSize = readOnly;
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
