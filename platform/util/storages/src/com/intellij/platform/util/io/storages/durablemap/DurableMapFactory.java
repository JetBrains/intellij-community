// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLog;
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogFactory;
import com.intellij.platform.util.io.storages.durablemap.EntryExternalizer.Entry;
import com.intellij.platform.util.io.storages.intmultimaps.DurableIntToMultiIntMap;
import com.intellij.platform.util.io.storages.intmultimaps.HashUtils;
import com.intellij.platform.util.io.storages.intmultimaps.NonDurableNonParallelIntToMultiIntMap;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.durablemap.DurableMapOverAppendOnlyLog.convertLogIdToStoredId;
import static com.intellij.platform.util.io.storages.durablemap.DurableMapOverAppendOnlyLog.convertStoredIdToLogId;
import static com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleMapFactory.NotClosedProperlyAction.IGNORE_AND_HOPE_FOR_THE_BEST;
import static com.intellij.util.io.IOUtil.MiB;

/**
 * Factory for {@link DurableMapOverAppendOnlyLog} -- use this instead of ctor.
 */
@ApiStatus.Internal
public class DurableMapFactory<K, V> implements StorageFactory<DurableMapOverAppendOnlyLog<K, V>> {
  private static final Logger LOG = Logger.getInstance(DurableMapFactory.class);

  public static final int DEFAULT_PAGE_SIZE = 8 * MiB;

  public static final String MAP_FILE_SUFFIX = ".map";


  public static final StorageFactory<? extends AppendOnlyLog> DEFAULT_VALUES_LOG_FACTORY = AppendOnlyLogFactory
    .withDefaults()
    .pageSize(DEFAULT_PAGE_SIZE)
    .cleanIfFileIncompatible()
    .failIfDataFormatVersionNotMatch(DurableMapOverAppendOnlyLog.DATA_FORMAT_VERSION);

  public static final StorageFactory<? extends DurableIntToMultiIntMap> DEFAULT_MAP_FACTORY = ExtendibleMapFactory
    .mediumSize()
    .cleanIfFileIncompatible()
    .ifNotClosedProperly(IGNORE_AND_HOPE_FOR_THE_BEST);//for .wasClosedProperly() flag to be not cleared


  private final StorageFactory<? extends AppendOnlyLog> logFactory;
  private final StorageFactory<? extends DurableIntToMultiIntMap> mapFactory;

  private final @NotNull EqualityPolicy<? super K> keyEquality;
  private final @Nullable EqualityPolicy<? super V> valueEquality;

  private final @NotNull EntryExternalizer<K, V> entryExternalizer;

  private final boolean rebuildMapFromLogIfInconsistent;

  private DurableMapFactory(@NotNull StorageFactory<? extends AppendOnlyLog> logFactory,
                            @NotNull StorageFactory<? extends DurableIntToMultiIntMap> mapFactory,

                            @NotNull EqualityPolicy<? super K> keyEquality,
                            @Nullable EqualityPolicy<? super V> valueEquality,

                            @NotNull EntryExternalizer<K, V> entryExternalizer,

                            boolean rebuildMapFromLogIfInconsistent) {
    this.logFactory = logFactory;
    this.mapFactory = mapFactory;
    this.keyEquality = keyEquality;
    this.valueEquality = valueEquality;
    this.entryExternalizer = entryExternalizer;
    this.rebuildMapFromLogIfInconsistent = rebuildMapFromLogIfInconsistent;
  }

  public static <K, V> @NotNull DurableMapFactory<K, V> withDefaults(@NotNull KeyDescriptorEx<K> keyDescriptor,
                                                                     @NotNull KeyDescriptorEx<V> valueDescriptor) {
    return new DurableMapFactory<>(
      DEFAULT_VALUES_LOG_FACTORY, DEFAULT_MAP_FACTORY,
      keyDescriptor, valueDescriptor,
      entryExternalizerFor(keyDescriptor, valueDescriptor),
      /*rebuildMapFromLogIfInconsistent = */ true
    );
  }

  public static <K, V> @NotNull DurableMapFactory<K, V> withDefaults(@NotNull KeyDescriptorEx<K> keyDescriptor,
                                                                     @NotNull DataExternalizerEx<V> valueExternalizer) {
    return new DurableMapFactory<>(
      DEFAULT_VALUES_LOG_FACTORY, DEFAULT_MAP_FACTORY,
      keyDescriptor, /*valueEquality: */ null,
      entryExternalizerFor(keyDescriptor, valueExternalizer),
      /*rebuildMapFromLogIfInconsistent = */ true
    );
  }

