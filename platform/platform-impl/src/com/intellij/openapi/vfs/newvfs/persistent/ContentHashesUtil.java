// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;

public class ContentHashesUtil {
  public static final ThreadLocalCachedValue<MessageDigest> HASHER_CACHE = new ThreadLocalCachedValue<MessageDigest>() {
    @NotNull
    @Override
    public MessageDigest create() {
      return DigestUtil.sha1();
    }

    @Override
    protected void init(@NotNull MessageDigest value) {
      value.reset();
    }
  };

  public static final int SIGNATURE_LENGTH = 20;

  public static final Charset HASHER_CHARSET = StandardCharsets.UTF_8;

  public static byte[] calculateContentHash(byte[] bytes, int offset, int length) {
    MessageDigest digest = HASHER_CACHE.getValue();
    digest.reset();
    digest.update(String.valueOf(length).getBytes(HASHER_CHARSET));
    digest.update("\0".getBytes(HASHER_CHARSET));
    digest.update(bytes, offset, length);
    return digest.digest();
  }

  public static class HashEnumerator extends PersistentBTreeEnumerator<byte[]> {
    public HashEnumerator(@NotNull Path contentsHashesFile) throws IOException {
      this(contentsHashesFile, null);
    }

    public HashEnumerator(@NotNull Path contentsHashesFile, @Nullable PagedFileStorage.StorageLockContext storageLockContext) throws IOException {
      this(contentsHashesFile, new ContentHashesDescriptor(), 64 * 1024, storageLockContext);
    }

    private HashEnumerator(@NotNull Path file,
                           @NotNull KeyDescriptor<byte[]> dataDescriptor,
                           int initialSize,
                           @Nullable PagedFileStorage.StorageLockContext lockContext) throws IOException {
      super(file, dataDescriptor, initialSize, lockContext);
      LOG.assertTrue(dataDescriptor instanceof DifferentSerializableBytesImplyNonEqualityPolicy);
    }

    @Override
    protected int doWriteData(byte[] value) throws IOException {
      return super.doWriteData(value) / SIGNATURE_LENGTH;
    }

    @Override
    public int getLargestId() {
      return super.getLargestId() / SIGNATURE_LENGTH;
    }

    @Override
    protected boolean isKeyAtIndex(byte[] value, int idx) throws IOException {
      return super.isKeyAtIndex(value, addrToIndex(indexToAddr(idx) * SIGNATURE_LENGTH));
    }

    @Override
    public byte[] valueOf(int idx) throws IOException {
      return super.valueOf(addrToIndex(indexToAddr(idx) * SIGNATURE_LENGTH));
    }
  }

  private static class ContentHashesDescriptor implements KeyDescriptor<byte[]>, DifferentSerializableBytesImplyNonEqualityPolicy {
    @Override
    public void save(@NotNull DataOutput out, byte[] value) throws IOException {
      out.write(value);
    }

    @Override
    public byte[] read(@NotNull DataInput in) throws IOException {
      byte[] b = new byte[SIGNATURE_LENGTH];
      in.readFully(b);
      return b;
    }

    @Override
    public int getHashCode(byte[] value) {
      int hash = 0; // take first 4 bytes, this should be good enough hash given we reference git revisions with 7-8 hex digits
      for (int i = 0; i < 4; ++i) {
        hash = (hash << 8) + (value[i] & 0xFF);
      }
      return hash;
    }

    @Override
    public boolean isEqual(byte[] val1, byte[] val2) {
      return Arrays.equals(val1, val2);
    }
  }
}
