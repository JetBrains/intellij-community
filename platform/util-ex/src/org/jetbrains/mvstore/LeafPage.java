/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.mvstore.type.DataType;
import org.jetbrains.mvstore.type.KeyableDataType;

import java.nio.ByteBuffer;

import static org.jetbrains.mvstore.DataUtil.PAGE_TYPE_LEAF;

final class LeafPage<K, V> extends Page<K, V> {
  /**
   * The storage for values.
   */
  private V[] values;

  LeafPage(MVMap<K, V> map, ByteBuf buf, long info, int chunkId) {
    super(map, info, buf, chunkId);
  }

  LeafPage(MVMap<K, V> map, KeyManager<K> keyManager, V[] values, int memory) {
    super(map, keyManager, memory);

    assert values.length == keyManager.getKeyCount();
    this.values = values;
  }

  /**
   * Create array for values storage.
   *
   * @param size number of entries
   * @return values array
   */
  private V[] createValueStorage(int size) {
      return map.getValueType().createStorage(size);
  }

  @Override
  public int getNodeType() {
    return PAGE_TYPE_LEAF;
  }

  @Override
  public Page<K, V> copy(MVMap<K, V> map) {
    return new LeafPage<>(map, keyManager, values, memory);
  }

  @Override
  public Page<K, V> getChildPage(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getChildPagePos(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V getValue(int index) {
    return values[index];
  }

  @Override
  public Page<K, V> split(int at, boolean left) {
    KeyManager<K> newKeys = left ? keyManager.copy(0, at, map) : keyManager.copy(at, keyManager.getKeyCount(), map);
    V[] newValues = map.getValueType().createStorage(newKeys.getKeyCount());
    System.arraycopy(values, left ? 0 : at, newValues, 0, newValues.length);
    return new LeafPage<>(map, newKeys, newValues, PAGE_MEMORY + map.getValueType().getMemory(newValues));
  }

  /**
   * Append additional key/value mappings to this Page.
   * New mappings suppose to be in correct key order.
   *
   * @param extraKeyCount number of mappings to be added
   * @param extraKeys to be added
   * @param extraValues to be added
   */
  public static <K, V> LeafPage<K, V> expand(LeafPage<K, V> source, int extraKeyCount, K[] extraKeys, V[] extraValues) {
    MVMap<K, V> map = source.map;

    int currentKeyCount = source.keyManager.getKeyCount();
    V[] newValues = source.createValueStorage(currentKeyCount + extraKeyCount);
    System.arraycopy(source.values, 0, newValues, 0, currentKeyCount);
    System.arraycopy(extraValues, 0, newValues, currentKeyCount, extraKeyCount);
    return new LeafPage<>(map, source.keyManager.expandKeys(extraKeyCount, extraKeys, map), newValues, source.memory + map.getValueType().getMemory(extraValues));
  }

  @Override
  public long getTotalCount() {
    return getKeyCount();
  }

  @Override
  long getCounts(int index) {
    throw new UnsupportedOperationException();
  }

  static <K, V> LeafPage<K, V> replaceValue(LeafPage<K, V> source, int index, V value) {
    V[] newValues = source.values.clone();
    newValues[index] = value;

    DataType<V> valueType = source.map.getValueType();
    return new LeafPage<>(source.map, source.keyManager, newValues, source.memory + (valueType.getMemory(value) - valueType.getMemory(source.values[index])));
  }

  static <K, V> LeafPage<K, V> add(LeafPage<K, V> source, int index, K key, V value) {
    MVMap<K, V> map = source.map;
    DataType<V> valueType = map.getValueType();

    int newKeyCount = source.keyManager.getKeyCount() + 1;
    V[] newValues = valueType.createStorage(newKeyCount);
    DataUtil.copyWithGap(source.values, newValues, source.values.length, index);
    newValues[index] = value;
    return new LeafPage<>(map, source.keyManager.insertKey(index, key, map), newValues, source.memory + valueType.getMemory(value));
  }

  @Override
  public Page<K, V> remove(int index) {
    int oldKeyCount = keyManager.getKeyCount();

    DataType<V> valueType = map.getValueType();
    V[] newValues = valueType.createStorage(oldKeyCount - 1);
    DataUtil.copyExcept(values, newValues, oldKeyCount, index);
    return new LeafPage<>(map, keyManager.remove(index == oldKeyCount ? (index - 1) : index, map), newValues, memory - valueType.getMemory(values[index]));
  }

  @Override
  public int removeAllRecursive(long version) {
    return removePage(version);
  }

  @Override
  public CursorPos<K, V> getPrependCursorPos(CursorPos<K, V> cursorPos) {
    return new CursorPos<>(this, -1, cursorPos);
  }

  @Override
  public CursorPos<K, V> getAppendCursorPos(CursorPos<K, V> cursorPos) {
    int keyCount = getKeyCount();
    return new CursorPos<>(this, ~keyCount, cursorPos);
  }

  @Override
  protected void writePayload(ByteBuf buf, int keyCount) {
    int dataStart = buf.writerIndex();
    int typePosition = dataStart - 1;

    keyManager.write(keyCount, map, buf);
    if (!map.getKeyType().isGenericCompressionApplicable()) {
      dataStart = buf.writerIndex();
    }

    map.getValueType().write(buf, values, keyCount);

    int uncompressedLength = buf.writerIndex() - dataStart;
    // some compression threshold
    if (uncompressedLength > 256) {
      MVStore store = map.getStore();
      int compressionLevel = store.getCompressionLevel();
      if (compressionLevel > 0) {
        compressData(buf, PAGE_TYPE_LEAF, typePosition, dataStart, store, uncompressedLength, compressionLevel);
      }
    }
  }

  @Override
  protected KeyManager<K> readPayload(ByteBuf buf, int keyCount, boolean isCompressed, int pageLength, int pageStartReaderIndex) {
    KeyableDataType<K> keyType = map.getKeyType();

    KeyManager<K> keyManager = null;
    if (!isCompressed || !keyType.isGenericCompressionApplicable()) {
      keyManager = keyType.createManager(buf, keyCount);
    }

    if (!isCompressed) {
      readValues(buf, keyCount);
      return keyManager;
    }

    int sizeDiff = IntBitPacker.readVar(buf);
    int compressedStart = buf.readerIndex();
    int compressedLength = pageLength - (compressedStart - pageStartReaderIndex);
    int decompressedLength = compressedLength + sizeDiff;
    //System.out.println(" < " + sizeDiff + "  " + decompressedLength + "  " + compressedLength);
    ByteBuf uncompressed = PooledByteBufAllocator.DEFAULT.buffer(decompressedLength, decompressedLength);
    try {
      ByteBuffer inNioBuf = DataUtil.getNioBuffer(buf, compressedStart, compressedLength);
      map.getStore().getDecompressor().decompress(inNioBuf, uncompressed.internalNioBuffer(0, decompressedLength));
      uncompressed.writerIndex(decompressedLength);

      if (keyType.isGenericCompressionApplicable()) {
        keyManager = keyType.createManager(uncompressed, keyCount);
      }
      readValues(uncompressed, keyCount);
      buf.readerIndex(compressedStart + compressedLength);
    }
    finally {
      uncompressed.release();
    }
    return keyManager;
  }

  private void readValues(ByteBuf buf, int keyCount) {
    DataType<V> valueType = map.getValueType();
    values = valueType.createStorage(keyCount);
    valueType.read(buf, values, keyCount);
  }

  @Override
  protected int calculateMemory() {
    return Page.PAGE_MEMORY + map.getValueType().getMemory(values);
  }

  @Override
  void writeUnsavedRecursive(Chunk chunk, ByteBuf buf, LongArrayList toc) {
    if (!isSaved()) {
      write(chunk, buf, toc);
    }
  }

  @Override
  void releaseSavedPages() {}

  @Override
  public int getRawChildPageCount() {
    return 0;
  }

  @Override
  public void dump(StringBuilder buff) {
    super.dump(buff);
    int keyCount = getKeyCount();
    buff.append(", keyCount=").append(keyCount);
    if (keyCount == 0) {
      return;
    }

    buff.append(", content: \n");
    for (int i = 0; i < keyCount; i++) {
      if (i > 0) {
        buff.append(' ');
      }
      buff.append(keyManager.getKey(i));
      if (values != null) {
        buff.append(':');
        buff.append(getValue(i));
      }
    }
  }
}
