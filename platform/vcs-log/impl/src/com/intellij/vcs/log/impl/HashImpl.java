// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * <p>The {@link Hash} implementation which stores a hash in a byte array thus saving some memory.</p>
 */
public final class HashImpl implements Hash {

  private static final int BASE = 16;

  private final byte @NotNull [] myData;
  private final int myHashCode;

  @NotNull
  public static Hash build(@NotNull String inputStr) {
    byte[] data = buildData(inputStr);
    assert data.length > 0 : "Can not build hash for string " + inputStr;
    return new HashImpl(data);
  }

  @NotNull
  public static Hash read(@NotNull DataInput in) throws IOException {
    int length = DataInputOutputUtil.readINT(in);
    if (length == 0) throw new IOException("Can not read hash: data length is zero");
    byte[] buf = new byte[length];
    in.readFully(buf);
    return new HashImpl(buf);
  }

  public void write(@NotNull DataOutput out) throws IOException {
    DataInputOutputUtil.writeINT(out, myData.length);
    out.write(myData);
  }

  private static byte @NotNull [] buildData(@NotNull String inputStr) {
    // if length == 5, need 3 byte + 1 signal byte
    int length = inputStr.length();
    byte even = (byte)(length % 2);
    byte[] data = new byte[length / 2 + 1 + even];
    data[0] = even;
    for (int i = 0; i < length / 2; i++) {
      int k = parseChar(inputStr, 2 * i) * BASE + parseChar(inputStr, 2 * i + 1);
      data[i + 1] = (byte)(k - 128);
    }
    if (even == 1) {
      int k = parseChar(inputStr, length - 1);
      data[length / 2 + 1] = (byte)(k - 128);
    }
    return data;
  }

  private static int parseChar(@NotNull String inputString, int index) {
    int k = Character.digit(inputString.charAt(index), BASE);
    if (k < 0) {
      throw new IllegalArgumentException("bad hash string: " + inputString);
    }
    return k;
  }

  private HashImpl(byte @NotNull [] hash) {
    myData = hash;
    myHashCode = Arrays.hashCode(hash);
  }

  @NotNull
  @Override
  public String asString() {
    assert myData.length > 0 : "bad length Hash.data";
    byte even = myData[0];
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i < myData.length; i++) {
      int k1 = (myData[i] + 128) / 16;
      int k2 = (myData[i] + 128) % 16;
      char c1 = Character.forDigit(k1, 16);
      char c2 = Character.forDigit(k2, 16);
      if (i == myData.length - 1 && even == 1) {
        sb.append(c2);
      }
      else {
        sb.append(c1).append(c2);
      }
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HashImpl that = (HashImpl)o;
    return myHashCode == that.myHashCode && Arrays.equals(myData, that.myData);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  @Override
  public String toString() {
    return asString();
  }

  @NotNull
  @Override
  public String toShortString() {
    return VcsLogUtil.getShortHash(asString());
  }
}
