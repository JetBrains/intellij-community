// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.ContentTooBigException;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.DigestUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import static com.intellij.util.io.blobstorage.StreamlinedBlobStorage.NULL_ID;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApiStatus.Internal
public final class PersistentFSContentAccessor {

  private final @NotNull PersistentFSConnection connection;

  PersistentFSContentAccessor(@NotNull PersistentFSConnection connection) {
    this.connection = connection;
  }

  @Nullable
  InputStream readContent(int fileId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    int contentId = connection.records().getContentRecordId(fileId);
    if (contentId == 0) return null;
    return readContentByContentId(contentId);
  }

  @NotNull
  InputStream readContentByContentId(int contentId) throws IOException {
    return connection.contents().readStream(contentId);
  }

  void writeContent(int fileId,
                    @NotNull ByteArraySequence content,
                    boolean fixedSizeHint) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    PersistentFSRecordsStorage records = connection.records();

    int contentRecordId = writeContentRecord(content);

    records.setContentRecordId(fileId, contentRecordId);
  }


  /**
   * Stores content and return contentRecordId, by which content could be later retrieved.
   * If the same content (bytes) was already stored -- method could return id of already existing record, without allocating
   * & storing new record.
   */
  int writeContentRecord(@NotNull ByteArraySequence content) throws IOException, ContentTooBigException {
    return connection.contents().storeRecord(content);
  }

  @ApiStatus.Obsolete
  byte @Nullable [] getContentHash(int fileId) throws IOException {
    int contentId = connection.records().getContentRecordId(fileId);
    if (contentId <= NULL_ID) {
      return null;
    }
    return connection.contents().contentHash(contentId);
  }

  private static @NotNull MessageDigest contentHashDigest() {
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

  public static byte @NotNull [] calculateHash(@NotNull ByteArraySequence bytes) {
    return calculateHash(bytes.getInternalBuffer(), bytes.getOffset(), bytes.length());
  }

  public static byte @NotNull [] calculateHash(byte[] bytes, int offset, int length) {
    // Probably we don't need to hash the length and "\0000".
    MessageDigest digest = contentHashDigest();
    digest.update(String.valueOf(length).getBytes(UTF_8));
    digest.update("\u0000".getBytes(UTF_8));
    digest.update(bytes, offset, length);
    return digest.digest();
  }

  final class ContentOutputStream extends DataOutputStream {
    private final int fileId;
    private final boolean fixedSize;

    ContentOutputStream(int fileId, boolean fixedSize) {
      super(new BufferExposingByteArrayOutputStream());
      PersistentFSConnection.ensureIdIsValid(fileId);
      this.fileId = fileId;
      this.fixedSize = fixedSize;
    }

    @Override
    public void close() throws IOException {
      super.close();
      BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;

      writeContent(fileId, _out.toByteArraySequence(), fixedSize);
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
    return connection.records().getContentRecordId(fileId);
  }

  void deleteContent(int fileId) {
    //nothing: content records kept forever
  }

  void releaseContentRecord(int contentRecordId) throws IOException {
    //nothing: content records kept forever
  }
}