  public static <K, V> @NotNull DurableMapFactory<K, V> withDefaults(@NotNull EqualityPolicy<? super K> keyEquality,
                                                                     @NotNull EqualityPolicy<? super V> valueEquality,
                                                                     @NotNull EntryExternalizer<K, V> entryExternalizer) {
    return new DurableMapFactory<>(
      DEFAULT_VALUES_LOG_FACTORY, DEFAULT_MAP_FACTORY,
      keyEquality, valueEquality,
      entryExternalizer,
      /*rebuildMapFromLogIfInconsistent = */ true
    );
  }

  public static <K, V> @NotNull DurableMapFactory<K, V> withDefaults(@NotNull EqualityPolicy<? super K> keyEquality,
                                                                     @NotNull EntryExternalizer<K, V> entryExternalizer) {
    return new DurableMapFactory<>(
      DEFAULT_VALUES_LOG_FACTORY, DEFAULT_MAP_FACTORY,
      keyEquality, /*valueEquality: */ null,
      entryExternalizer,
      /*rebuildMapFromLogIfInconsistent = */ true
    );
  }

  public DurableMapFactory<K, V> logFactory(@NotNull StorageFactory<? extends AppendOnlyLog> logFactory) {
    return new DurableMapFactory<>(logFactory, mapFactory, keyEquality, valueEquality, entryExternalizer, rebuildMapFromLogIfInconsistent);
  }

  public DurableMapFactory<K, V> mapFactory(@NotNull StorageFactory<? extends DurableIntToMultiIntMap> mapFactory) {
    return new DurableMapFactory<>(logFactory, mapFactory, keyEquality, valueEquality, entryExternalizer, rebuildMapFromLogIfInconsistent);
  }

  public DurableMapFactory<K, V> rebuildMapIfInconsistent(boolean rebuildMapFromLogIfInconsistent) {
    return new DurableMapFactory<>(logFactory, mapFactory, keyEquality, valueEquality, entryExternalizer, rebuildMapFromLogIfInconsistent);
  }

  @Override
  public @NotNull DurableMapOverAppendOnlyLog<K, V> open(@NotNull Path storagePath) throws IOException {
    String name = storagePath.getFileName().toString();
    Path mapPath = storagePath.resolveSibling(name + MAP_FILE_SUFFIX);
    boolean mapFileExists = Files.exists(mapPath);

    return logFactory.wrapStorageSafely(
      storagePath,
      entriesLog -> {
        try {
          return mapFactory.wrapStorageSafely(
            mapPath,
            keyHashToEntryOffsetMap -> {
              if (!entriesLog.isEmpty() && keyHashToEntryOffsetMap.isEmpty()) {
                if (keyHashToEntryOffsetMap instanceof NonDurableNonParallelIntToMultiIntMap) {
                  //empty, non-durable map: must be filled from the log
                  fillValueHashToIdMap(entriesLog, keyEquality, entryExternalizer, keyHashToEntryOffsetMap);
                  LOG.info("[" + name + "]: keyToOffsetMap (in memory) was filled from entriesLog " +
                           "(" + keyHashToEntryOffsetMap.size() + " records)");
                }
                else if (!mapFileExists) {
                  //empty, durable map, AND map file didn't exists AND entriesLog is not empty
                  //  => we probably lost the .map file, and it was created fresh => try to fill the map from
                  //  the entriesLog also:
                  fillValueHashToIdMap(entriesLog, keyEquality, entryExternalizer, keyHashToEntryOffsetMap);
                  LOG.info("[" + name + "]: keyToOffsetMap (in memory) was filled from entriesLog " +
                           "(" + keyHashToEntryOffsetMap.size() + " records)");
                }
                //else (empty, durable map, not freshly created): valid case if all entries were removed
              }

              return new DurableMapOverAppendOnlyLog<>(
                entriesLog,
                keyHashToEntryOffsetMap,
                keyEquality,
                valueEquality,
                entryExternalizer
              );
            }
          );
        }
        catch (CorruptedException e) {
          if (!rebuildMapFromLogIfInconsistent) {
            throw e;
          }

          //If keyToOffsetMap is durable, and corrupted -> below we try rebuilding such a map
          //   (i.e. it is a 'recovery' for durable maps corrupted by improper app termination)

          //TODO RC: this branch is a bit of hack: CorruptedException could be thrown not only from the mapFactory, but also
          //         from the entriesLog -- there is no way to find out the source.
          //         Would be better to rely on the mapFactory itself to DROP_AND_CREATE_EMPTY_MAP -- but we need a way to know
          //         map _was_ re-created from 0. Currently there is no way to know that: mapFactory just provides a map,
          //         which is empty -- maybe because it is genuinely empty, maybe because it was re-created from 0.
          //         The recovery logic may be simpler & more reliable if e.g. StorageFactory provides an optional additional
          //         arg (something like OpeningConditions) with details about how exactly the storage was opened -- e.g. with
          //         nuances or not.

          FileUtil.delete(mapPath);//assume MapFactory has closed & unmapped the map before throwing exception

          return mapFactory.wrapStorageSafely(
            mapPath,
            keyHashToEntryOffsetMap -> {
              if (!entriesLog.isEmpty()) {
                LOG.warn("[" + name + "]: .keyToOffsetMap map corrupted -> try recovering the map from entriesLog " +
                         "(impl: " + keyHashToEntryOffsetMap.getClass() + ")", e);


                fillValueHashToIdMap(entriesLog, keyEquality, entryExternalizer, keyHashToEntryOffsetMap);

                LOG.info("[" + name + "]: keyToOffsetMap (durable) was recovered from entriesLog " +
                         "(" + keyHashToEntryOffsetMap.size() + " records)");
              }

              return new DurableMapOverAppendOnlyLog<>(
                entriesLog,
                keyHashToEntryOffsetMap,
                keyEquality,
                valueEquality,
                entryExternalizer
              );
            }
          );

          //TODO RC: what if entriesLog was recovered? -- could it be the keyHashToEntryOffsetMap map is somehow .wasClosedProperly,
          //         but still is inconsistent with valuesLog? It seems it could: current implementation of append-only-log
          //         _could_ sometimes lose the written-and-commited record: i.e. one of the previous values in the log
          //         wasn't committed, and even record header wasn't written -- and because of that whole region after
          //         that allocated-but-not-yet-started record is lost (see more detailed discussion in the implementation
          //         class). So there _is_ a chance that value is appended to the log, and valueId is inserted into the map,
          //         but value record is lost on crash, so the id put in the map is now invalid.
          //         So we should either modify AppendOnlyLog so that it doesn't allow that (e.g. we could not return
          //         from allocateRecord until at least header is written -- again, see discussion in the impl class) -- or
          //         we should always rebuild the map if AppendOnlyLog was recovered, _and_ the recovered region >0 -- even
          //         if the map itself wasClosedProperly=true.

          //MAYBE separate 'always rebuild map' and 'rebuild map if inconsistent'
          //      (both requires .open(path,CREATE_NEW) method)
        }
      }
    );
  }

