/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * <p>The {@link Hash} implementation which stores a hash in a byte array thus saving some memory.</p>
 */
public class HashImpl implements Hash {

  private static final int SHORT_HASH_LENGTH = 7;

  @NotNull
  private final byte[] myData;
  private final int myHashCode;

  @NotNull
  public static Hash build(@NotNull String inputStr) {
    byte[] data = buildData(inputStr);
    return new HashImpl(data);
  }

  @NotNull
  private static byte[] buildData(@NotNull String inputStr) {
    // if length == 5, need 3 byte + 1 signal byte
    int length = inputStr.length();
    byte even = (byte)(length % 2);
    byte[] data = new byte[length / 2 + 1 + even];
    data[0] = even;
    try {
      for (int i = 0; i < length / 2; i++) {
        int k = Integer.parseInt(inputStr.substring(2 * i, 2 * i + 2), 16);
        data[i + 1] = (byte)(k - 128);
      }
      if (even == 1) {
        int k = Integer.parseInt(inputStr.substring(length - 1), 16);
        data[length / 2 + 1] = (byte)(k - 128);
      }
    }
    catch (NumberFormatException e) {
      throw new IllegalArgumentException("bad hash string: " + inputStr);
    }
    return data;
  }

  private HashImpl(@NotNull byte[] hash) {
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
    String s = asString();
    return s.substring(0, Math.min(s.length(), SHORT_HASH_LENGTH));
  }
}
