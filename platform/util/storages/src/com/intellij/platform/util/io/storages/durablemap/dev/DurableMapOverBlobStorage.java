// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.dev;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.platform.util.io.storages.DataExternalizerEx.KnownSizeRecordWriter;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.DurableMapOverAppendOnlyLog;
import com.intellij.platform.util.io.storages.durablemap.EntryExternalizer;
import com.intellij.platform.util.io.storages.durablemap.EntryExternalizer.Entry;
import com.intellij.platform.util.io.storages.intmultimaps.DurableIntToMultiIntMap;
import com.intellij.platform.util.io.storages.intmultimaps.HashUtils;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.Unmappable;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * FIXME this implementation is an experiment -- quick-n-dirty prototype for a 'appendable DurableMap over memory-mapped files',
 * to test performance and viability. It is not a good implementation, nor good API design: implementation is mostly copy-pasted
 * from {@link DurableMapOverAppendOnlyLog}, API -- from {@link AppendablePersistentMap}.
 */
@ApiStatus.Internal
public class DurableMapOverBlobStorage<K, V> implements DurableMap<K, V>, Unmappable {

  public static final int DATA_FORMAT_VERSION = 1;

  private final StreamlinedBlobStorage keyValuesStorage;
  private final DurableIntToMultiIntMap keyHashToIdMap;

  private final EqualityPolicy<? super K> keyEquality;
  /** If not-null => we'll use it to compare values to skip storing duplicates */
  private final @Nullable EqualityPolicy<? super V> valueEquality;

  private final EntryExternalizer<K, V> entryExternalizer;

  public DurableMapOverBlobStorage(@NotNull StreamlinedBlobStorage keyValuesStorage,
                                   @NotNull DurableIntToMultiIntMap keyHashToIdMap,
                                   @NotNull EqualityPolicy<? super K> keyEquality,
                                   @Nullable EqualityPolicy<? super V> valueEquality,
                                   @NotNull EntryExternalizer<K, V> entryExternalizer) {
    this.keyValuesStorage = keyValuesStorage;
    this.keyHashToIdMap = keyHashToIdMap;

    this.keyEquality = keyEquality;
    this.valueEquality = valueEquality;

    this.entryExternalizer = entryExternalizer;
  }

  @Override
  public boolean containsMapping(@NotNull K key) throws IOException {
    int keyHash = keyEquality.getHashCode(key);
    int adjustedHash = HashUtils.adjustHash(keyHash);

    int foundRecordId = keyHashToIdMap.lookup(adjustedHash, recordId -> {
      int logRecordId = convertStoredIdToLogId(recordId);
      return keyValuesStorage.readRecord(logRecordId, recordBuffer -> {
        K recordKey = entryExternalizer.readKey(recordBuffer);
        return recordKey != null;
      });
    });

    return foundRecordId != DurableIntToMultiIntMap.NO_VALUE;
  }

  @Override
  public V get(@NotNull K key) throws IOException {
    int keyHash = keyEquality.getHashCode(key);
    int adjustedHash = HashUtils.adjustHash(keyHash);

    Ref<Entry<K, V>> resultRef = new Ref<>();
    keyHashToIdMap.lookup(adjustedHash, recordId -> {
      int logRecordId = convertStoredIdToLogId(recordId);
      Entry<K, V> entry = readEntryIfKeyMatch(logRecordId, key);
      if (entry != null) {
        resultRef.set(entry);
        return true;
      }
      else {
        return false;
      }
    });

    Entry<K, V> entry = resultRef.get();
    if (entry == null) {
      return null;
    }
    else {
      return entry.value();
    }
  }

