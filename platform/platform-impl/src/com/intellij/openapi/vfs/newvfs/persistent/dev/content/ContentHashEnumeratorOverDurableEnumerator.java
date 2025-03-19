// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.content;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogFactory;
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogOverMMappedFile;
import com.intellij.platform.util.io.storages.enumerator.DurableEnumerator;
import com.intellij.platform.util.io.storages.enumerator.DurableEnumeratorFactory;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.CleanableStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Implementation on top of DurableEnumerator
 */
@ApiStatus.Internal
public class ContentHashEnumeratorOverDurableEnumerator implements ContentHashEnumerator, CleanableStorage {

  //TODO RC: current implementation relies on knowledge of append-only-log record format -- header size, page
  //         alignment: see HASH_RECORD_LENGTH, .enumeratorIdToHashId(), .hashIdToEnumeratorId() methods --
  //         which is hacky solution.
  //         But better solution is needed: basically, we need to return id=recordNo (1-based).
  //         It is not a problem to return .recordsCount(), but to to map (id=recordNo) back to the record
  //         offset in append-only log -- for that we need either to keep additional Map[int->int], or still use
  //         details of append-only-log binary layout, but somehow legalise that usage.

  //TODO RC: move DurableEnumerator to the module reachable from platform-indexing,
  //         We need to create DurableEnumerator here, with KeyDescriptorEx, 'cos it is all ContentHashEnumerator-specific


  /** Length of hash record in DurableEnumerator: hash.length + 4 bytes of append-only-log's record header */
  private static final int HASH_RECORD_LENGTH = (SIGNATURE_LENGTH + 4);

  private final DurableEnumerator<byte[]> enumerator;
  private final int pageSize;

  public static ContentHashEnumeratorOverDurableEnumerator open(@NotNull Path storagePath) throws IOException {
    int pageSize = 1 << 20;
    return new ContentHashEnumeratorOverDurableEnumerator(
      DurableEnumeratorFactory.defaultWithDurableMap(ContentHashKeyDescriptor.INSTANCE)
        .rebuildMapIfInconsistent(true)
        .valuesLogFactory(AppendOnlyLogFactory.withDefaults().pageSize(pageSize))
        .open(storagePath),
      pageSize
    );
  }

  private ContentHashEnumeratorOverDurableEnumerator(@NotNull DurableEnumerator<byte[]> enumerator,
                                                     int enumeratorPageSize) {
    this.enumerator = enumerator;
    this.pageSize = enumeratorPageSize;
  }

  @Override
  public int enumerate(byte @NotNull [] hash) throws IOException {
    checkValidHash(hash);

    int id = enumerator.enumerate(hash);
    return enumeratorIdToHashId(id);
  }

  @Override
  public int tryEnumerate(byte @Nullable [] hash) throws IOException {
    checkValidHash(hash);

    int id = enumerator.tryEnumerate(hash);
    if (id == NULL_ID) {
      return id;
    }
    return enumeratorIdToHashId(id);
  }

  @Override
  public int enumerateEx(byte @NotNull [] hash) throws IOException {
    int id = tryEnumerate(hash);
    if (id != NULL_ID) {
      return -id;
    }
    return enumerate(hash);
  }

  @Override
  public byte[] valueOf(int hashId) throws IOException {
    return enumerator.valueOf(hashIdToEnumeratorId(hashId));
  }

  @Override
  public boolean forEach(@NotNull ValueReader<? super byte[]> reader) throws IOException {
    return enumerator.forEach(
      (enumeratorId, hash) -> reader.read(enumeratorIdToHashId(enumeratorId), hash)
    );
  }

  @Override
  public int recordsCount() throws IOException {
    return enumerator.recordsCount();
  }

  @Override
  public boolean isDirty() {
    return enumerator.isDirty();
  }

  @Override
  public void force() throws IOException {
    enumerator.force();
  }

  @Override
  public void close() throws IOException {
    enumerator.close();
  }

  @Override
  public void closeAndClean() throws IOException {
    enumerator.closeAndClean();
  }

  /** hashId = (0, 1, 2 ... ) */
  @VisibleForTesting
  public int enumeratorIdToHashId(int enumeratorId) {
    if (enumeratorId == NULL_ID) {
      return NULL_ID;
    }

    int logHeaderSize = AppendOnlyLogOverMMappedFile.HeaderLayout.HEADER_SIZE;
    long offset = (((long)enumeratorId - 1) << 2) + logHeaderSize;

    int pageNo = (int)(offset / pageSize);
    if (pageNo == 0) {
      return (int)((offset - logHeaderSize) / HASH_RECORD_LENGTH + 1);
    }
    else {
      int offsetOnPage = (int)(offset % pageSize);
      int recordsOnPage = pageSize / HASH_RECORD_LENGTH;
      int recordsOnFirstPage = (pageSize - logHeaderSize) / HASH_RECORD_LENGTH;
      return recordsOnPage * (pageNo - 1)
             + recordsOnFirstPage
             + offsetOnPage / HASH_RECORD_LENGTH
             + 1;
    }
  }

  @VisibleForTesting
  public int hashIdToEnumeratorId(int hashId) {
    if (hashId == NULL_ID) {
      return NULL_ID;
    }
    int recordNo = hashId - 1;

    int logHeaderSize = AppendOnlyLogOverMMappedFile.HeaderLayout.HEADER_SIZE;
    int recordsOnPage = pageSize / HASH_RECORD_LENGTH;
    int recordsOnFirstPage = (pageSize - logHeaderSize) / HASH_RECORD_LENGTH;

    if (recordNo < recordsOnFirstPage) {
      return ((recordNo * HASH_RECORD_LENGTH) >> 2) + 1;
    }
    else {
      int pageNo = (recordNo - recordsOnFirstPage) / recordsOnPage + 1;
      int recordOnPage = (recordNo - recordsOnFirstPage) % recordsOnPage;

      return (int)((((pageNo * (long)pageSize - logHeaderSize) + recordOnPage * HASH_RECORD_LENGTH) >> 2) + 1);
    }
  }

  private static void checkValidHash(byte[] hash) {
    if (hash != null) {
      if (hash.length != SIGNATURE_LENGTH) {
        throw new IllegalArgumentException("hash.length(=" + hash.length + ") must be " + SIGNATURE_LENGTH);
      }
    }
  }

  /** Content hash (cryptographic hash of file content, byte[]) descriptor. */
  public static class ContentHashKeyDescriptor implements KeyDescriptorEx<byte[]> {
    public static final ContentHashKeyDescriptor INSTANCE = new ContentHashKeyDescriptor();

    private ContentHashKeyDescriptor() {
    }

    @Override
    public int getHashCode(byte[] contentHash) {
      int hashCode = 0; // take first 4 bytes, this should be good enough hash given we reference git revisions with 7-8 hex digits
      for (int i = 0; i < 4; i++) {
        hashCode = (hashCode << 8) + (contentHash[i] & 0xFF);
      }
      return hashCode;
    }

    @Override
    public boolean isEqual(byte[] hash1,
                           byte[] hash2) {
      return Arrays.equals(hash1, hash2);
    }

    @Override
    public KnownSizeRecordWriter writerFor(byte @NotNull [] hash) {
      return DataExternalizerEx.fromBytes(hash);
    }

    @Override
    public byte[] read(@NotNull ByteBuffer input) throws IOException {
      byte[] hash = new byte[SIGNATURE_LENGTH];
      input.get(hash);
      return hash;
    }
  }
}
