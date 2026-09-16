// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.dev;

import com.intellij.platform.util.io.storages.CommonKeyDescriptors;
import com.intellij.platform.util.io.storages.KeyValueStoreScenarios.StringKeyScenarios;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.appendonlylog.dev.ChunkedAppendOnlyLogOverMMappedFile;
import com.intellij.platform.util.io.storages.durablemap.DurableMapTestBase;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.BasicScenarios;
import com.intellij.platform.util.io.storages.durablemap.DurableMapScenarios.LookupCorruptionRecoveryScenarios;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;

@SuppressWarnings("JUnitTestCaseWithNoTests")
public class DurableMapWithAppendableValuesTest
  extends DurableMapTestBase<String, Set<Integer>, DurableMapWithAppendableValues<String, Integer>>
  implements BasicScenarios<String, Set<Integer>, DurableMapWithAppendableValues<String, Integer>>,
             StringKeyScenarios<Set<Integer>, DurableMapWithAppendableValues<String, Integer>>,
             LookupCorruptionRecoveryScenarios<String, Set<Integer>, DurableMapWithAppendableValues<String, Integer>> {

  public static final @NotNull IntFunction<Map.Entry<String, Set<Integer>>> SUBSTRATE_DECODER = substrate -> {
    String key = String.valueOf(substrate);
    Set<Integer> values = new HashSet<>();
    for (int i = 0; i < substrate % 64; i++) {
      values.add(i * substrate);
    }
    return Map.entry(key, values);
  };

  public DurableMapWithAppendableValuesTest() {
    super(SUBSTRATE_DECODER);
  }

  @Override
  protected @NotNull StorageFactory<? extends DurableMapWithAppendableValues<String, Integer>> factory() {
    return storagePath -> {
      ChunkedAppendOnlyLogOverMMappedFile chunkedLog = MMappedFileStorageFactory
        .withDefaults()
        .wrapStorageSafely(storagePath, ChunkedAppendOnlyLogOverMMappedFile::new);

      ExtendibleHashMap map = ExtendibleMapFactory
        .mediumSize()
        .open(storagePath.resolveSibling(storagePath.getFileName() + ".map"));

      return new DurableMapWithAppendableValues<>(
        chunkedLog,
        map,
        stringAsUTF8(),
        CommonKeyDescriptors.integer()
      );
    };
  }

  @Override
  protected boolean isAppendOnly() {
    return false;
  }
}
