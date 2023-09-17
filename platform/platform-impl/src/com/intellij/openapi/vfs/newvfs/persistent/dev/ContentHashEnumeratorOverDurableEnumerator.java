// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumeratorFactory;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.io.DurableDataEnumerator;
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
public class ContentHashEnumeratorOverDurableEnumerator implements ContentHashEnumerator {

  private final DurableDataEnumerator<byte[]> enumerator;

  public static ContentHashEnumeratorOverDurableEnumerator open(@NotNull Path storagePath) throws IOException {
    return new ContentHashEnumeratorOverDurableEnumerator(
      DurableEnumeratorFactory.defaultWithDurableMap(ContentHashKeyDescriptor.INSTANCE)
        .rebuildMapIfInconsistent(true)
        .open(storagePath)
    );
  }

  public ContentHashEnumeratorOverDurableEnumerator(@NotNull DurableDataEnumerator<byte[]> enumerator) {
    this.enumerator = enumerator;
  }

  //FIXME RC: current implementation is hacky, it relies on knowledge of append-only-log
  //          record format (header size/alignment). Something more abstract is needed

  //FIXME RC: move DurableEnumerator to the module reachable from platform-indexing,
  //          WE need to create DurableEnumerator here, with KeyDescriptorEx, 'cos it is all ContentHashEnumerator-specific

  @Override
  public int enumerate(byte @NotNull [] hash) throws IOException {
    int id = enumerator.enumerate(hash);
    return enumeratorIdToHashId(id);
  }

  private static int enumeratorIdToHashId(int enumeratorId) {
    if (enumeratorId == NULL_ID) {
      return NULL_ID;
    }
    return (enumeratorId - 1) / (SIGNATURE_LENGTH + 4) + 1;
  }

  private static int hashIdToEnumeratorId(int hashId) {
    if (hashId == NULL_ID) {
      return NULL_ID;
    }
    return (hashId - 1) * (SIGNATURE_LENGTH + 4) + 1;
  }

  @Override
  public int tryEnumerate(byte @Nullable [] value) throws IOException {
    int id = enumerator.tryEnumerate(value);
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
