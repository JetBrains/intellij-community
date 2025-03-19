// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.platform.util.io.storages.DataExternalizerEx.KnownSizeRecordWriter;
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLog;
import com.intellij.platform.util.io.storages.durablemap.EntryExternalizer.Entry;
import com.intellij.platform.util.io.storages.intmultimaps.DurableIntToMultiIntMap;
import com.intellij.platform.util.io.storages.intmultimaps.HashUtils;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.Unmappable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.BiPredicate;

/**
 * Simplest implementation: (key, value) pairs stored in append-only log, {@link DurableIntToMultiIntMap} is used to keep
 * and update the mapping.
 * <p/>
 * Intended for read-dominant use-cases: i.e. for not too many updates -- otherwise ao-log grows up quickly.
 * <p/>
 * Map doesn't allow null keys. It does allow null values, but {@code .put(key,null)} is equivalent to {@code .remove(key)}
 * Map needs a compaction from time to time
 * <p/>
 * Construct with {@link DurableMapFactory}, not with constructor
 */
@ApiStatus.Internal
public class DurableMapOverAppendOnlyLog<K, V> implements DurableMap<K, V>, Unmappable {

  public static final int DATA_FORMAT_VERSION = 1;

  //TODO RC: current implementation is almost single-threaded -- all the operations, including (potential) IO, happen
  //         under keyHashToIdMap's lock. The only reason for that is an attempt to avoid storing repeating (key,value)
  //         pairs, i.e. avoid filling the log with same key-value.
  //         Without that requirement we could append record to the log _outside_ of the lock, and only acquire the lock
  //         for put(/replace/remove) new recordId in the map -- which should be very short operation, in relation to
  //         serialization and store key-value themself (EHMap _could_ be made more concurrent, but it is harder, and
  //         I'm not convinced it is worth the complexity exactly because it should be very fast, and lock should
  //         almost never be contended then)

  //RC: important property: .keyHashToIdMap must be non-essential for the Map persistence. The map state is fully contained in
  // .keyValuesLog -- i.e. in any moment .keyHashToIdMap content could be dropped, and fully rebuild from .keyValuesLog.
  // Keeping this invariant is crucial for crash-tolerance, because current [int->int*] implementation is NOT crash-tolerant
  // -- so if we want to have crash-tolerance for the DurableMap, we're forced to see the .keyHashToIdMap as
  // non-essential, drop it if any doubts, and recover from the .keyValuesLog, relying on AppendOnlyLog _being_ crash-tolerant.
  // This invariant is implied in the current Map implementation: e.g. for remove(key) just removing the key.hash from .keyHashToIdMap
  // is not enough -- if we do only that, the remove could be lost on crash. So we need to append 'key removed' entry to
  // .keyValuesLog first, and only after that remove the hash from .keyHashToIdMap -- so the remove survives potential crash.
  //

  //TODO RC: use .fixedSize()

  private final AppendOnlyLog keyValuesLog;
  private final DurableIntToMultiIntMap keyHashToIdMap;

  private final EqualityPolicy<? super K> keyEquality;
  /** If not-null => we'll use it to compare values to skip storing duplicates */
  private final @Nullable EqualityPolicy<? super V> valueEquality;

  private final EntryExternalizer<K, V> entryExternalizer;

  /** Ctor is for internal use mostly, use {@link DurableMapFactory} to configure and create the map */
  public DurableMapOverAppendOnlyLog(@NotNull AppendOnlyLog keyValuesLog,
                                     @NotNull DurableIntToMultiIntMap keyHashToIdMap,
                                     @NotNull EqualityPolicy<? super K> keyEquality,
                                     @Nullable EqualityPolicy<? super V> valueEquality,
                                     @NotNull EntryExternalizer<K, V> entryExternalizer) {
    this.keyValuesLog = keyValuesLog;
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
      long logRecordId = convertStoredIdToLogId(recordId);
      return keyValuesLog.read(logRecordId, recordBuffer -> {
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
      long logRecordId = convertStoredIdToLogId(recordId);
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
          long logRecordId = convertStoredIdToLogId(candidateRecordId);
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

      long logRecordId = appendEntry(key, value);
      int storedRecordId = convertLogIdToStoredId(logRecordId);

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


  @Override
  public void remove(@NotNull K key) throws IOException {
    put(key, null);
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super K> processor) throws IOException {
    return keyHashToIdMap.forEach((keyHash, recordId) -> {
      K key = readKey(convertStoredIdToLogId(recordId));
      if (key == null) {
        throw new AssertionError(
          "(keyHash: " + keyHash + ", recordId: " + recordId + "): key can't be null, removed records must NOT be in keyHashToIdMap"
        );
      }
      return processor.process(key);
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
    int totalRecords = keyValuesLog.recordsCount();

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
    keyValuesLog.flush();
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
      IOException.class, () -> new IOException("Can't close " + keyValuesLog + "/" + keyHashToIdMap),

      keyValuesLog::close,
      keyHashToIdMap::close
    );
  }

  @Override
  public void closeAndUnsafelyUnmap() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class, () -> new IOException("Can't unmap " + keyValuesLog + "/" + keyHashToIdMap),

      () -> {
        if (keyValuesLog instanceof Unmappable unmappable) {
          unmappable.closeAndUnsafelyUnmap();
        }
        else {
          keyValuesLog.close();
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
      IOException.class, () -> new IOException("Can't closeAndClean " + keyValuesLog + "/" + keyHashToIdMap),

      keyValuesLog::closeAndClean,
      keyHashToIdMap::closeAndClean
    );
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + keyValuesLog + "]";
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

  static long convertStoredIdToLogId(int storedRecordId) {
    //noinspection RedundantCast
    return (long)storedRecordId;
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
   * I.e. it is just short-circuit version of {@link #readEntry(long)} and check entry.key.equals(expectedKey)
   */
  private Entry<K, V> readEntryIfKeyMatch(long logRecordId,
                                          @NotNull K expectedKey) throws IOException {
    return keyValuesLog.read(logRecordId, recordBuffer -> {
      return entryExternalizer.readIfKeyMatch(recordBuffer, expectedKey);
    });
  }

  private Entry<K, V> readEntry(long logRecordId) throws IOException {
    return keyValuesLog.read(logRecordId, entryExternalizer::read);
  }

  /** @return a key from record(logRecordId), or null, if the record is deleted */
  private @Nullable K readKey(long logRecordId) throws IOException {
    return keyValuesLog.read(logRecordId, entryExternalizer::readKey);
  }

  private long appendEntry(@NotNull K key,
                           @Nullable V value) throws IOException {
    KnownSizeRecordWriter entryWriter = entryExternalizer.writerFor(key, value);
    return keyValuesLog.append(entryWriter, entryWriter.recordSize());
  }
}
