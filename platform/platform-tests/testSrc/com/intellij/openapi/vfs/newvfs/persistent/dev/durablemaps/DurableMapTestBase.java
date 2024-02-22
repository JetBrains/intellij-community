// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.durablemaps;

import com.intellij.openapi.vfs.newvfs.persistent.StorageTestingUtils;
import com.intellij.util.io.KeyValueStoreTestBase;
import com.intellij.util.io.dev.durablemaps.Compactable;
import com.intellij.util.io.dev.durablemaps.DurableMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public abstract class DurableMapTestBase<M extends DurableMap<String, String>> extends KeyValueStoreTestBase<M> {
  //TODO RC: most of the tests are actual for PersistentMap also! So better extract them into
  //         the superclass, if DurableMap/PersistentMap ifaces merge.

  @Test
  public void mapIsInitiallyEmpty() throws IOException {
    assertTrue(storage.isEmpty());
    assertEquals(0, storage.size());
  }

  @Test
  public void containsMappingReturnsTrue_forSingleMappingPut() throws IOException {
    String key = "testKey";
    String value = "testValue";

    assertNull(storage.get(key),
               "Empty store contains no keys");

    storage.put(key, value);

    assertTrue(storage.containsMapping(key));
  }

  @Test
  public void ifSingleMappingPut_AndRemoved_containsMappingReturnsFalse() throws IOException {
    String key = "testKey";
    String value = "testValue";

    assertNull(storage.get(key),
               "Empty store contains no keys");

    storage.put(key, value);
    storage.remove(key);

    assertFalse(storage.containsMapping(key),
                "Must be no mapping since it was just removed");
  }

  @Test
  public void secondPutWithSameKey_overridesValuePreviouslyPut() throws IOException {
    String key = "testKey";
    String value = "testValue";

    storage.put(key, value);

    String anotherValue = "anotherTestValue";
    storage.put(key, anotherValue);

    assertEquals(anotherValue,
                 storage.get(key),
                 "New value put must overwrite previous one");
  }

  @Test
  public void compactionReturnsMapWithSameMapping_afterManyDifferentMappingsPut(@TempDir Path tempDir) throws Exception {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      storage.put(entry.getKey(), entry.getValue());
    }
    assertTrue(
      storage.compactionScore().compactionNotNeeded(),
      "No overrides/removes yet -- should NOT be a need for compaction"
    );

    Path compactedStoragePath = tempDir.resolve("compacted-storage");
    M compactedStorage = storage.compact(() -> factory().open(compactedStoragePath));
    try {
      assertTrue(
        compactedStorage.compactionScore().compactionNotNeeded(),
        "Map just compacted -- should NOT be a need for another compaction: " + storage.compactionScore()
      );
      assertEquals(
        compactedStorage.size(),
        compactedStorage.size(),
        "Compacted map must have same size"
      );

      for (int substrate : keyValuesSubstrate) {
        Map.Entry<String, String> entry = keyValue(substrate);
        String key = entry.getKey();
        String value = entry.getValue();
        assertEquals(value,
                     compactedStorage.get(key),
                     "compacted map[" + key + "] must contain the same mapping as original");
      }
    }
    finally {
      StorageTestingUtils.bestEffortToCloseAndUnmap(compactedStorage);
    }
  }

  @Test
  public void compactionReturnsMapWithLastMapping_afterManyManyValuesWereOverwritten(@TempDir Path tempDir) throws Exception {
    //store original values:
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      storage.put(entry.getKey(), entry.getValue());
    }
    //overwrite with new values:
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String newValue = entry.getValue() + "_random_Suffix";

      storage.put(key, newValue);
    }

    assertFalse(
      storage.compactionScore().compactionNotNeeded(),
      "There are quite a lot of of wasted space in map, compaction can't be 'not needed' at all: " + storage.compactionScore()
    );

    Path compactedStoragePath = tempDir.resolve("compacted-storage");
    M compactedStorage = storage.compact(
      () -> factory().open(compactedStoragePath)
    );
    try {
      Compactable.CompactionScore compactionScore = compactedStorage.compactionScore();
      assertTrue(
        compactionScore.compactionNotNeeded(),
        "Map just compacted must NOT need compaction again: " + compactionScore
      );
      assertEquals(
        keyValuesSubstrate.length,
        compactedStorage.size(),
        "Compacted map must have size of records being put"
      );

      for (int substrate : keyValuesSubstrate) {
        Map.Entry<String, String> entry = keyValue(substrate);
        String key = entry.getKey();
        String value = entry.getValue() + "_random_Suffix";
        assertEquals(value,
                     compactedStorage.get(key),
                     "compacted map[" + key + "] must contain the most recent value from original");
      }
    }
    finally {
      StorageTestingUtils.bestEffortToCloseAndUnmap(compactedStorage);
    }
  }


  @Test
  public void forManyMappings_Put_ContainsMappingReturnsTrue() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      storage.put(entry.getKey(), entry.getValue());
    }

    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      //String value = entry.getValue();
      assertTrue(storage.containsMapping(key),
                 "store[" + key + "] must contain mapping");
    }
  }

  @Test
  public void storageIsEmpty_afterManyMappingsPut_AndRemoved() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();

      storage.put(key, value);

      assertTrue(storage.containsMapping(key),
                 "store[" + key + "] must contain mapping after .put()");

      storage.remove(key);

      assertFalse(storage.containsMapping(key),
                  "store[" + key + "] must NOT contain mapping after .remove()");

      assertNull(storage.get(key),
                 "store[" + key + "] must return null after .remove()");
    }

    assertEquals(0,
                 storage.size(),
                 "Storage must be empty after removing all the entries");
    assertTrue(storage.isEmpty(),
               "Storage must be empty after removing all the entries");

    HashSet<String> keys = new HashSet<>();
    storage.processKeys(key -> {
      keys.add(key);
      return true;
    });
    assertTrue(keys.isEmpty(),
               ".processKeys() must list nothing after removing all the entries");
  }

  @Test
  public void forManyMappings_putWithSameKey_overridesValuesPreviouslyPut() throws IOException {
    //store original values:
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();

      storage.put(key, value);
    }

    //overwrite with new values:
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String newValue = entry.getValue() + "_random_Suffix";

      storage.put(key, newValue);
    }

    //check new values is returned:
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String expectedValue = entry.getValue() + "_random_Suffix";
      assertEquals(expectedValue,
                   storage.get(key),
                   "store[" + key + "] must return value from last put()");
    }
  }

  @Test
  public void forManyMappings_AfterPutAndRemove_containsMappingReturnsFalse() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();

      storage.put(key, value);

      assertTrue(storage.containsMapping(key),
                 "store[" + key + "] must contain mapping after .put()");

      storage.remove(key);

      assertFalse(storage.containsMapping(key),
                  "store[" + key + "] must NOT contain mapping after .remove()");

      assertNull(storage.get(key),
                 "store[" + key + "] must return null after .remove()");
    }

    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      assertFalse(storage.containsMapping(key),
                  "store[" + key + "] must NOT contain mapping after .remove()");

      assertNull(storage.get(key),
                 "store[" + key + "] must return null after .remove()");
    }
  }
}
