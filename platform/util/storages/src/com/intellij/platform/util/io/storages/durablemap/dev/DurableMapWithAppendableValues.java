// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.dev;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.platform.util.io.storages.appendonlylog.dev.ChunkedAppendOnlyLog;
import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.durablemap.DurableMapOverAppendOnlyLog;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.platform.util.io.storages.intmultimaps.DurableIntToMultiIntMap;
import com.intellij.platform.util.io.storages.intmultimaps.HashUtils;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Contrary to {@link DurableMapOverAppendOnlyLog} allows to append to values (i.e., without copy-on-write).
 * Uses {@link ChunkedAppendOnlyLog} to store values as series of
 * chunks, each of chunk itself works as append-only log.
 */
@ApiStatus.Internal
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

  //Append-only-log records format:
  // [ keySize: int32 ][ keyBytes: byte[keySize] ][valueBytes: *]
  // keySize sign-bit is used for marking 'deleted'/value=null records: keySize<0 means value for the key is absent

  @Override
  public Set<VItem> get(@NotNull K key) throws IOException {
    Items<VItem> items = items(key);
    if (items == null) {
      return null;
    }
    @SuppressWarnings("SSBasedInspection")
    ObjectOpenHashSet<VItem> result = new ObjectOpenHashSet<>();
    items.forEach(item -> {
      result.add(item);
    });
    return result;
  }


  @Override
  public Items<VItem> items(@NotNull K key) throws IOException {
    int keyHash = keyDescriptor.getHashCode(key);
    int adjustedHash = HashUtils.adjustHash(keyHash);
    Ref<Pair<K, ItemsImpl>> resultRef = new Ref<>();
    keyHashToChunkIdMap.lookup(adjustedHash, candidateId -> {
      long candidateChunkId = convertStoredIdToChunkId(candidateId);
      Pair<K, ItemsImpl> entry = readEntryIfKeyMatch(candidateChunkId, key);
      if (entry == null) {
        return false;
      }
      resultRef.set(entry);
      return true;
    });
    Pair<K, ItemsImpl> entry = resultRef.get();
    if (entry == null) {
      return null;
    }
    return entry.second;
  }

  @Override
  public boolean containsMapping(@NotNull K key) throws IOException {
    int foundRecordId = lookupRecordId(key);

    return foundRecordId != DurableIntToMultiIntMap.NO_VALUE;
  }

  @Override
  public void put(@NotNull K key,
                  @Nullable Set<VItem> values) throws IOException {
    int keyHash = keyDescriptor.getHashCode(key);
    int adjustedKeyHash = HashUtils.adjustHash(keyHash);

    DataExternalizerEx.KnownSizeRecordWriter keyWriter = keyDescriptor.writerFor(key);
    int keySize = keyWriter.recordSize();
    int keyRecordSize = keySize + Integer.BYTES;

    if (values == null) {
      ChunkedAppendOnlyLog.LogChunk chunk = keyValuesLog.append(keyRecordSize, /*reserveNextChunkIdField: */ false);
      chunk.append(
        buffer -> {
          putHeader(buffer, keySize, /*empty: */ true);
          return keyWriter.write(buffer.slice(Integer.BYTES, keySize)
                                   .order(buffer.order()));
        },
        keyRecordSize
      );
      synchronized (keyHashToChunkIdMap) {
        int foundRecordId = lookupRecordId(key, adjustedKeyHash);
        if (foundRecordId != DurableIntToMultiIntMap.NO_VALUE) {
          keyHashToChunkIdMap.remove(adjustedKeyHash, foundRecordId);
        }
        else {
          // nothing -- key didn't exist before
          // MAYBE RC: skip appending <null> record to the keyValuesLog if there wasn't a non-null record before?
        }
      }
      return;
    }

    //FIXME RC: look up for already existing chunk first!

    //TODO RC: how to guess reasonable initial chunkSize?
    int chunkSize = Math.max(keyRecordSize, 512);
    ChunkedAppendOnlyLog.LogChunk chunk = keyValuesLog.append(chunkSize, /*reserveNextChunkIdField: */ true);
    chunk.append(
      buffer -> {
        putHeader(buffer, keySize, /*empty: */false);
        return keyWriter.write(
          buffer.slice(Integer.BYTES, keySize)
            .order(buffer.order())
        );
      },
      keyRecordSize
    );

    ItemsImpl items = new ItemsImpl(chunk);
    for (VItem item : values) {
      items.append(item);
    }

    int newRecordId = convertChunkIdToStoredId(chunk.id());
    synchronized (keyHashToChunkIdMap) {
      int foundRecordId = lookupRecordId(key, adjustedKeyHash);
      if (foundRecordId != DurableIntToMultiIntMap.NO_VALUE) {
        keyHashToChunkIdMap.replace(adjustedKeyHash, foundRecordId, newRecordId);
      }
      else {
        keyHashToChunkIdMap.put(adjustedKeyHash, newRecordId);
      }
    }
  }

  @Override
  public void remove(@NotNull K key) throws IOException {
    put(key, null);
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super K> processor) throws IOException {
    Set<K> alreadyProcessed = CollectionFactory.createSmallMemoryFootprintSet();
    //Keys listed via .forEach() are non-unique -- having 2 entries (key, value1), (key, value2) same key be listed twice.
    //MAYBE RC: Having alreadyProcessed set is expensive for large maps, better have .forEachKey() method
    //          in DurableIntToMultiIntMap
    //TODO RC: forEachEntry() reads & deserializes both key and value -- but we don't need values here, only keys are needed.
    //         Specialize method so it reads only keys?
    return forEachEntry((key, value) -> {
      if (alreadyProcessed.add(key)) {
        return processor.process(key);
      }
      return true;
    });
  }

  public boolean forEachEntry(@NotNull BiPredicate<? super K, Items<? super VItem>> processor) throws IOException {
    return keyHashToChunkIdMap.forEach((keyHash, recordId) -> {
      Pair<K, ItemsImpl> entry = readEntry(convertStoredIdToChunkId(recordId));
      K key = entry.first;
      ItemsImpl value = entry.second;
      if (value != null) {
        return processor.test(key, value);
      }
      return true;
    });
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
    return keyHashToChunkIdMap.isClosed();
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
    //inlined Math.toIntExact(), just with more detailed error message
    int storeId = (int)(logChunkId);
    if (storeId != logChunkId) {
      throw new IllegalStateException("logChunkId(=" + logChunkId + ") doesn't fit into int32");
    }
    return storeId;
  }

  private static long convertStoredIdToChunkId(int storedRecordId) {
    return storedRecordId;
  }

  private int lookupRecordId(@NotNull K key) throws IOException {
    int keyHash = keyDescriptor.getHashCode(key);
    int adjustedHash = HashUtils.adjustHash(keyHash);

    return lookupRecordId(key, adjustedHash);
  }

  /**
   * Method iterate records in a (key, adjustedKeyHash) bucket, and look for a [record.key equals key]
   *
   * @return recordId for the key, or {@link DurableIntToMultiIntMap#NO_VALUE} if no mapping for a key exists
   */
  private int lookupRecordId(@NotNull K key,
                             int adjustedKeyHash) throws IOException {
    return keyHashToChunkIdMap.lookup(adjustedKeyHash, recordId -> {
      long logChunkId = convertStoredIdToChunkId(recordId);
      ChunkedAppendOnlyLog.LogChunk chunk = keyValuesLog.read(logChunkId);
      ByteBuffer recordBuffer = chunk.read();
      int header = recordBuffer.getInt(0);
      if (isValueAbsent(header)) {
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
  }

  private Pair<K, ItemsImpl> readEntry(long logChunkId) throws IOException {
    ChunkedAppendOnlyLog.LogChunk chunk = keyValuesLog.read(logChunkId);
    ByteBuffer chunkBuffer = chunk.read();
    int header = readHeader(chunkBuffer);
    int keyRecordSize = keySize(header);
    boolean valueIsAbsent = isValueAbsent(header);

    ByteBuffer keyRecordSlice = chunkBuffer.slice(Integer.BYTES, keyRecordSize)
      .order(chunkBuffer.order());
    K key = keyDescriptor.read(keyRecordSlice);

    if (valueIsAbsent) {
      return Pair.pair(key, null);
    }

    return Pair.pair(key, new ItemsImpl(chunk));
  }

  /**
   * @return [key, value] pair by logRecordId, if key==expectedKey, null if the record contains key!=expectedKey.
   * I.e. it is just short-circuit version of {@link #readEntry(long)} and following check entry.key.equals(expectedKey),
   * which skips value deserialization & Pair allocation if [key!=expectedKey]
   */
  private Pair<K, ItemsImpl> readEntryIfKeyMatch(long logChunkId,
                                                 @NotNull K expectedKey) throws IOException {
    ChunkedAppendOnlyLog.LogChunk chunk = keyValuesLog.read(logChunkId);
    ByteBuffer chunkBuffer = chunk.read();
    int header = readHeader(chunkBuffer);
    int keyRecordSize = keySize(header);
    boolean valueIsAbsent = isValueAbsent(header);

    ByteBuffer keyRecordSlice = chunkBuffer.slice(Integer.BYTES, keyRecordSize)
      .order(chunkBuffer.order());
    K candidateKey = keyDescriptor.read(keyRecordSlice);
    if (!keyDescriptor.isEqual(expectedKey, candidateKey)) {
      return null;
    }

    if (valueIsAbsent) {
      return Pair.pair(expectedKey, null);
    }

    return Pair.pair(expectedKey, new ItemsImpl(chunk));
  }

  private static int readHeader(@NotNull ByteBuffer keyBuffer) {
    return keyBuffer.getInt(0);
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

  /** @return value is absent -- null/deleted (we don't differentiate those two cases in this map impl) */
  private static boolean isValueAbsent(int header) {
    int highestBitMask = 0b1000_0000_0000_0000;
    return (header & highestBitMask) != 0;
  }

  //FIXME RC: give the class actual implementation
  private /*record*/ class ItemsImpl implements Items<VItem> {
    private final ChunkedAppendOnlyLog.LogChunk startingChunk;
    private ChunkedAppendOnlyLog.LogChunk lastChunk;

    private ItemsImpl(@NotNull ChunkedAppendOnlyLog.LogChunk startingChunk) {
      this.startingChunk = startingChunk;
      this.lastChunk = startingChunk;
    }

    @Override
    public void append(@NotNull VItem item) throws IOException {
      DataExternalizerEx.KnownSizeRecordWriter writer = valueDescriptor.writerFor(item);
      int valueSize = writer.recordSize();

      appendImpl(item, valueSize, writer);
    }

    private void appendImpl(@NotNull VItem item,
                            int valueSize,
                            @NotNull DataExternalizerEx.KnownSizeRecordWriter writer) throws IOException {
      maybeAdvanceLastChunk();
      int totalRecordSize = valueSize + Integer.BYTES;
      boolean appended = lastChunk.append(buffer -> {
        //TODO RC: do we need a valueSize, or value writer/readers should do it themselves?
        //         this is really a question of KeyDescriptorEx/KnownSizeRecordWriter contract:
        //         should they ensure record size stored? Or should they rely on calling code (i.e. storage)
        //         provide the buffer (slice) of exact size -- hence, it is storage's responsibility to
        //         keep record size somehow?
        buffer.putInt(valueSize);
        return writer.write(buffer);
      }, totalRecordSize);

      if (!appended) {//not enough capacity left in chunk => allocate & chain new chunk
        ChunkedAppendOnlyLog.LogChunk nextChunk = keyValuesLog.append(lastChunk.capacity(), /*reserveNextChunkIdField: */ true);
        boolean succeed = lastChunk.nextChunkId(nextChunk.id());
        if (!succeed) {//FIXME: implement something like retry
          throw new IllegalStateException("FIXME: ...");
        }

        lastChunk = nextChunk;
        append(item);
      }
    }

    private void maybeAdvanceLastChunk() throws IOException {
      while (true) {
        long nextChunkId = lastChunk.nextChunkId();
        if (nextChunkId == ChunkedAppendOnlyLog.NULL_ID) {
          return;
        }
        lastChunk = keyValuesLog.read(nextChunkId);
      }
    }

    @Override
    public void remove(@NotNull VItem item) throws IOException {
      //TODO please, implement me
      throw new UnsupportedOperationException("Method is not implemented yet");
    }

    @Override
    public <E extends Throwable> boolean forEach(@NotNull ThrowableConsumer<? super VItem, E> consumer) throws IOException, E {
      ByteBuffer buffer = startingChunk.read();
      int header = buffer.getInt();
      if (isValueAbsent(header)) {
        return true;//nothing
      }
      int keySize = keySize(header);
      buffer.position(Integer.BYTES + keySize);
      while (buffer.hasRemaining()) {
        int valueSize = buffer.getInt();
        VItem item = valueDescriptor.read(
          buffer.slice(buffer.position(), valueSize)
            .order(buffer.order())
        );

        consumer.consume(item);

        buffer.position(buffer.position() + valueSize);
      }
      //TODO RC: continue chain if nextChunkId
      return true;
    }
  }
}
