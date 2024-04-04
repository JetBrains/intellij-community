// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.durablemaps;

import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.ChunkedAppendOnlyLogOverMMappedFile;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleHashMap;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.io.dev.StorageFactory;
import com.intellij.util.io.dev.enumerator.KeyDescriptorEx;
import com.intellij.util.io.dev.enumerator.StringAsUTF8;
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

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

  public static final KeyDescriptorEx<Integer> INT_DESCRIPTOR = new KeyDescriptorEx<>() {
    @Override
    public int getHashCode(Integer value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(Integer key1, Integer key2) {
      return key1.equals(key2);
    }

    @Override
    public Integer read(@NotNull ByteBuffer input) throws IOException {
      return input.getInt();
    }

    @Override
    public KnownSizeRecordWriter writerFor(@NotNull Integer value) throws IOException {
      return new KnownSizeRecordWriter() {
        @Override
        public ByteBuffer write(@NotNull ByteBuffer data) throws IOException {
          return data.putInt(value);
        }

        @Override
        public int recordSize() {
          return Integer.BYTES;
        }
      };
    }
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
        StringAsUTF8.INSTANCE,
        INT_DESCRIPTOR
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
