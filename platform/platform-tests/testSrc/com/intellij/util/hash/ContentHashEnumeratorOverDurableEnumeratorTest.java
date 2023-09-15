// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.hash;

import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumerator;
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumeratorFactory;
import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
import com.intellij.util.io.dev.enumerator.KeyDescriptorEx;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;


class ContentHashEnumeratorOverDurableEnumeratorTest extends ContentHashEnumeratorTestBase {
  @TempDir
  private Path tempDir;

  @Override
  protected ContentHashEnumerator openEnumerator() throws IOException {
    Path storagePath = tempDir.resolve("enumerator_mmap");
    KeyDescriptorEx<byte[]> keyDescriptorEx = new KeyDescriptorEx<>() {
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
    };
    DurableEnumerator<byte[]> enumerator = DurableEnumeratorFactory.defaultWithDurableMap(keyDescriptorEx)
      .open(storagePath);
    return new ContentHashEnumeratorOverDurableEnumerator(enumerator);
  }
}