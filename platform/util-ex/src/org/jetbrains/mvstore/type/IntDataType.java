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
import org.jetbrains.mvstore.DataUtil;
import org.jetbrains.mvstore.KeyManager;
import org.jetbrains.mvstore.MVMap;
import org.jetbrains.mvstore.MVStore;

public final class IntDataType implements KeyableDataType<Integer> {
  public static final IntDataType INSTANCE = new IntDataType();

  private static final Integer[] EMPTY_ARRAY = new Integer[0];

  private IntDataType() {}

  @Override
  public @NotNull KeyManager<Integer> createEmptyManager(@NotNull MVMap<Integer, ?> map) {
    return new IntKeyManager(IntKeyManager.EMPTY_ARRAY);
  }

  @Override
  public @NotNull KeyManager<Integer> createManager(ByteBuf buf, int count) {
    return new IntKeyManager(buf, count);
  }

  @Override
  public int getMemory(Integer obj) {
    return getFixedMemory();
  }

  @Override
  public int getFixedMemory() {
    return Integer.BYTES;
  }

  @Override
  public void write(ByteBuf buf, Integer data) {
    IntBitPacker.writeVar(buf, data);
  }

  @Override
  public Integer read(ByteBuf buf) {
    return IntBitPacker.readVar(buf);
  }

  @Override
  public Integer[] createStorage(int size) {
    return size == 0 ? EMPTY_ARRAY : new Integer[size];
  }

  @Override
  public int compare(Integer one, Integer two) {
    return Integer.compare(one, two);
  }

  @Override
  public boolean isGenericCompressionApplicable() {
    return false;
  }
}

final class IntKeyManager implements KeyManager<Integer> {
  static final int[] EMPTY_ARRAY = new int[0];

  private final int[] keys;

  IntKeyManager(int[] keys) {
    if (MVStore.ASSERT_MODE) {
      for (int i = 1; i < keys.length; i++) {
        if (keys[i] <= keys[i - 1]) {
          throw new AssertionError(keys[i] + " <= " + keys[i - 1]);
        }
      }
    }
    this.keys = keys;
  }

  IntKeyManager(ByteBuf buf, int count) {
    if (count == 0) {
      keys = EMPTY_ARRAY;
      return;
    }

    keys = new int[count];
    //for (int i = 0; i < count; i++) {
    //  keys[i] = DataUtil.readVarInt(buf);
    //}

    int variableEncodingLength = count % IntegratedBinaryPacking.INT_BLOCK_SIZE;
    int initValue;
    if (variableEncodingLength == 0) {
      initValue = 0;
    }
    else {
      IntBitPacker.decompressVariable(buf, keys, variableEncodingLength);
      initValue = keys[variableEncodingLength - 1];
      if (variableEncodingLength == count) {
        return;
      }
    }

    int compressedArrayLength = IntBitPacker.readVar(buf);
    int[] compressed = new int[compressedArrayLength];
    DataUtil.readIntArray(compressed, buf, compressedArrayLength);
    IntBitPacker.decompressIntegrated(compressed, 0, keys, variableEncodingLength, count, initValue);
  }

  @Override
  public int getKeyCount() {
    return keys.length;
  }

  @Override
  public Integer getKey(int index) {
    return keys[index];
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public int binarySearch(Integer key, MVMap<Integer, ?> map, int initialGuess) {
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
  private static int binarySearch(int key, int[] storage, int low, int high, int x) {
    while (low <= high) {
      int midVal = storage[x];
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
  public IntKeyManager expandKeys(int extraKeyCount, Integer[] extraKeys, MVMap<Integer, ?> map) {
    int keyCount = keys.length;
    int[] newKeys = new int[keyCount + extraKeyCount];
    System.arraycopy(keys, 0, newKeys, 0, keyCount);
    System.arraycopy(extraKeys, 0, newKeys, keyCount, extraKeyCount);
    return new IntKeyManager(newKeys);
  }

  @Override
  public KeyManager<Integer> copy(int startIndex, int endIndex, MVMap<Integer, ?> map) {
    int[] newKeys = new int[endIndex - startIndex];
    System.arraycopy(keys, startIndex, newKeys, 0, newKeys.length);
    return new IntKeyManager(newKeys);
  }

  @Override
  public KeyManager<Integer> insertKey(int index, Integer key, MVMap<Integer, ?> map) {
    int keyCount = keys.length;
    assert index <= keyCount : index + " > " + keyCount;
    int[] newKeys = new int[keyCount + 1];
    DataUtil.copyWithGap(keys, newKeys, keyCount, index);

    newKeys[index] = key;
    return new IntKeyManager(newKeys);
  }

  @Override
  public KeyManager<Integer> remove(int index, MVMap<Integer, ?> map) {
    int keyCount = keys.length;
    if (keyCount == 1) {
      return new IntKeyManager(EMPTY_ARRAY);
    }
    else {
      int[] newKeys = new int[keyCount - 1];
      DataUtil.copyExcept(keys, newKeys, keyCount, index);
      return new IntKeyManager(newKeys);
    }
  }

  @Override
  public void write(int count, MVMap<Integer, ?> map, ByteBuf buf) {
    if (count == 0) {
      return;
    }

    //for (int key : keys) {
    //  DataUtil.writeVarInt(buf, key);
    //}

    int variableEncodingLength = count % IntegratedBinaryPacking.INT_BLOCK_SIZE;
    int initValue;
    if (variableEncodingLength == 0) {
      initValue = 0;
    }
    else {
      IntBitPacker.compressVariable(keys, 0, variableEncodingLength, buf);
      if (count == variableEncodingLength) {
        return;
      }

      initValue = keys[variableEncodingLength - 1];
    }

    int[] compressed = new int[IntegratedBinaryPacking.estimateCompressedArrayLength(keys, variableEncodingLength, count, initValue)];
    int compressedArrayLength = IntBitPacker.compressIntegrated(keys, variableEncodingLength, count, compressed, initValue);
    IntBitPacker.writeVar(buf, compressedArrayLength);
    DataUtil.writeIntArray(compressed, buf, compressedArrayLength);
  }

  @Override
  public int getSerializedDataSize() {
    int count = keys.length;
    if (count == 0) {
      return 0;
    }
    int variableEncodingLength = count % IntegratedBinaryPacking.INT_BLOCK_SIZE;
    int result = variableEncodingLength * DataUtil.VAR_INT_MAX_SIZE;
    if (count == variableEncodingLength) {
      return result;
    }
    return result + IntegratedBinaryPacking.estimateCompressedArrayLength(keys, variableEncodingLength, count, variableEncodingLength == 0 ? 0 : keys[variableEncodingLength - 1]);
  }
}