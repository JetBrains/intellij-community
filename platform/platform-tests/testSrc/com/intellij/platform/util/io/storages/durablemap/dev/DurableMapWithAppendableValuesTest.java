// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.dev;

import com.intellij.platform.util.io.storages.CommonKeyDescriptors;
import com.intellij.platform.util.io.storages.appendonlylog.dev.ChunkedAppendOnlyLogOverMMappedFile;
import com.intellij.platform.util.io.storages.durablemap.DurableMapTestBase;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;

public class DurableMapWithAppendableValuesTest
  extends DurableMapTestBase<String, Set<Integer>, DurableMapWithAppendableValues<String, Integer>> {

  public static final @NotNull IntFunction<Map.Entry<String, Set<Integer>>> SUBSTRATE_DECODER = substrate -> {
    String key = String.valueOf(substrate);
    Set<Integer> values = new HashSet<>();
    for (int i = 0; i < substrate % 64; i++) {
      values.add(i * substrate);
    }
    return Map.entry(key, values);
  };

  @Override
  protected @NotNull StorageFactory<? extends DurableMapWithAppendableValues<String, Integer>> factory() {
    return storagePath -> {
      ChunkedAppendOnlyLogOverMMappedFile chunkedLog = MMappedFileStorageFactory
        .withDefaults()
        .wrapStorageSafely(storagePath, ChunkedAppendOnlyLogOverMMappedFile::new);

      ExtendibleHashMap map = ExtendibleMapFactory
        .mediumSize()
        .open(storagePath.resolve(storagePath.getFileName().toAbsolutePath() + ".hashToId"));

      return new DurableMapWithAppendableValues<>(
        chunkedLog,
        map,
        stringAsUTF8(),
        CommonKeyDescriptors.integer()
      );
    };
  }

  public DurableMapWithAppendableValuesTest() {
    super(SUBSTRATE_DECODER);
  }

  @Override
  @Disabled("Compaction is not implemented yet for this map")
  public void compactionReturnsMapWithSameMapping_afterManyDifferentMappingsPut(@TempDir Path tempDir) throws Exception {
    super.compactionReturnsMapWithSameMapping_afterManyDifferentMappingsPut(tempDir);
  }

  @Override
  @Disabled("Compaction is not implemented yet for this map")
  public void compactionReturnsMapWithLastMapping_afterManyManyValuesWereOverwritten(@TempDir Path tempDir) throws Exception {
    super.compactionReturnsMapWithLastMapping_afterManyManyValuesWereOverwritten(tempDir);
  }
}
