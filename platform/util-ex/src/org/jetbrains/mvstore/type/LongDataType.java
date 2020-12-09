/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore.type;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.integratedBinaryPacking.IntegratedBinaryPacking;
import org.jetbrains.integratedBinaryPacking.LongBitPacker;
import org.jetbrains.mvstore.DataUtil;
import org.jetbrains.mvstore.KeyManager;
import org.jetbrains.mvstore.MVMap;
import org.jetbrains.mvstore.MVStore;

/**
 * Class LongDataType.
 *
 * @author <a href='mailto:andrei.tokar@gmail.com'>Andrei Tokar</a>
 */
public final class LongDataType implements KeyableDataType<Long> {
  public static final LongDataType INSTANCE = new LongDataType();

  private LongDataType() {}

  @Override
  public int getMemory(Long obj) {
    return getFixedMemory();
  }

  @Override
  public int getFixedMemory() {
    return Long.BYTES;
  }

  @Override
  public void write(ByteBuf buff, Long data) {
    LongBitPacker.writeVar(buff, data);
  }

  @Override
  public Long read(ByteBuf buff) {
    return LongBitPacker.readVar(buff);
  }

  @Override
  public Long[] createStorage(int size) {
    return new Long[size];
  }

  @Override
  public int compare(Long one, Long two) {
    return Long.compare(one, two);
  }

  @Override
  public @NotNull KeyManager<Long> createEmptyManager(@NotNull MVMap<Long, ?> map) {
    return new LongKeyManager(LongKeyManager.EMPTY_ARRAY);
  }

  @Override
  public @NotNull KeyManager<Long> createManager(ByteBuf buf, int count) {
    return new LongKeyManager(buf, count);
  }

  @Override
  public boolean isGenericCompressionApplicable() {
    return false;
  }
}

final class LongKeyManager implements KeyManager<Long> {
  static final long[] EMPTY_ARRAY = new long[0];

  private final long[] keys;

  LongKeyManager(long[] keys) {
    if (MVStore.ASSERT_MODE) {
      for (int i = 1; i < keys.length; i++) {
        if (keys[i] <= keys[i - 1]) {
          throw new AssertionError(keys[i] + " <= " + keys[i - 1]);
        }
      }
    }
    this.keys = keys;
  }

  LongKeyManager(ByteBuf buf, int count) {
    if (count == 0) {
      keys = EMPTY_ARRAY;
      return;
    }

    keys = new long[count];
    //for (int i = 0; i < count; i++) {
    //  keys[i] = DataUtil.readVarInt(buf);
    //}

    int variableEncodingLength = count % IntegratedBinaryPacking.LONG_BLOCK_SIZE;
    long initValue;
    if (variableEncodingLength == 0) {
      initValue = 0;
    }
    else {
      LongBitPacker.decompressVariable(buf, keys, variableEncodingLength);
      initValue = keys[variableEncodingLength - 1];
      if (variableEncodingLength == count) {
        return;
      }
    }

    int compressedArrayLength = IntBitPacker.readVar(buf);
    long[] compressed = new long[compressedArrayLength];
    DataUtil.readLongArray(compressed, buf, compressedArrayLength);
    LongBitPacker.decompressIntegrated(compressed, 0, keys, variableEncodingLength, count, initValue);
  }

  @Override
  public int getKeyCount() {
    return keys.length;
  }

  @Override
  public Long getKey(int index) {
    return keys[index];
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public int binarySearch(Long key, MVMap<Long, ?> map, int initialGuess) {
    int length = keys.length;
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
    return binarySearch(key, keys, low, high, x);
  }

  @SuppressWarnings("DuplicatedCode")
  private static int binarySearch(long key, long[] storage, int low, int high, int x) {
    while (low <= high) {
      long midVal = storage[x];
      if (key > midVal) {
        low = x + 1;
      }
      else if (key < midVal) {
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
  public LongKeyManager expandKeys(int extraKeyCount, Long[] extraKeys, MVMap<Long, ?> map) {
    int keyCount = keys.length;
    long[] newKeys = new long[keyCount + extraKeyCount];
    System.arraycopy(keys, 0, newKeys, 0, keyCount);
    System.arraycopy(extraKeys, 0, newKeys, keyCount, extraKeyCount);
    return new LongKeyManager(newKeys);
  }

  @Override
  public KeyManager<Long> copy(int startIndex, int endIndex, MVMap<Long, ?> map) {
    long[] newKeys = new long[endIndex - startIndex];
    System.arraycopy(keys, startIndex, newKeys, 0, newKeys.length);
    return new LongKeyManager(newKeys);
  }

  @Override
  public KeyManager<Long> insertKey(int index, Long key, MVMap<Long, ?> map) {
    int keyCount = keys.length;
    assert index <= keyCount : index + " > " + keyCount;
    long[] newKeys = new long[keyCount + 1];
    DataUtil.copyWithGap(keys, newKeys, keyCount, index);

    newKeys[index] = key;
    return new LongKeyManager(newKeys);
  }

  @Override
  public KeyManager<Long> remove(int index, MVMap<Long, ?> map) {
    int keyCount = keys.length;
    if (keyCount == 1) {
      return new LongKeyManager(EMPTY_ARRAY);
    }
    else {
      long[] newKeys = new long[keyCount - 1];
      DataUtil.copyExcept(keys, newKeys, keyCount, index);
      return new LongKeyManager(newKeys);
    }
  }

  @Override
  public void write(int count, MVMap<Long, ?> map, ByteBuf buf) {
    if (count == 0) {
      return;
    }

    //for (int key : keys) {
    //  DataUtil.writeVarInt(buf, key);
    //}

    int variableEncodingLength = count % IntegratedBinaryPacking.LONG_BLOCK_SIZE;
    long initValue;
    if (variableEncodingLength == 0) {
      initValue = 0;
    }
    else {
      LongBitPacker.compressVariable(keys, 0, variableEncodingLength, buf);
      if (count == variableEncodingLength) {
        return;
      }

      initValue = keys[variableEncodingLength - 1];
    }

    long[] compressed = new long[IntegratedBinaryPacking.estimateCompressedArrayLength(keys, variableEncodingLength, count, initValue)];
    int compressedArrayLength = LongBitPacker.compressIntegrated(keys, variableEncodingLength, count, compressed, initValue);
    LongBitPacker.writeVar(buf, compressedArrayLength);
    DataUtil.writeLongArray(compressed, buf, compressedArrayLength);
  }

  @Override
  public int getSerializedDataSize() {
    int count = keys.length;
    if (count == 0) {
      return 0;
    }

    int variableEncodingLength = count % IntegratedBinaryPacking.LONG_BLOCK_SIZE;
    int result = variableEncodingLength * DataUtil.VAR_LONG_MAX_SIZE;
    if (count == variableEncodingLength) {
      return result;
    }
    return result + IntegratedBinaryPacking.estimateCompressedArrayLength(keys, variableEncodingLength, count, variableEncodingLength == 0 ? 0 : keys[variableEncodingLength - 1]);
  }
}