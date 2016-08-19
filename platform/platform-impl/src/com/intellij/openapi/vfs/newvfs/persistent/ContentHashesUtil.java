/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.ThreadLocalCachedValue;
import com.intellij.util.io.DifferentSerializableBytesImplyNonEqualityPolicy;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.PersistentBTreeEnumerator;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Created by Maxim.Mossienko on 4/4/2014.
 */
public class ContentHashesUtil {
  public static final ThreadLocalCachedValue<MessageDigest> HASHER_CACHE = new ThreadLocalCachedValue<MessageDigest>() {
    @Override
    public MessageDigest create() {
      return createHashDigest();
    }

    @Override
    protected void init(MessageDigest value) {
      value.reset();
    }
  };

  public static MessageDigest createHashDigest() {
    try {
      return MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException ex) {
      assert false:"Every Java implementation should have SHA1 support"; // http://docs.oracle.com/javase/7/docs/api/java/security/MessageDigest.html
    }
    return null;
  }

  private static final int SIGNATURE_LENGTH = 20;

  public static class HashEnumerator extends PersistentBTreeEnumerator<byte[]> {
    public HashEnumerator(File contentsHashesFile, PagedFileStorage.StorageLockContext storageLockContext) throws IOException {
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