  @Override
  public void put(@NotNull K key,
                  @Nullable V value) throws IOException {
    int keyHash = keyEquality.getHashCode(key);
    int adjustedHash = HashUtils.adjustHash(keyHash);
    //Abstraction break: synchronize on keyHashToIdMap because we know keyHashToIdMap uses this-monitor to synchronize
    //    itself
    synchronized (keyHashToIdMap) {
      Ref<Boolean> valueIsSameRef = new Ref<>(Boolean.FALSE);
      int foundRecordId = keyHashToIdMap.lookup(
        adjustedHash,
        candidateRecordId -> {
          int logRecordId = convertStoredIdToLogId(candidateRecordId);
          if (valueEquality != null) {
            Entry<K, V> entryWithSameKey = readEntryIfKeyMatch(logRecordId, key);
            if (entryWithSameKey == null) {
              return false; // [record.key != key] => hash collision => look further
            }

            boolean valueIsSame = valuesEqualNullSafe(value, entryWithSameKey.value());
            valueIsSameRef.set(valueIsSame);
          }
          else {
            //without valueEquality we don't use .value at all -> don't need to read it then
            K candidateKey = readKey(logRecordId);
            if (candidateKey == null
                || !keyEquality.isEqual(key, candidateKey)) {
              return false; // [record.key != key] => hash collision => look further
            }

            valueIsSameRef.set(Boolean.FALSE);
          }

          return true;
        }
      );

      boolean keyRecordExists = (foundRecordId != DurableIntToMultiIntMap.NO_VALUE);
      boolean valueIsSame = valueIsSameRef.get();

      //Check is value differ from current value -- we don't need to append the log with the same (key,value)
      // again and again
      if (keyRecordExists && valueIsSame) {
        return; // [current value == new value] => nothing to do
      }

      long newLogRecordId = updateOrWriteEntry(foundRecordId, key, value);
      if (newLogRecordId == foundRecordId) {
        if (value == null) {//remove deleted mapping
          keyHashToIdMap.remove(adjustedHash, foundRecordId);
        }
        return;//new record written on the top of the old one => no need to update the map
      }

      int storedRecordId = convertLogIdToStoredId(newLogRecordId);

      if (keyRecordExists) {
        // (key) record exist, but with different value => replace recordId:
        if (value != null) {
          keyHashToIdMap.replace(adjustedHash, foundRecordId, storedRecordId);
        }
        else {//remove deleted mapping
          keyHashToIdMap.remove(adjustedHash, foundRecordId);
        }
      }
      else {
        // (key) record don't exist yet => put it:
        if (value != null) {
          keyHashToIdMap.put(adjustedHash, storedRecordId);
        }
      }
    }
  }


  //TODO RC: append is a bad API -- much better would be 'modify()' there old value-buffer could be modified in any way,
  //         including the append
  /** key must already exists in the map, otherwise IOException is thrown */
  public void append(@NotNull K key,
                     @NotNull KnownSizeRecordWriter appender) throws IOException {
    int keyHash = keyEquality.getHashCode(key);
    int adjustedHash = HashUtils.adjustHash(keyHash);
    //Abstraction break: synchronize on keyHashToIdMap because we know keyHashToIdMap uses this-monitor to synchronize
    //    itself
    synchronized (keyHashToIdMap) {
      int foundRecordId = keyHashToIdMap.lookup(
        adjustedHash,
        candidateRecordId -> {
          int logRecordId = convertStoredIdToLogId(candidateRecordId);
          K candidateKey = readKey(logRecordId);
          if (candidateKey == null
              || !keyEquality.isEqual(key, candidateKey)) {
            return false; // [record.key != key] => hash collision => look further
          }

          return true;
        }
      );

      boolean keyRecordExists = (foundRecordId != DurableIntToMultiIntMap.NO_VALUE);
      if (!keyRecordExists) {
        throw new IOException("[" + key + "] entry does not exist -- can't be appended to");
      }

      long potentiallyNewLogRecordId = appendToEntry(foundRecordId, key, appender);
      if (potentiallyNewLogRecordId == foundRecordId) {
        return;//new data appended to the already existing record => no need to update the map
      }

      int storedRecordId = convertLogIdToStoredId(potentiallyNewLogRecordId);

      // (key) record exist, but with different value => replace recordId:
      keyHashToIdMap.replace(adjustedHash, foundRecordId, storedRecordId);
    }
  }


  @Override
  public void remove(@NotNull K key) throws IOException {
    put(key, null);
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super K> processor) throws IOException {
    //Keys listed via .forEach() are non-unique -- having 2 entries (key, value1), (key, value2) same key be listed twice.
    Set<K> alreadyProcessed = CollectionFactory.createSmallMemoryFootprintSet();
    //MAYBE RC: Having alreadyProcessed set is expensive for large maps?
    return keyHashToIdMap.forEach((keyHash, recordId) -> {
      K key = readKey(convertStoredIdToLogId(recordId));
      if (key == null) {
        throw new AssertionError(
          "(keyHash: " + keyHash + ", recordId: " + recordId + "): key can't be null, removed records must NOT be in keyHashToIdMap"
        );
      }
      if (alreadyProcessed.add(key)) {
        return processor.process(key);
      }
      return true;
    });
  }

