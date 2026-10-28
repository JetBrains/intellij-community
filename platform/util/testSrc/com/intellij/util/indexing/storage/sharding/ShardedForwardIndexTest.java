// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage.sharding;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.durablemap.DurableMapFactory;
import com.intellij.util.indexing.InputMapExternalizer;
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.ShardableManyKeysIntegerToIntegerIndexExtension;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputIndexDataExternalizer;
import com.intellij.util.indexing.impl.forward.MapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.indexing.impl.storage.durablemap.DurableMapBasedForwardIndex;
import com.intellij.util.io.ByteSequenceDataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardedForwardIndexTest {
  @Test
  void enumeratesAllShardsAndStopsEarly(@TempDir Path directory) throws IOException {
    var extension = new ShardableManyKeysIntegerToIntegerIndexExtension();
    var accessor = accessor(extension);
    try (var index = new ShardedForwardIndex(extension,
                                             shardNo -> new PersistentMapBasedForwardIndex(directory.resolve("shard-" + shardNo), false))) {
      index.put(1, accessor.serializeIndexedData(new InputData<>(Map.of(10, 1, 11, 1))));
      index.put(2, accessor.serializeIndexedData(new InputData<>(Map.of(20, 2))));
      index.put(3, accessor.serializeIndexedData(new InputData<>(Map.of(30, 3))));
      index.put(4, new ByteArraySequence(new byte[0]));
      index.put(2, null);

      var keys = new HashSet<Integer>();
      assertTrue(index.processFilteredKeys(id -> id <= 3, accessor, keys::add));
      assertEquals(Set.of(10, 11, 30), keys, "The traversal must skip removed files and filter before decoding values");

      int[] visited = {0};
      assertFalse(index.processFilteredKeys(id -> id <= 3, accessor, _ -> {
        visited[0]++;
        return false;
      }));
      assertEquals(1, visited[0], "The traversal must stop before it reads another shard");
      assertThrows(IOException.class, () -> index.processFilteredKeys(id -> id == 4, accessor, _ -> true),
                   "A decoding failure must leave the traversal as an IOException");
    }
  }

  @Test
  void enumeratesDurableShards(@TempDir Path directory) throws IOException {
    var extension = new ShardableManyKeysIntegerToIntegerIndexExtension();
    var accessor = accessor(extension);
    var factory = DurableMapFactory.withDefaults(
      KeyDescriptorEx.adapt(EnumeratorIntegerDescriptor.INSTANCE),
      DataExternalizerEx.adapt(ByteSequenceDataExternalizer.INSTANCE)
    );
    try (var index = new ShardedForwardIndex(extension,
                                             shardNo -> new DurableMapBasedForwardIndex(directory.resolve("shard-" + shardNo), factory))) {
      index.put(3, accessor.serializeIndexedData(new InputData<>(Map.of(30, 3))));
      index.put(4, accessor.serializeIndexedData(new InputData<>(Map.of(40, 4))));
      index.put(5, accessor.serializeIndexedData(new InputData<>(Map.of(50, 5))));
      index.put(6, new ByteArraySequence(new byte[0]));
      index.put(4, null);

      var keys = new HashSet<Integer>();
      assertTrue(index.processFilteredKeys(id -> id <= 5, accessor, keys::add));
      assertEquals(Set.of(30, 50), keys, "The traversal must include current values from every durable shard");
      assertFalse(index.processFilteredKeys(id -> id <= 5, accessor, _ -> false),
                  "The traversal must stop when the processor returns false");
      assertThrows(IOException.class, () -> index.processFilteredKeys(id -> id == 6, accessor, _ -> true),
                   "A decoding failure must leave the traversal as an IOException");
    }
  }

  private static MapForwardIndexAccessor<Integer, Integer> accessor(ShardableManyKeysIntegerToIntegerIndexExtension extension) {
    return new MapForwardIndexAccessor<>(new InputMapExternalizer<>(
      new InputIndexDataExternalizer<>(extension.getKeyDescriptor(), extension.getName()), extension.getValueExternalizer(), false
    ));
  }
}
