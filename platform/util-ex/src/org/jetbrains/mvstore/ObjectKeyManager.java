/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mvstore.type.DataType;
import org.jetbrains.mvstore.type.KeyableDataType;

public final class ObjectKeyManager<K> implements KeyManager<K> {
  @SuppressWarnings("SSBasedInspection")
  public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  public static final ObjectKeyManager<?> EMPTY = new ObjectKeyManager<>();

  private final K[] keys;
  private final int serializedDataSize;

  public ObjectKeyManager(K [] keys, int serializedDataSize) {
    this.keys = keys;
    this.serializedDataSize = serializedDataSize;
  }

  public ObjectKeyManager(DataType<K> dataType, ByteBuf buf, int count) {
    keys = dataType.createStorage(count);
    int readableBytesBefore = buf.readableBytes();
    dataType.read(buf, keys, count);
    serializedDataSize = readableBytesBefore - buf.readableBytes();
  }

  private ObjectKeyManager() {
    //noinspection unchecked
    keys = (K[])EMPTY_OBJECT_ARRAY;
    serializedDataSize = 0;
  }

  ObjectKeyManager(@NotNull MVMap<K, ?> map, K[] keysBuffer, int available, int keyCount) {
    KeyableDataType<K> keyType = map.getKeyType();
    keys = keyType.createStorage(keyCount);
    System.arraycopy(keysBuffer, available, keys, 0, keyCount);
    serializedDataSize = keyType.getMemory(keys);
  }

  // create
  public ObjectKeyManager(@NotNull MVMap<K, ?> map, @NotNull K singleKey) {
    keys = map.getKeyType().createStorage(1);
    keys[0] = singleKey;
    serializedDataSize = map.getKeyType().getMemory(singleKey);
  }

  @Override
  public int getKeyCount() {
    return keys.length;
  }

  @Override
  public K getKey(int index) {
    return keys[index];
  }

  @Override
  public int binarySearch(K key, MVMap<K, ?> map, int cachedCompare) {
    int length = keys.length;
    return length == 0 ? -1 : binarySearch(key, keys, length, cachedCompare, map.getKeyType());
  }

  private static <T> int binarySearch(T key, T[] storage, int size, int initialGuess, KeyableDataType<T> keyType) {
    int low = 0;
    int high = size - 1;
    // the cached index minus one, so that
    // for the first time (when cachedCompare is 0),
    // the default value is used
    int x = initialGuess - 1;
    if (x < 0 || x > high) {
      x = high >>> 1;
    }

    while (low <= high) {
      int compare = keyType.compare(key, storage[x]);
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
    return ~low;
  }

  @Override
  public ObjectKeyManager<K> expandKeys(int extraKeyCount, K[] extraKeys, MVMap<K, ?> map) {
    int keyCount = keys.length;
    KeyableDataType<K> keyType = map.getKeyType();
    K[] newKeys = keyType.createStorage(keyCount + extraKeyCount);
    System.arraycopy(keys, 0, newKeys, 0, keyCount);
    System.arraycopy(extraKeys, 0, newKeys, keyCount, extraKeyCount);
    return new ObjectKeyManager<>(newKeys, serializedDataSize + keyType.getMemory(extraKeys));
  }

  @Override
  public KeyManager<K> copy(int startIndex, int endIndex, MVMap<K, ?> map) {
    KeyableDataType<K> keyType = map.getKeyType();
    int count = endIndex - startIndex;
    K[] newKeys = keyType.createStorage(count);
    System.arraycopy(keys, startIndex, newKeys, 0, count);
    return new ObjectKeyManager<>(newKeys, keyType.getMemory(newKeys));
  }

  @Override
  public ObjectKeyManager<K> insertKey(int index, K key, MVMap<K, ?> map) {
    int keyCount = keys.length;
    KeyableDataType<K> keyType = map.getKeyType();
    assert index <= keyCount : index + " > " + keyCount;

    K[] newKeys = keyType.createStorage(keyCount + 1);
    DataUtil.copyWithGap(keys, newKeys, keyCount, index);
    newKeys[index] = key;
    return new ObjectKeyManager<>(newKeys, serializedDataSize + keyType.getMemory(key));
  }

  @Override
  public KeyManager<K> remove(int index, MVMap<K, ?> map) {
    int keyCount = keys.length;
    if (keyCount == 1) {
      //noinspection unchecked
      return (KeyManager<K>)EMPTY;
    }

    KeyableDataType<K> keyType = map.getKeyType();
    int freedMemory = keyType.getMemory(keys[index]);
    K[] newKeys = keyType.createStorage(keyCount - 1);
    DataUtil.copyExcept(keys, newKeys, keyCount, index);
    return new ObjectKeyManager<>(newKeys, serializedDataSize - freedMemory);
  }

  @Override
  public void write(int count, MVMap<K, ?> map, ByteBuf buf) {
    map.getKeyType().write(buf, keys, count);
  }

  @Override
  public int getSerializedDataSize() {
    return serializedDataSize;
  }
}