  @Override
  public boolean forEachEntry(@NotNull BiPredicate<? super K, ? super V> processor) throws IOException {
    return keyHashToIdMap.forEach((keyHash, recordId) -> {
      Entry<K, V> entry = readEntry(convertStoredIdToLogId(recordId));
      K key = entry.key();
      if (!entry.isValueVoid()) {
        V value = entry.value();
        return processor.test(key, value);
      }
      return true;
    });
  }


  @Override
  public boolean isEmpty() throws IOException {
    return keyHashToIdMap.isEmpty();
  }

  @Override
  public int size() throws IOException {
    return keyHashToIdMap.size();
  }

  @Override
  public @NotNull CompactionScore compactionScore() throws IOException {
    int activeRecords = keyHashToIdMap.size();
    int totalRecords = keyValuesStorage.liveRecordsCount();

    if (totalRecords == 0) {
      return new CompactionScore(0);
    }

    //'% of wasted records -- out of all records stored'
    double score = 1 - (activeRecords * 1.0 / totalRecords);

    if (totalRecords < 512) {
      // score could be too unstable if records number is small:
      return new CompactionScore(Math.max(score, 0.1));
    }

    return new CompactionScore(score);
  }

  @Override
  public @NotNull <M extends DurableMap<K, V>> M compact(
    @NotNull ThrowableComputable<M, ? extends IOException> compactedMapFactory
  ) throws IOException {
    //FIXME RC: design the new map creation: how/where to create it? Paths should somehow be
    //          passed from outside, but also maybe tuned here?
    //          Maybe just use the storageFactory? Keep the factory by which the current Map was created in a fields, and
    //          use either it, or the one passed from outside?
    //MAYBE RC: should we do a compaction if there is nothing to compact really -- i.e. if there is 0 wasted records?
    //          Or maybe we should return current map in this case? Or reject explicitly, by throwing exception?
    //          Or just leave it on caller decision?
    return IOUtil.wrapSafely(
      compactedMapFactory.compute(),
      compactedMap -> {
        keyHashToIdMap.forEach((keyHash, recordId) -> {
          Entry<K, V> entry = readEntry(convertStoredIdToLogId(recordId));
          if (!entry.isValueVoid()) {
            compactedMap.put(entry.key(), entry.value());
          }
          return true;
        });
        return compactedMap;
      }
    );
  }


  @Override
  public void force() throws IOException {
    keyValuesStorage.force();
    keyHashToIdMap.flush();
  }

  @Override
  public boolean isDirty() {
    //as usual, assume mapped-files based impls are never 'dirty':
    return false;
  }


