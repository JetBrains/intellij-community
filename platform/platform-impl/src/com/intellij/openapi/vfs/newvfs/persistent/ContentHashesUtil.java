// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;

public class ContentHashesUtil {
  public static final ThreadLocalCachedValue<MessageDigest> HASHER_CACHE = new ThreadLocalCachedValue<MessageDigest>() {
    @NotNull
    @Override
    public MessageDigest create() {
      return createHashDigest();
    }

    @Override
    protected void init(@NotNull MessageDigest value) {
      value.reset();
    }
  };

  @NotNull
  static MessageDigest createHashDigest() {
    return DigestUtil.sha1();
  }

  private static final int SIGNATURE_LENGTH = 20;

  public static class HashEnumerator extends PersistentBTreeEnumerator<byte[]> {
    public HashEnumerator(@NotNull File contentsHashesFile) throws IOException {
      this(contentsHashesFile, null);
    }

    public HashEnumerator(@NotNull File contentsHashesFile, @Nullable PagedFileStorage.StorageLockContext storageLockContext) throws IOException {
      super(contentsHashesFile, new ContentHashesDescriptor(), 64 * 1024, storageLockContext);
    }

    @Override
    protected int doWriteData(byte[] value) throws IOException {
      return super.doWriteData(value) / SIGNATURE_LENGTH;
    }

    @Override
    public int getLargestId() {
      return super.getLargestId() / SIGNATURE_LENGTH;
    }

    private final ThreadLocal<Boolean> myProcessingKeyAtIndex = new ThreadLocal<>();

    @Override
    protected boolean isKeyAtIndex(byte[] value, int idx) throws IOException {
      myProcessingKeyAtIndex.set(Boolean.TRUE);
      try {
        return super.isKeyAtIndex(value, addrToIndex(indexToAddr(idx)* SIGNATURE_LENGTH));
      } finally {
        myProcessingKeyAtIndex.set(null);
      }
    }

    @Override
    public byte[] valueOf(int idx) throws IOException {
      if (myProcessingKeyAtIndex.get() == Boolean.TRUE) return super.valueOf(idx);
      return super.valueOf(addrToIndex(indexToAddr(idx)* SIGNATURE_LENGTH));
    }

    @Override
    public int tryEnumerate(byte[] value) throws IOException {
      return super.tryEnumerate(value);
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
