// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

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
import static com.intellij.util.io.IOUtil.MiB;

/**
 *
 */
@ApiStatus.Internal
public class DurableEnumeratorFactory<V> implements StorageFactory<DurableEnumerator<V>> {
  public static final int DEFAULT_PAGE_SIZE = 8 * MiB;

  public static final StorageFactory<DurableIntToMultiIntMap> DEFAULT_IN_MEMORY_MAP_FACTORY =
    (storagePath) -> new NonDurableNonParallelIntToMultiIntMap();

  public static final StorageFactory<? extends AppendOnlyLog> DEFAULT_VALUES_LOG_FACTORY = AppendOnlyLogFactory
    .withPageSize(DEFAULT_PAGE_SIZE)
    .failIfDataFormatVersionNotMatch(DurableEnumerator.DATA_FORMAT_VERSION);

  public static final StorageFactory<? extends DurableIntToMultiIntMap> DEFAULT_DURABLE_MAP_FACTORY = ExtendibleMapFactory.defaults();

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
          //TODO RC: We could recover the map from valuesLog -- but we need valueHashToIdFactory.clean() to remove
          //         the files, and reopen from 0

          if (rebuildMapFromLogIfInconsistent) {
            if (!valuesLog.isEmpty() && valueHashToId.isEmpty()) {
              fillValueHashToIdMap(valuesLog, valueDescriptor, valueHashToId);
            }
            //TODO RC: check other (potential) inconsistencies:
            //         log was recovered,
            //         valueHashToIdMap has 'not closed properly' marker, etc
            //         ...but to rebuild map in those cases we need .clean() or .createFromScratch() method for map!
            //MAYBE separate 'always rebuild map' and 'rebuild map if inconsistent'
            //      (both requires .open(path,CREATE_NEW) method)
          }

          //TODO RC: valueHashToId could be loaded async -- to not delay initialization (see DurableStringEnumerator)

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
