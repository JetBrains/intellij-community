package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author erokhins
 *         <code>
 *         if (inputStr_1 == inputStr_2) {
 *         Hash.build(inputStr_1) == Hash.build(inputStr_2)
 *         }
 *         </code>
 */
public final class Hash {

  private static final int SHORT_HASH_LENGTH = 7;
  private static final int CAPABILITY = 5000;
  private static final Map<Hash, Hash> ourCache = new HashMap<Hash, Hash>(CAPABILITY);

  private static void clearMap() {
    if (ourCache.size() >= CAPABILITY - 5) {
      ourCache.clear();
    }
  }

  @NotNull
  public static Hash build(@NotNull String inputStr) {
    clearMap();
    byte[] data = buildData(inputStr);
    Hash newHash = new Hash(data);
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

  @NotNull
  private final byte[] data;
  private final int hashCode;

  private Hash(@NotNull byte[] hash) {
    this.data = hash;
    this.hashCode = Arrays.hashCode(hash);
  }

  public String toStrHash() {
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

  public boolean equals(@Nullable Object obj) {
    if (obj != null && obj.getClass() == Hash.class) {
      Hash hash = (Hash)obj;
      return hash.hashCode() == this.hashCode() && Arrays.equals(this.data, hash.data);
    }
    return false;
  }

  public int hashCode() {
    return hashCode;
  }

  public String toString() {
    return toStrHash();
  }

  public String toShortString() {
    String s = toStrHash();
    return s.substring(0, Math.min(s.length(), SHORT_HASH_LENGTH));
  }
}
