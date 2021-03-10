// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
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

public final class PersistentFSContentAccessor {
  private static final Logger LOG = Logger.getInstance(PersistentFSContentAccessor.class);

  private final boolean myUseContentHashes;

  PersistentFSContentAccessor(boolean useContentHashes) {
    myUseContentHashes = useContentHashes;
  }

  @Nullable
  ThrowableComputable<DataInputStream, IOException> readContent(int fileId, @NotNull PersistentFSConnection connection) {
    PersistentFSConnection.ensureIdIsValid(fileId);
    int page = connection.getRecords().getContentRecordId(fileId);
    if (page == 0) return null;
    return () -> {
      return readContentDirectly(page, connection);
    };
  }

  DataInputStream readContentDirectly(int contentId, @NotNull PersistentFSConnection connection) throws IOException {
    DataInputStream stream = connection.getContents().readStream(contentId);
    if (FSRecords.useCompressionUtil) {
      byte[] bytes = CompressionUtil.readCompressed(stream);
      stream = new DataInputStream(new UnsyncByteArrayInputStream(bytes));
    }

    return stream;
  }

  void deleteContent(int fileId, @NotNull PersistentFSConnection connection) throws IOException {
    int contentPage = connection.getRecords().getContentRecordId(fileId);
    if (contentPage != 0) {
      releaseContentRecord(contentPage, connection);
    }
  }

  void releaseContentRecord(int contentId, @NotNull PersistentFSConnection connection) throws IOException {
    connection.getContents().releaseRecord(contentId);
  }

  boolean writeContent(int fileId, @NotNull ByteArraySequence bytes, boolean fixedSize, @NotNull PersistentFSConnection connection) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);

    boolean modified = false;
    RefCountingContentStorage contentStorage = connection.getContents();

    int page;
    if (myUseContentHashes) {
      page = findOrCreateContentRecord(bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength(), connection);

      if (page < 0 || connection.getRecords().getContentRecordId(fileId) != page) {
        modified = true;
        int value = page > 0 ? page : -page;
        connection.getRecords().setContentRecordId(fileId, value);
      }

      int value = page > 0 ? page : -page;
      connection.getRecords().setContentRecordId(fileId, value);

      if (page > 0) return modified;
      page = -page;
      fixedSize = true;
    }
    else {
      page = connection.getRecords().getContentRecordId(fileId);
      if (page == 0 || contentStorage.getRefCount(page) > 1) {
        page = contentStorage.acquireNewRecord();
        connection.getRecords().setContentRecordId(fileId, page);
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

    contentStorage.writeBytes(page, newBytes, fixedSize);
    return true;
  }

  int allocateContentRecordAndStore(byte[] bytes, @NotNull PersistentFSConnection connection) throws IOException {
    int recordId;
    if (myUseContentHashes) {
      recordId = findOrCreateContentRecord(bytes, 0, bytes.length, connection);
      if (recordId > 0) return recordId;
      recordId = -recordId;
    }
    else {
      recordId = connection.getContents().acquireNewRecord();
    }
    try (AbstractStorage.StorageDataOutput output = connection.getContents().writeStream(recordId, true)) {
      output.write(bytes);
    }
    return recordId;
  }

  byte @Nullable [] getContentHash(int fileId, @NotNull PersistentFSConnection connection) throws IOException {
      if (!myUseContentHashes) return null;

      int contentId = connection.getRecords().getContentRecordId(fileId);
      return contentId <= 0 ? null : connection.getContentHashesEnumerator().valueOf(contentId);
  }

  private long totalContents;
  private long totalReuses;
  private long time;
  private int contents;

  private int reuses;

  private int findOrCreateContentRecord(byte[] bytes, int offset, int length, @NotNull PersistentFSConnection connection) throws IOException {
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

    ContentHashEnumerator hashesEnumerator = connection.getContentHashesEnumerator();
    final int largestId = hashesEnumerator.getLargestId();
    int page = hashesEnumerator.enumerate(contentHash);

    if (page <= largestId) {
      ++reuses;
      connection.getContents().acquireRecord(page);
      totalReuses += length;

      return page;
    }
    else {
      int newRecord = connection.getContents().acquireNewRecord();
      assert page == newRecord : "Unexpected content storage modification: page="+page+"; newRecord="+newRecord;

      return -page;
    }
  }

  int acquireContentRecord(int fileId, @NotNull PersistentFSConnection connection) {
    int record = connection.getRecords().getContentRecordId(fileId);
    if (record > 0) connection.getContents().acquireRecord(record);
    return record;
  }

  void checkContentsStorageSanity(int id, PersistentFSConnection connection) {
    int recordId = connection.getRecords().getContentRecordId(id);
    assert recordId >= 0;
    if (recordId > 0) {
      connection.getContents().checkSanity(recordId);
    }
  }

  @NotNull
  private static MessageDigest getContentHashDigest() {
    // TODO replace with sha-256
    return DigestUtil.sha1();
  }

  private static byte @NotNull[] calculateHash(byte[] bytes, int offset, int length) {
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
    @NotNull
    private final PersistentFSConnection myConnection;
    boolean myModified;

    ContentOutputStream(int fileId, boolean readOnly, @NotNull PersistentFSConnection connection) {
      super(new BufferExposingByteArrayOutputStream());
      PersistentFSConnection.ensureIdIsValid(fileId);
      myFileId = fileId;
      myFixedSize = readOnly;
      myConnection = connection;
    }

    @Override
    public void close() throws IOException {
      super.close();
      final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;

      if (writeContent(myFileId, _out.toByteArraySequence(), myFixedSize, myConnection)) {
        myModified = true;
      }
    }

    boolean isModified() {
      return myModified;
    }
  }
}