  @Override
  public boolean isClosed() {
    return keyHashToIdMap.isClosed();
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class, () -> new IOException("Can't close " + keyValuesStorage + "/" + keyHashToIdMap),

      keyValuesStorage::close,
      keyHashToIdMap::close
    );
  }

  @Override
  public void closeAndUnsafelyUnmap() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class, () -> new IOException("Can't unmap " + keyValuesStorage + "/" + keyHashToIdMap),

      () -> {
        if (keyValuesStorage instanceof Unmappable unmappable) {
          unmappable.closeAndUnsafelyUnmap();
        }
        else {
          keyValuesStorage.close();
        }
      },
      () -> {
        if (keyHashToIdMap instanceof Unmappable unmappable) {
          unmappable.closeAndUnsafelyUnmap();
        }
        else {
          keyHashToIdMap.close();
        }
      }
    );
  }

  @Override
  public void closeAndClean() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class, () -> new IOException("Can't closeAndClean " + keyValuesStorage + "/" + keyHashToIdMap),

      keyValuesStorage::closeAndClean,
      keyHashToIdMap::closeAndClean
    );
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + keyValuesStorage + "]";
  }

  // ============================= infrastructure: ============================================================================ //

  //logRecordId (long): id of record in AppendOnlyLog
  //storedId    (int):  id stored (as value) in the keyHashToIdMap

  static int convertLogIdToStoredId(long logRecordId) {
    int intStoredId = (int)logRecordId;
    if (intStoredId != logRecordId) {
      throw new AssertionError("Overflow: logRecordId(=" + logRecordId + ") > MAX_INT(" + Integer.MAX_VALUE + ")");
    }
    return intStoredId;
  }

  static int convertStoredIdToLogId(int storedRecordId) {
    //noinspection RedundantCast
    return (int)storedRecordId;
  }

  /** valueDescriptor is expected to NOT process null values, so we compare null values separately */
  private boolean valuesEqualNullSafe(@Nullable V value,
                                      @Nullable V anotherValue) {
    if ((anotherValue == null && value == null)) {
      return true;
    }
    if (valueEquality == null) {
      return false;
    }
    if ((anotherValue != null && value != null)
        && valueEquality.isEqual(value, anotherValue)) {
      return true;
    }
    return false;
  }

  /**
   * @return [key, value] pair by logRecordId, if key==expectedKey, null if the record contains key!=expectedKey
   * I.e. it is just short-circuit version of {@link #readEntry(int)} and check entry.key.equals(expectedKey)
   */
  private Entry<K, V> readEntryIfKeyMatch(int logRecordId,
                                          @NotNull K expectedKey) throws IOException {
    return keyValuesStorage.readRecord(logRecordId, recordBuffer -> {
      return entryExternalizer.readIfKeyMatch(recordBuffer, expectedKey);
    });
  }

  private Entry<K, V> readEntry(int logRecordId) throws IOException {
    return keyValuesStorage.readRecord(logRecordId, entryExternalizer::read);
  }

  /** @return a key from record(logRecordId), or null, if the record is deleted */
  private @Nullable K readKey(int logRecordId) throws IOException {
    return keyValuesStorage.readRecord(logRecordId, entryExternalizer::readKey);
  }

  private int updateOrWriteEntry(int foundRecordId,
                                 @NotNull K key,
                                 @Nullable V value) throws IOException {
    KnownSizeRecordWriter entryWriter = entryExternalizer.writerFor(key, value);
    int recordSize = entryWriter.recordSize();
    int maxPayloadSupported = keyValuesStorage.maxPayloadSupported();
    if (recordSize > maxPayloadSupported) {
      throw new IOException("[" + key + "].recordSize(=" + recordSize + ") > max supported payload size(=" + maxPayloadSupported + ")");
    }
    return keyValuesStorage.writeToRecord(
      foundRecordId,
      buffer -> {
        ByteBuffer toWrite;
        if (buffer.capacity() < recordSize) {
          toWrite = ByteBuffer.allocate(recordSize)
            .order(buffer.order());
        }
        else {
          toWrite = buffer;
        }
        toWrite.position(0)
          .limit(recordSize);

        entryWriter.write(toWrite);

        toWrite.position(recordSize)
          .limit(recordSize);
        return toWrite;
      },
      recordSize
    );
  }

  private int appendToEntry(int foundRecordId,
                            @NotNull K key,
                            @NotNull KnownSizeRecordWriter appender) throws IOException {
    int recordSize = appender.recordSize();
    int maxPayloadSupported = keyValuesStorage.maxPayloadSupported();
    if (recordSize > maxPayloadSupported) {
      throw new IOException("[" + key + "].recordSize(=" + recordSize + ") > max supported payload size(=" + maxPayloadSupported + ")");
    }
    return keyValuesStorage.writeToRecord(
      foundRecordId,
      buffer -> {
        ByteBuffer toWrite;
        int currentRecordSize = buffer.limit();
        int newRecordSize = currentRecordSize + recordSize;
        if (newRecordSize > buffer.capacity()) {
          toWrite = ByteBuffer.allocate(newRecordSize)
            .order(buffer.order());
          toWrite.put(buffer);
        }
        else {
          toWrite = buffer;
        }
        toWrite.position(currentRecordSize)
          .limit(newRecordSize);

        appender.write(toWrite);

        toWrite.position(newRecordSize)
          .limit(newRecordSize);
        return toWrite;
      }
    );
  }
}