  public static <K, V> @NotNull EntryExternalizer<K, V> entryExternalizerFor(@NotNull KeyDescriptorEx<K> keyDescriptor,
                                                                             @NotNull DataExternalizerEx<V> valueExternalizer) {
    if (keyDescriptor.isRecordSizeConstant()) {
      return new FixedSizeKeyEntryExternalizer<>(keyDescriptor, valueExternalizer);
    }
    else {
      return new DefaultEntryExternalizer<>(keyDescriptor, valueExternalizer);
    }
  }

  private static <K, V> void fillValueHashToIdMap(@NotNull AppendOnlyLog valuesLog,
                                                  @NotNull EqualityPolicy<? super K> keyEquality,
                                                  @NotNull EntryExternalizer<K, V> entryExternalizer,
                                                  @NotNull DurableIntToMultiIntMap keyHashToEntryOffsetMap) throws IOException {
    //MAYBE RC: keyHashToEntryOffsetMap could be filled async -- to not delay initialization? (see DurableStringEnumerator)

    valuesLog.forEachRecord((logId, entryBuffer) -> {
      Entry<K, V> entry = entryExternalizer.read(entryBuffer);
      K key = entry.key();

      //RC: below is basically DurableMapOverAppendOnlyLog.put() method, slightly stripped

      int adjustedHash = HashUtils.adjustHash(keyEquality.getHashCode(key));
      int storedId = convertLogIdToStoredId(logId);

      int foundRecordId = keyHashToEntryOffsetMap.lookup(
        adjustedHash,
        candidateRecordId -> {
          long logRecordId = convertStoredIdToLogId(candidateRecordId);
          Entry<K, V> entryWithSameKey = valuesLog.read(
            logRecordId,
            _entryBuffer -> entryExternalizer.readIfKeyMatch(_entryBuffer, key)
          );
          if (entryWithSameKey == null) {
            return false; // [record.key != key] => hash collision => look further
          }
          //record with key exists, but with different value
          return true;
        }
      );
      //Don't check _value_ equality here, because
      // a) there should be no duplicated (key,value) entries in the log -- we shouldn't have added such entries
      // b) even if duplicated entries do exist -- lets keep ref to the last one of duplicates in the map, nothing wrong in it

      boolean keyRecordExists = (foundRecordId != DurableIntToMultiIntMap.NO_VALUE);
      if (keyRecordExists) {
        keyHashToEntryOffsetMap.replace(adjustedHash, foundRecordId, storedId);
      }
      else {
        keyHashToEntryOffsetMap.put(adjustedHash, storedId);
      }

      return true;
    });
  }
}
