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
import java.util.HashMap;
import java.util.Map;

/**
 * <p>The {@link Hash} implementation which stores a hash in a byte array thus saving some memory.</p>
 * <p>
 *   In addition to that, it hash a static cache which lets avoid creating multiple HashImpl objects for the same hash value:
 *   <code>
 *     if (inputStr_1 == inputStr_2) {
 *       Hash.build(inputStr_1) == Hash.build(inputStr_2)
 *     }
 *   </code>
 * </p>
 *
 * @author erokhins
 */
public class HashImpl implements Hash {

  private static final int SHORT_HASH_LENGTH = 7;
  private static final int CAPABILITY = 5000;
  private static final Map<Hash, Hash> ourCache = new HashMap<Hash, Hash>(CAPABILITY);

  @NotNull
  private final byte[] data;
  private final int hashCode;

  private static void clearMap() {
    if (ourCache.size() >= CAPABILITY - 5) {
      ourCache.clear();
    }
  }

  @NotNull
  public static Hash build(@NotNull String inputStr) {
    clearMap();
    byte[] data = buildData(inputStr);
    Hash newHash = new HashImpl(data);
    if (ourCache.containsKey(newHash)) {
      return ourCache.get(newHash);
    }
    else {
      ourCache.put(newHash, newHash);
    }
    return newHash;
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
    this.data = hash;
    this.hashCode = Arrays.hashCode(hash);
  }

  @NotNull
  @Override
  public String asString() {
    assert data.length > 0 : "bad length Hash.data";
    byte even = data[0];
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i < data.length; i++) {
      int k1 = (data[i] + 128) / 16;
      int k2 = (data[i] + 128) % 16;
      char c1 = Character.forDigit(k1, 16);
      char c2 = Character.forDigit(k2, 16);
      if (i == data.length - 1 && even == 1) {
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
    return (hashCode == that.hashCode && asString().equals(that.asString()));
  }

  public int hashCode() {
    return hashCode;
  }

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
