/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore.type;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mvstore.KeyManager;
import org.jetbrains.mvstore.MVMap;
import org.jetbrains.mvstore.ObjectKeyManager;

import java.util.Arrays;

public final class FixedByteArrayDataType implements KeyableDataType<byte[]> {
  private final int length;

  public FixedByteArrayDataType(int length) {
    this.length = length;
  }

  @Override
  public boolean equals(byte[] a, byte[] b) {
    return Arrays.equals(a, b);
  }

  @Override
  public int compare(byte[] a, byte[] b) {
    return Arrays.compare(a, b);
  }

  @Override
  public final byte[][] createStorage(int size) {
    return new byte[size][];
  }

  @Override
  public int getMemory(byte[] obj) {
    return length;
  }

  @Override
  public int getFixedMemory() {
    return length;
  }

  @Override
  public void read(ByteBuf buf, byte[][] storage, int len) {
    int readerIndex = buf.readerIndex();
    for (int i = 0; i < len; i++) {
      storage[i] = ByteBufUtil.getBytes(buf, readerIndex, length);
      readerIndex += length;
    }
    buf.readerIndex(readerIndex);
  }

  @Override
  public byte[] read(ByteBuf buf) {
    throw new IllegalStateException();
  }

  @Override
  public void write(ByteBuf buf, byte[] value) {
    assert value.length == length;
    buf.writeBytes(value);
  }

  @Override
  public @NotNull KeyManager<byte[]> createManager(ByteBuf buf, int count) {
    // cannot use PooledByteBufAllocator because not clear when to release it
    int dataLength = count * length;
    byte[] data = ByteBufUtil.getBytes(buf, buf.readerIndex(), dataLength);
    buf.readerIndex(buf.readerIndex() + dataLength);
    return new ContiguousByteArrayKeyManager(data, count, length);
  }
}

final class ContiguousByteArrayKeyManager implements KeyManager<byte[]> {
  private final int count;
  private final int keyLength;
  private final byte[] data;

  ContiguousByteArrayKeyManager(byte[] data, int count, int keyLength) {
    assert (keyLength * count) == data.length;
    this.data = data;
    this.count = count;
    this.keyLength = keyLength;
  }

  @Override
  public int getKeyCount() {
    return count;
  }

  @Override
  public byte[] getKey(int index) {
    return Arrays.copyOfRange(data, index * keyLength, index * keyLength + keyLength);
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public int binarySearch(byte[] key, MVMap<byte[], ?> map, int initialGuess) {
    int length = count;
    if (length == 0) {
      return -1;
    }

    int low = 0;
    int high = length - 1;
    // the cached index minus one, so that
    // for the first time (when cachedCompare is 0),
    // the default value is used
    int x = initialGuess - 1;
    if (x < 0 || x > high) {
      x = high >>> 1;
    }
    return binarySearch(key, low, high, x);
  }

  @SuppressWarnings("DuplicatedCode")
  private int binarySearch(byte[] key, int low, int high, int x) {
    byte[] data = this.data;
    while (low <= high) {
      int xOffset = x * keyLength;
      int compare = Arrays.compare(key, 0, keyLength, data, xOffset, xOffset + keyLength);
      if (compare > 0) {
        low = x + 1;
      }
      else if (compare < 0) {
        high = x - 1;
      }
      else {
        return x;
      }
      x = (low + high) >>> 1;
    }
    return -(low + 1);
  }

  @Override
  public ObjectKeyManager<byte[]> expandKeys(int extraKeyCount, byte[][] extraKeys, MVMap<byte[], ?> map) {
    int keyCount = count;
    byte[][] newKeys = new byte[keyCount + extraKeyCount][];
    for (int i = 0; i < keyCount; i++) {
      newKeys[i] = Arrays.copyOfRange(data, i * keyLength, i * keyLength + keyLength);
    }
    System.arraycopy(extraKeys, 0, newKeys, keyCount, extraKeyCount);
    return new ObjectKeyManager<>(newKeys, newKeys.length * keyLength);
  }

  @Override
  public KeyManager<byte[]> copy(int startIndex, int endIndex, MVMap<byte[], ?> map) {
    // copy creates mutable
    byte[][] copy = new byte[endIndex - startIndex][];
    for (int i = startIndex; i < endIndex; i++) {
      copy[i] = Arrays.copyOfRange(data, i * keyLength, i * keyLength + keyLength);
    }
    return new ObjectKeyManager<>(copy, copy.length * keyLength);
  }

  @Override
  public KeyManager<byte[]> insertKey(int index, byte[] key, MVMap<byte[], ?> map) {
    byte[][] copy = new byte[count + 1][];
    copy[index] = key;
    for (int i = 0; i < count; i++) {
      copy[i >= index ? (i + 1) : i] = Arrays.copyOfRange(data, i * keyLength, i * keyLength + keyLength);
    }
    return new ObjectKeyManager<>(copy, copy.length * keyLength);
  }

  @Override
  public KeyManager<byte[]> remove(int index, MVMap<byte[], ?> map) {
    byte[][] copy = new byte[count - 1][];
    for (int i = 0; i < count; i++) {
      if (i != index) {
        copy[i > index ? (i - 1) : i] = Arrays.copyOfRange(data, i * keyLength, i * keyLength + keyLength);
      }
    }
    return new ObjectKeyManager<>(copy, copy.length * keyLength);
  }

  @Override
  public void write(int count, MVMap<byte[], ?> map, ByteBuf buf) {
    buf.writeBytes(data);
  }

  @Override
  public int getSerializedDataSize() {
    return data.length;
  }
}
