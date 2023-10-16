// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogFactory;
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumerator;
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumeratorFactory;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
import com.intellij.util.io.dev.enumerator.KeyDescriptorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Implementation on top of DurableEnumerator
 */
public class ContentHashEnumeratorOverDurableEnumerator implements ContentHashEnumerator, CleanableStorage {

  //TODO RC: current implementation relies on knowledge of append-only-log record format (header size/alignment:
  //         HASH_RECORD_LENGTH). This is a) hacky, and b) not strictly correct: relation
  //         [enumeratedId = recordNo * HASH_RECORD_LENGTH] breaks on page-border, because
  //         [pageSize % HASH_RECORD_LENGTH != 0] -- so there is a padding record at the end of each page.
  //         Right now I 'fixed' the issue by setting huge page size (128Mb) -- that postpones the issue
  //         until >5M content hashes -- probably, enough for most use-cases.
  //         But better solution is needed: basically, we need to return id=recordNo (1-based).
  //         It is not a problem to return .recordsCount(), but to to map (id=recordNo) back to the record
  //         offset in append-only log -- for that we need to re-assemble DurableEnumerator, i.e. implement
  //         ContentHashEnumeratorOverDurableEnumerator not over out-of-box DurableEnumerator, but over
  //         (append-only-log + multimap), with different id-assigning strategy

  //TODO RC: move DurableEnumerator to the module reachable from platform-indexing,
  //         We need to create DurableEnumerator here, with KeyDescriptorEx, 'cos it is all ContentHashEnumerator-specific


  /** Length of hash record in DurableEnumerator: hash.length + 4 bytes of append-only-log's record header */
  private static final int HASH_RECORD_LENGTH = (SIGNATURE_LENGTH + 4);

  private final DurableEnumerator<byte[]> enumerator;

  public static ContentHashEnumeratorOverDurableEnumerator open(@NotNull Path storagePath) throws IOException {
    return new ContentHashEnumeratorOverDurableEnumerator(
      DurableEnumeratorFactory.defaultWithDurableMap(ContentHashKeyDescriptor.INSTANCE)
        .rebuildMapIfInconsistent(true)
        //FIXME RC: 128Mb page ~= 5M records before page-borders issues arise (see TODO at the class level)
        .valuesLogFactory(AppendOnlyLogFactory.withDefaults().pageSize(128 << 20))
        .open(storagePath)
    );
  }

  public ContentHashEnumeratorOverDurableEnumerator(@NotNull DurableEnumerator<byte[]> enumerator) {
    this.enumerator = enumerator;
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
    return ((ScannableDataEnumeratorEx<byte[]>)enumerator).forEach(
      (enumeratorId, hash) -> reader.read(enumeratorIdToHashId(enumeratorId), hash)
    );
  }

  @Override
  public int recordsCount() throws IOException {
    return ((ScannableDataEnumeratorEx)enumerator).recordsCount();
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

  private static int enumeratorIdToHashId(int enumeratorId) {
    if (enumeratorId == NULL_ID) {
      return NULL_ID;
    }
    if (((enumeratorId - 1) % HASH_RECORD_LENGTH) != 0) {
      throw new IllegalArgumentException("enumeratorId(=" + enumeratorId + ") must be (n * " + HASH_RECORD_LENGTH + " + 1)");
    }
    return (enumeratorId - 1) / HASH_RECORD_LENGTH + 1;
  }

  private static int hashIdToEnumeratorId(int hashId) {
    if (hashId == NULL_ID) {
      return NULL_ID;
    }
    return (hashId - 1) * HASH_RECORD_LENGTH + 1;
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
    public int hashCodeOf(byte[] contentHash) {
      int hashCode = 0; // take first 4 bytes, this should be good enough hash given we reference git revisions with 7-8 hex digits
      for (int i = 0; i < 4; i++) {
        hashCode = (hashCode << 8) + (contentHash[i] & 0xFF);
      }
      return hashCode;
    }

    @Override
    public boolean areEqual(byte[] hash1,
                            byte[] hash2) {
      return Arrays.equals(hash1, hash2);
    }

    @Override
    public long saveToLog(byte @NotNull [] hash,
                          @NotNull AppendOnlyLog log) throws IOException {
      return log.append(hash);
    }

    @Override
    public byte[] read(@NotNull ByteBuffer input) throws IOException {
      byte[] hash = new byte[ContentHashEnumerator.SIGNATURE_LENGTH];
      input.get(hash);
      return hash;
    }
  }
}
