// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.durablemaps;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.io.dev.appendonlylog.ChunkedAppendOnlyLog;
import com.intellij.util.io.dev.appendonlylog.ChunkedAppendOnlyLog.LogChunk;
import com.intellij.util.io.dev.durablemaps.AppendableDurableMap;
import com.intellij.util.io.dev.durablemaps.DurableMap;
import com.intellij.util.io.dev.enumerator.KeyDescriptorEx;
import com.intellij.util.io.dev.intmultimaps.DurableIntToMultiIntMap;
import com.intellij.util.io.dev.intmultimaps.HashUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Contrary to {@link DurableMapOverAppendOnlyLog} allows to append to values (i.e., without copy-on-write).
 * Uses {@link com.intellij.util.io.dev.appendonlylog.ChunkedAppendOnlyLog} to store values as series of
 * chunks, each of chunk itself works as append-only log.
 */
public class DurableMapWithAppendableValues<K, VItem> implements AppendableDurableMap<K, VItem> {

  private final ChunkedAppendOnlyLog keyValuesLog;
  private final ExtendibleHashMap keyHashToChunkIdMap;

  private final KeyDescriptorEx<K> keyDescriptor;
  private final KeyDescriptorEx<VItem> valueDescriptor;

  public DurableMapWithAppendableValues(@NotNull ChunkedAppendOnlyLog keyValuesLog,
                                        @NotNull ExtendibleHashMap keyHashToChunkIdMap,
                                        @NotNull KeyDescriptorEx<K> keyDescriptor,
                                        @NotNull KeyDescriptorEx<VItem> valueDescriptor) {
    this.keyValuesLog = keyValuesLog;
    this.keyHashToChunkIdMap = keyHashToChunkIdMap;
    this.keyDescriptor = keyDescriptor;
    this.valueDescriptor = valueDescriptor;
  }

  //Append-only-log records format: <keySize:int32><keyBytes><valueBytes>
  //  keySize sign bit is used for marking 'deleted'/value=null records: keySize<0 means record is deleted

  @Override
  public Set<VItem> get(@NotNull K key) throws IOException {
    Items<VItem> items = items(key);
    if (items == null) {
      return null;
    }
    ObjectOpenHashSet<VItem> result = new ObjectOpenHashSet<>();
    items.forEach(item -> {
      result.add(item);
    });
    return result;
  }


  @Override
  public Items<VItem> items(@NotNull K key) throws IOException {
    int hash = keyDescriptor.getHashCode(key);
    int adjustedHash = HashUtils.adjustHash(hash);
    Ref<Pair<K, ItemsImpl<VItem>>> resultRef = new Ref<>();
    keyHashToChunkIdMap.lookup(adjustedHash, candidateId -> {
      long candidateChunkId = convertStoredIdToChunkId(candidateId);
      Pair<K, ItemsImpl<VItem>> entry = readEntryIfKeyMatch(candidateChunkId, key);
      if (entry == null) {
        return false;
      }
      resultRef.set(entry);
      return true;
    });
    Pair<K, ItemsImpl<VItem>> entry = resultRef.get();
    if (entry == null) {
      return null;
    }
    return entry.second;
  }

  @Override
  public boolean containsMapping(@NotNull K key) throws IOException {
    int hash = keyDescriptor.getHashCode(key);
    int keyHash = HashUtils.adjustHash(hash);

    int foundRecordId = keyHashToChunkIdMap.lookup(keyHash, recordId -> {
      long logChunkId = convertStoredIdToChunkId(recordId);
      LogChunk chunk = keyValuesLog.read(logChunkId);
      ByteBuffer recordBuffer = chunk.read();
      int header = recordBuffer.getInt(0);
      if (isValueVoid(header)) {
        return false;
      }

      int keyRecordSize = keySize(header);
      ByteBuffer keyRecordSlice = recordBuffer.slice(Integer.BYTES, keyRecordSize);
      K candidateKey = keyDescriptor.read(keyRecordSlice);
      if (keyDescriptor.isEqual(key, candidateKey)) {
        return true;
      }
      else {
        return false;
      }
    });

    return foundRecordId != DurableIntToMultiIntMap.NO_VALUE;
  }

