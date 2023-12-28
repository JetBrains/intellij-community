// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogFactory;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.io.dev.StorageFactory;
import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
import com.intellij.util.io.dev.enumerator.KeyDescriptorEx;
import com.intellij.util.io.dev.intmultimaps.DurableIntToMultiIntMap;
import com.intellij.util.io.dev.intmultimaps.NonDurableNonParallelIntToMultiIntMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumerator.fillValueHashToIdMap;
import static com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory.NotClosedProperlyAction.DROP_AND_CREATE_EMPTY_MAP;
import static com.intellij.util.io.IOUtil.MiB;

@ApiStatus.Internal
public class DurableEnumeratorFactory<V> implements StorageFactory<DurableEnumerator<V>> {
  private static final Logger LOG = Logger.getInstance(DurableEnumeratorFactory.class);

  public static final int DEFAULT_PAGE_SIZE = 8 * MiB;

  public static final StorageFactory<DurableIntToMultiIntMap> DEFAULT_IN_MEMORY_MAP_FACTORY =
    (storagePath) -> new NonDurableNonParallelIntToMultiIntMap();

  public static final StorageFactory<? extends AppendOnlyLog> DEFAULT_VALUES_LOG_FACTORY = AppendOnlyLogFactory
    .withDefaults()
    .pageSize(DEFAULT_PAGE_SIZE)
    .cleanIfFileIncompatible()
    .failIfDataFormatVersionNotMatch(DurableEnumerator.DATA_FORMAT_VERSION);

  public static final StorageFactory<? extends DurableIntToMultiIntMap> DEFAULT_DURABLE_MAP_FACTORY = ExtendibleMapFactory.mediumSize()
    .cleanIfFileIncompatible()
    .ifNotClosedProperly(DROP_AND_CREATE_EMPTY_MAP);

  public static final String MAP_FILE_SUFFIX = ".hashToId";


  private final @NotNull KeyDescriptorEx<V> valueDescriptor;

  private final @NotNull StorageFactory<? extends AppendOnlyLog> valuesLogFactory;
  private final @NotNull StorageFactory<? extends DurableIntToMultiIntMap> valueHashToIdFactory;

  private final @NotNull String mapFileSuffix;

  private final boolean rebuildMapFromLogIfInconsistent;


  private DurableEnumeratorFactory(@NotNull KeyDescriptorEx<V> valueDescriptor,
                                   @NotNull StorageFactory<? extends AppendOnlyLog> valuesLogFactory,
                                   @NotNull StorageFactory<? extends DurableIntToMultiIntMap> valueHashToIdFactory,
                                   boolean rebuildMapFromLogIfInconsistent,
                                   @NotNull String mapFileSuffix) {
    this.valueDescriptor = valueDescriptor;
    this.valuesLogFactory = valuesLogFactory;
    this.valueHashToIdFactory = valueHashToIdFactory;
    this.rebuildMapFromLogIfInconsistent = rebuildMapFromLogIfInconsistent;
    this.mapFileSuffix = mapFileSuffix;
  }

  public static <V> DurableEnumeratorFactory<V> defaultWithDurableMap(@NotNull KeyDescriptorEx<V> valueDescriptor) {
    return new DurableEnumeratorFactory<>(
      valueDescriptor,
      DEFAULT_VALUES_LOG_FACTORY,
      DEFAULT_DURABLE_MAP_FACTORY,
      /*rebuildMapIfInconsistent: */ true,
      MAP_FILE_SUFFIX
    );
  }

  public static <V> DurableEnumeratorFactory<V> defaultWithInMemoryMap(@NotNull KeyDescriptorEx<V> valueDescriptor) {
    return new DurableEnumeratorFactory<>(
      valueDescriptor,
      DEFAULT_VALUES_LOG_FACTORY,
      DEFAULT_IN_MEMORY_MAP_FACTORY,
      /*rebuildMapIfInconsistent: */ true,
      MAP_FILE_SUFFIX
    );
  }

  public DurableEnumeratorFactory<V> valuesLogFactory(@NotNull StorageFactory<? extends AppendOnlyLog> valuesLogFactory) {
    return new DurableEnumeratorFactory<>(valueDescriptor, valuesLogFactory, valueHashToIdFactory,
                                          rebuildMapFromLogIfInconsistent, mapFileSuffix);
  }

  public DurableEnumeratorFactory<V> mapFactory(@NotNull StorageFactory<? extends DurableIntToMultiIntMap> valueHashToIdFactory) {
    return new DurableEnumeratorFactory<>(valueDescriptor, valuesLogFactory, valueHashToIdFactory,
                                          rebuildMapFromLogIfInconsistent, mapFileSuffix);
  }

  public DurableEnumeratorFactory<V> rebuildMapIfInconsistent(boolean rebuildMapFromLogIfInconsistent) {
    return new DurableEnumeratorFactory<>(valueDescriptor, valuesLogFactory, valueHashToIdFactory,
                                          rebuildMapFromLogIfInconsistent, mapFileSuffix);
  }

  @Override
  public @NotNull DurableEnumerator<V> open(@NotNull Path storagePath) throws IOException {
    String name = storagePath.getName(storagePath.getNameCount() - 1).toString();
    Path hashToIdPath = storagePath.resolveSibling(name + mapFileSuffix);

    return valuesLogFactory.wrapStorageSafely(
      storagePath,
      valuesLog -> valueHashToIdFactory.wrapStorageSafely(
        hashToIdPath,
        valueHashToId -> {
          if (rebuildMapFromLogIfInconsistent) {
            //If hashToId map is durable, but its factory configured to 'create an empty map if (corrupted, inconsistent,
            // wasn't properly closed...)' -- then this branch rebuilds such a map, hence provides a recovery even for
            // durable maps
            if (!valuesLog.isEmpty() && valueHashToId.isEmpty()) {
              LOG.warn("[" + name + "]: .valueHashToId map is out-of-sync with .valuesLog data (records count don't match) " +
                       "-> rebuilding the map");
              //MAYBE RC: valueHashToId could be build/load async -- to not delay initialization (see DurableStringEnumerator)
              fillValueHashToIdMap(valuesLog, valueDescriptor, valueHashToId);
              LOG.warn("[" + name + "]: .valueHashToId was rebuilt (" + valueHashToId.size() + " records)");
            }
            //TODO RC: what if valuesLog was recovered? -- could it be the .hashToId map is somehow wasClosedProperly,
            //         but still is inconsistent with valuesLog? It seems it could: current implementation of append-only-log
            //         _could_ sometimes lose the written-and-commited record: i.e. one of the previous values in the log
            //         wasn't committed, and even record header wasn't written -- and because of that whole region after
            //         that allocated-but-not-yet-started record is lost. So there _is_ a chance that value is appended
            //         to the log, and valueId is inserted into the map, but value record is lost on crash, so the id
            //         put in the map is now invalid.
            //         So we should either modify AppendOnlyLog so that it doesn't allow that (e.g. we could not return
            //         from allocateRecord until at least header is written) -- or we should rebuild the map even if
            //         the map itself wasClosedProperly, but AppendOnlyLog was recovered, and recovered region >0

            //MAYBE separate 'always rebuild map' and 'rebuild map if inconsistent'
            //      (both requires .open(path,CREATE_NEW) method)
          }


          return new DurableEnumerator<>(
            valueDescriptor,
            valuesLog,
            () -> valueHashToId
          );
        }
      )
    );
  }
}
