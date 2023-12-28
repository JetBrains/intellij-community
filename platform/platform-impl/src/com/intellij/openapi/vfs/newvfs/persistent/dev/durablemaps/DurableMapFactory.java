// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.durablemaps;

import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogFactory;
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumerator;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.io.dev.StorageFactory;
import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
import com.intellij.util.io.dev.enumerator.KeyDescriptorEx;
import com.intellij.util.io.dev.intmultimaps.DurableIntToMultiIntMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory.NotClosedProperlyAction.DROP_AND_CREATE_EMPTY_MAP;
import static com.intellij.util.io.IOUtil.MiB;

/**
 * Factory for {@link DurableMapOverAppendOnlyLog} -- use this instead of ctor.
 */
@ApiStatus.Internal
public class DurableMapFactory<K, V> implements StorageFactory<DurableMapOverAppendOnlyLog<K, V>> {

  public static final int DEFAULT_PAGE_SIZE = 8 * MiB;

  public static final StorageFactory<? extends AppendOnlyLog> DEFAULT_VALUES_LOG_FACTORY = AppendOnlyLogFactory
    .withDefaults()
    .pageSize(DEFAULT_PAGE_SIZE)
    .cleanIfFileIncompatible()
    .failIfDataFormatVersionNotMatch(DurableEnumerator.DATA_FORMAT_VERSION);

  public static final StorageFactory<? extends DurableIntToMultiIntMap> DEFAULT_MAP_FACTORY = ExtendibleMapFactory
    .mediumSize()
    .cleanIfFileIncompatible()
    .ifNotClosedProperly(DROP_AND_CREATE_EMPTY_MAP);

  private final StorageFactory<? extends AppendOnlyLog> logFactory;
  private final StorageFactory<? extends DurableIntToMultiIntMap> mapFactory;

  private final KeyDescriptorEx<K> keyDescriptor;
  private final KeyDescriptorEx<V> valueDescriptor;

  private DurableMapFactory(@NotNull StorageFactory<? extends AppendOnlyLog> logFactory,
                            @NotNull StorageFactory<? extends DurableIntToMultiIntMap> mapFactory,
                            @NotNull KeyDescriptorEx<K> keyDescriptor,
                            @NotNull KeyDescriptorEx<V> valueDescriptor) {
    this.logFactory = logFactory;
    this.mapFactory = mapFactory;
    this.keyDescriptor = keyDescriptor;
    this.valueDescriptor = valueDescriptor;
  }

  public static <K, V> @NotNull DurableMapFactory<K, V> withDefaults(@NotNull KeyDescriptorEx<K> keyDescriptor,
                                                                     @NotNull KeyDescriptorEx<V> valueDescriptor) {
    return new DurableMapFactory<>(DEFAULT_VALUES_LOG_FACTORY, DEFAULT_MAP_FACTORY,
                                   keyDescriptor, valueDescriptor);
  }

  public DurableMapFactory<K,V> logFactory(@NotNull StorageFactory<? extends AppendOnlyLog> logFactory){
    return new DurableMapFactory<>(logFactory, mapFactory, keyDescriptor, valueDescriptor);
  }

  public DurableMapFactory<K,V> mapFactory(@NotNull StorageFactory<? extends DurableIntToMultiIntMap> mapFactory){
    return new DurableMapFactory<>(logFactory, mapFactory, keyDescriptor, valueDescriptor);
  }

  @Override
  public @NotNull DurableMapOverAppendOnlyLog<K, V> open(@NotNull Path storagePath) throws IOException {
    Path mapPath = storagePath.resolveSibling(storagePath.getFileName() + ".map");
    return logFactory.wrapStorageSafely(
      storagePath,
      entriesLog -> mapFactory.wrapStorageSafely(
        mapPath,
        keyHashToEntryOffsetMap -> new DurableMapOverAppendOnlyLog<>(
          entriesLog,
          keyHashToEntryOffsetMap,
          keyDescriptor,
          valueDescriptor
        )
      )
    );
  }
}