  @Override
  public void put(@NotNull K key, @Nullable Set<VItem> value) throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public void remove(@NotNull K key) throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super K> processor) throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }


  @Override
  public boolean isEmpty() throws IOException {
    return keyHashToChunkIdMap.isEmpty();
  }

  @Override
  public int size() throws IOException {
    return keyHashToChunkIdMap.size();
  }

  @Override
  public boolean isDirty() {
    //as usual, assume mapped-files based impls are never 'dirty':
    return false;
  }

  @Override
  public void force() throws IOException {
    keyValuesLog.flush();
    keyHashToChunkIdMap.flush();
  }

  @Override
  public @NotNull CompactionScore compactionScore() throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public <C1 extends DurableMap<K, Set<VItem>>> @NotNull C1 compact(@NotNull ThrowableComputable<C1, ? extends IOException> compactedMapFactory)
    throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public boolean isClosed() {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Can't close " + keyValuesLog + "/" + keyHashToChunkIdMap),
      keyValuesLog::close,
      keyHashToChunkIdMap::close
    );
  }

  @Override
  public void closeAndClean() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Can't closeAndClean " + keyValuesLog + "/" + keyHashToChunkIdMap),
      keyValuesLog::closeAndClean,
      keyHashToChunkIdMap::closeAndClean
    );
  }

  // ===================================== implementation ================================================================


  private static int convertChunkIdToStoredId(long logChunkId) {
    int storeId = (int)(logChunkId);
    if (storeId != logChunkId) {
      throw new IllegalStateException("logChunkId(=" + logChunkId + ") doesn't fit into int32");
    }
    return storeId;
  }

  private static long convertStoredIdToChunkId(int storedRecordId) {
    return storedRecordId;
  }

  private Pair<K, ItemsImpl<VItem>> readEntry(long logChunkId) throws IOException {
    LogChunk chunk = keyValuesLog.read(logChunkId);
    ByteBuffer chunkBuffer = chunk.read();
    int header = readHeader(chunkBuffer);
    int keyRecordSize = keySize(header);
    boolean valueIsNull = isValueVoid(header);

    ByteBuffer keyRecordSlice = chunkBuffer.slice(Integer.BYTES, keyRecordSize);
    K key = keyDescriptor.read(keyRecordSlice);

    if (valueIsNull) {
      return Pair.pair(key, null);
    }

    int valueLength = chunkBuffer.remaining() - keyRecordSize - Integer.BYTES;
    ByteBuffer valueRecordSlice = chunkBuffer.slice(Integer.BYTES + keyRecordSize, valueLength);
    return Pair.pair(key, new ItemsImpl<>(chunk, valueRecordSlice));
  }

  /**
   * @return [key, value] pair by logRecordId, if key==expectedKey, null if the record contains key!=expectedKey.
   * I.e. it is just short-circuit version of {@link #readEntry(long)} and check entry.key.equals(expectedKey)
   */
  private Pair<K, ItemsImpl<VItem>> readEntryIfKeyMatch(long logChunkId,
                                                        @NotNull K expectedKey) throws IOException {
    LogChunk chunk = keyValuesLog.read(logChunkId);
    ByteBuffer chunkBuffer = chunk.read();
    int header = readHeader(chunkBuffer);
    int keyRecordSize = keySize(header);
    boolean valueIsNull = isValueVoid(header);

    ByteBuffer keyRecordSlice = chunkBuffer.slice(Integer.BYTES, keyRecordSize);
    K candidateKey = keyDescriptor.read(keyRecordSlice);
    if (!keyDescriptor.isEqual(expectedKey, candidateKey)) {
      return null;
    }

    if (valueIsNull) {
      return Pair.pair(expectedKey, null);
    }

    int valueLength = chunkBuffer.remaining() - keyRecordSize - Integer.BYTES;
    ByteBuffer valueRecordSlice = chunkBuffer.slice(Integer.BYTES + keyRecordSize, valueLength);
    return Pair.pair(expectedKey, new ItemsImpl<>(chunk, valueRecordSlice));
  }

  private static int readHeader(@NotNull ByteBuffer keyBuffer) {
    return keyBuffer.get(0);
  }

  private static void putHeader(@NotNull ByteBuffer keyBuffer,
                                int keySize,
                                boolean valueEmpty) {
    if (keySize < 0) {
      throw new IllegalArgumentException("keySize(=" + keySize + ") must have highest bit 0");
    }
    if (valueEmpty) {
      int highestBitMask = 0b1000_0000_0000_0000;
      keyBuffer.putInt(0, keySize | highestBitMask);
    }
    else {
      //MAYBE RC: use varint DataInputOutputUtil.writeINT(buffer, keySize)?
      //          -- but this makes record size computation more difficult
      keyBuffer.putInt(0, keySize);
    }
  }

  private static int keySize(int header) {
    int highestBitMask = 0b1000_0000_0000_0000;
    return header & ~highestBitMask;
  }

  /** @return value is void -- null/deleted (we don't differentiate those two cases in this map impl) */
  private static boolean isValueVoid(int header) {
    int highestBitMask = 0b1000_0000_0000_0000;
    return (header & highestBitMask) != 0;
  }

  //FIXME RC: give the class actual implementation
  private static class ItemsImpl<VItem> implements Items<VItem> {
    private final LogChunk startingChunk;
    private final ByteBuffer valueBuffer;

    private ItemsImpl(LogChunk startingChunk,
                      ByteBuffer valueBuffer) {
      this.startingChunk = startingChunk;
      this.valueBuffer = valueBuffer;
    }

    @Override
    public void append(@NotNull VItem item) throws IOException {
      //TODO please, implement me
      throw new UnsupportedOperationException("Method is not implemented yet");
    }

    @Override
    public void remove(@NotNull VItem item) throws IOException {
      //TODO please, implement me
      throw new UnsupportedOperationException("Method is not implemented yet");
    }

    @Override
    public <E extends Throwable> boolean forEach(@NotNull ThrowableConsumer<? super VItem, E> consumer) throws IOException, E {
      //TODO please, implement me
      throw new UnsupportedOperationException("Method is not implemented yet");
    }
  }
}
