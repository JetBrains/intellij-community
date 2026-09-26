// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.KeyValueStoreScenarios;
import com.intellij.platform.util.io.storages.KeyValueTestData;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Contains reusable test scenarios (unit5 test-interfaces) for durable map implementations
public final class DurableMapScenarios {

  private DurableMapScenarios() { }

  public interface BasicScenarios<K, V, M extends DurableMap<K, V>>
    extends DurableMapTestContext<K, V, M>, KeyValueStoreScenarios.BasicScenarios<K, V, M> {
    @Test
    default void initially_MapIsEmpty() throws IOException {
      assertTrue(storageUnderTest().isEmpty());
      assertEquals(0, storageUnderTest().size());
    }

    @Test
    default void initially_MapIsNotClosed() {
      assertFalse(storageUnderTest().isClosed(),
                  "Map should be !closed initially");
    }

    @Test
    default void closedMap_reportedAsClosed() throws IOException {
      storageUnderTest().close();
      assertTrue(storageUnderTest().isClosed(),
                 "Map should report itself closed after .close()");
    }

    @Test
    default void containsMappingReturnsTrue_forSingleMappingPut(KeyValueTestData<K, V> testData) throws IOException {
      Map.Entry<K, V> entry = testData.keyValue(43);
      K key = entry.getKey();
      V value = entry.getValue();

      assertNull(storageUnderTest().get(key),
                 "Empty store contains no keys");

      storageUnderTest().put(key, value);

      assertTrue(storageUnderTest().containsMapping(key));
    }

    @Test
    default void ifSingleMappingPut_AndRemoved_containsMappingReturnsFalse(KeyValueTestData<K, V> testData) throws IOException {
      Map.Entry<K, V> entry = testData.keyValue(43);
      K key = entry.getKey();
      V value = entry.getValue();

      assertNull(storageUnderTest().get(key),
                 "Empty store contains no keys");

      storageUnderTest().put(key, value);
      storageUnderTest().remove(key);

      assertFalse(storageUnderTest().containsMapping(key),
                  "Must be no mapping since it was just removed");
    }

    @Test
    default void secondPutWithSameKey_overridesValuePreviouslyPut(KeyValueTestData<K, V> testData) throws IOException {
      Map.Entry<K, V> entry = testData.keyValue(43);
      K key = entry.getKey();
      V value = entry.getValue();

      storageUnderTest().put(key, value);

      V anotherValue = testData.keyValue(45).getValue();
      storageUnderTest().put(key, anotherValue);

      assertEquals(anotherValue,
                   storageUnderTest().get(key),
                   "New value put must overwrite previous one");
    }

    @Test
    default void forManyMappings_Put_ContainsMappingReturnsTrue(KeyValueTestData<K, V> testData) throws IOException {
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        storageUnderTest().put(entry.getKey(), entry.getValue());
      }

      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        assertTrue(storageUnderTest().containsMapping(key),
                   "store[" + key + "] must contain mapping");
      }
    }

    @Test
    default void emptyMap_containsNothing(KeyValueTestData<K, V> testData) throws IOException {
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        assertFalse(storageUnderTest().containsMapping(entry.getKey()),
                    "Empty map must contain nothing"
        );
      }
    }

    @Test
    default void forManyMappings_Put_processKeys_ListsAllTheKeysAdded(KeyValueTestData<K, V> testData) throws IOException {
      Set<K> addedKeys = CollectionFactory.createSmallMemoryFootprintSet();
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        storageUnderTest().put(entry.getKey(), entry.getValue());

        addedKeys.add(entry.getKey());
      }

      List<K> keysReportedByProcessKeys = new ArrayList<>();
      storageUnderTest().processKeys(key -> keysReportedByProcessKeys.add(key));

      assertEquals(
        addedKeys.size(),
        keysReportedByProcessKeys.size(),
        ".processKeys() must return same number of keys, as were added"
      );

      assertEquals(
        addedKeys,
        CollectionFactory.createSmallMemoryFootprintSet(keysReportedByProcessKeys),
        ".processKeys() must return same keys, as were added"
      );
    }

    @Test
    default void storageIsEmpty_afterManyMappingsPut_AndRemoved(KeyValueTestData<K, V> testData) throws IOException {
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        storageUnderTest().put(key, value);

        assertTrue(storageUnderTest().containsMapping(key),
                   "store[" + key + "] must contain mapping after .put()");

        storageUnderTest().remove(key);

        assertFalse(storageUnderTest().containsMapping(key),
                    "store[" + key + "] must NOT contain mapping after .remove()");

        assertNull(storageUnderTest().get(key),
                   "store[" + key + "] must return null after .remove()");
      }

      assertEquals(0,
                   storageUnderTest().size(),
                   "Storage must be empty after removing all the entries");
      assertTrue(storageUnderTest().isEmpty(),
                 "Storage must be empty after removing all the entries");

      HashSet<K> keys = new HashSet<>();
      storageUnderTest().processKeys(key -> {
        keys.add(key);
        return true;
      });
      assertTrue(keys.isEmpty(),
                 ".processKeys() must list nothing after removing all the entries");
    }

    @Test
    default void forManyMappings_putWithSameKey_overridesValuesPreviouslyPut(KeyValueTestData<K, V> testData) throws IOException {
      //store original values:
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        storageUnderTest().put(key, value);
      }

      //overwrite with new values:
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V newValue = testData.differentValue(substrate, 17);

        storageUnderTest().put(key, newValue);
      }

      //check new values is returned:
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V expectedValue = testData.differentValue(substrate, 17);
        assertEquals(expectedValue,
                     storageUnderTest().get(key),
                     "store[" + key + "] must return value from last put()");
      }
    }

    @Test
    default void forManyMappings_AfterPutAndRemove_containsMappingReturnsFalse(KeyValueTestData<K, V> testData) throws IOException {
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        storageUnderTest().put(key, value);

        assertTrue(storageUnderTest().containsMapping(key),
                   "store[" + key + "] must contain mapping after .put()");

        storageUnderTest().remove(key);

        assertFalse(storageUnderTest().containsMapping(key),
                    "store[" + key + "] must NOT contain mapping after .remove()");

        assertNull(storageUnderTest().get(key),
                   "store[" + key + "] must return null after .remove()");
      }

      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        assertFalse(storageUnderTest().containsMapping(key),
                    "store[" + key + "] must NOT contain mapping after .remove()");

        assertNull(storageUnderTest().get(key),
                   "store[" + key + "] must return null after .remove()");
      }
    }

    @Test
    default void forEach_ListsAllEntriesThatWerePutBefore(KeyValueTestData<K, V> testData) throws IOException {
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        storageUnderTest().put(key, value);
      }

      List<Map.Entry<K, V>> entries = listAllEntries(storageUnderTest());
      Set<Map.Entry<K, V>> entriesSet = CollectionFactory.createSmallMemoryFootprintSet(entries);
      assertEquals(
        entries.size(),
        entriesSet.size(),
        ".forEachEntry() must list entries without duplicates"
      );
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        assertTrue(
          entriesSet.contains(entry),
          () -> "forEach() must list the " + entry + " that was put"
        );
      }
    }

    @Test
    default void forEach_ListsSingleMappingPut(KeyValueTestData<K, V> testData) throws IOException {
      Map.Entry<K, V> entry = testData.keyValue(42);
      K key = entry.getKey();
      V value = entry.getValue();

      storageUnderTest().put(key, value);
      List<Map.Entry<K, V>> entries = listAllEntries(storageUnderTest());
      assertEquals(
        List.of(Map.entry(key, value)),
        entries,
        "forEach() must list the single entry that was put"
      );
    }

    @Test
    default void forEach_ListsNothing_afterManyMappingsPut_AndRemoved(KeyValueTestData<K, V> testData) throws IOException {
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        storageUnderTest().put(key, value);

        assertTrue(storageUnderTest().containsMapping(key),
                   "store[" + key + "] must contain mapping after .put()");

        storageUnderTest().remove(key);

        assertFalse(storageUnderTest().containsMapping(key),
                    "store[" + key + "] must NOT contain mapping after .remove()");

        assertNull(storageUnderTest().get(key),
                   "store[" + key + "] must return null after .remove()");
      }

      List<Map.Entry<K, V>> entries = listAllEntries(storageUnderTest());
      assertTrue(entries.isEmpty(),
                 ".forEachEntry() must list nothing after removing all the entries");
    }

    private static <K, V> @NotNull List<Map.Entry<K, V>> listAllEntries(@NotNull DurableMap<K, V> storage) throws IOException {
      List<Map.Entry<K, V>> entries = new ArrayList<>();
      storage.forEachEntry((key, value) -> {
        entries.add(Map.entry(key, value));
        return true;
      });
      return entries;
    }
  }

  public interface CompactionScenarios<K, V, M extends DurableMap<K, V>> extends DurableMapTestContext<K, V, M> {
    @Test
    default void compactionReturnsMapWithSameMapping_afterManyDifferentMappingsPut(@TempDir Path tempDir,
                                                                                   KeyValueTestData<K, V> testData) throws Exception {
      M originalStorage = storageUnderTest();
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        originalStorage.put(entry.getKey(), entry.getValue());
      }
      assertTrue(
        originalStorage.compactionScore().compactionNotNeeded(),
        "No overrides/removes yet -- should NOT be a need for compaction"
      );

      Path compactedStoragePath = tempDir.resolve("compacted-storage");
      M compactedStorage = originalStorage.compact(() -> openStorage(compactedStoragePath));
      try {
        assertTrue(
          compactedStorage.compactionScore().compactionNotNeeded(),
          "Map just compacted -- should NOT be a need for another compaction: " + originalStorage.compactionScore()
        );
        assertEquals(
          originalStorage.size(),
          compactedStorage.size(),
          "Compacted map must have same size"
        );

        for (int substrate : testData.substrate()) {
          Map.Entry<K, V> entry = testData.keyValue(substrate);
          K key = entry.getKey();
          V value = entry.getValue();
          assertEquals(value,
                       compactedStorage.get(key),
                       "compacted map[" + key + "] must contain the same mapping as original");
        }
      }
      finally {
        closeStorage(compactedStorage);
      }
    }

    @Test
    default void compactionReturnsMapWithLastMapping_afterManyManyValuesWereOverwritten(@TempDir Path tempDir,
                                                                                        KeyValueTestData<K, V> testData) throws Exception {
      //store original values:
      int[] keyValuesSubstrate = testData.substrate();
      M originalStorage = storageUnderTest();
      for (int substrate : keyValuesSubstrate) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        originalStorage.put(entry.getKey(), entry.getValue());
      }
      //overwrite with new values:
      for (int substrate : keyValuesSubstrate) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V newValue = testData.differentValue(substrate, 17);

        originalStorage.put(key, newValue);
      }

      if (isAppendOnlyStorage()) {
        //append-only map should have ~50% space wasted after all the keys were overwritten:
        assertFalse(
          originalStorage.compactionScore().compactionNotNeeded(),
          "There are quite a lot of of wasted space in map, compaction can't be 'not needed' at all: " +
          originalStorage.compactionScore()
        );
      }

      Path compactedStoragePath = tempDir.resolve("compacted-storage");
      M compactedStorage = originalStorage.compact(
        () -> openStorage(compactedStoragePath)
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
          Map.Entry<K, V> entry = testData.keyValue(substrate);
          K key = entry.getKey();
          V value = testData.differentValue(substrate, 17);
          assertEquals(value,
                       compactedStorage.get(key),
                       "compacted map[" + key + "] must contain the most recent value from original");
        }
      }
      finally {
        closeStorage(compactedStorage);
      }
    }
  }

  public interface LookupLossRecoveryScenarios<K, V, M extends DurableMap<K, V>> extends DurableMapTestContext<K, V, M> {
    @Test
    default void mapContent_WithManyMappingsAddedAndRemoved_CouldBeRestored_ifHashToIdMapping_IsLost(
      KeyValueTestData<K, V> testData
    ) throws IOException {
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        storageUnderTest().put(key, value);
        storageUnderTest().remove(key);
      }
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        storageUnderTest().put(key, value);
      }

      reopenDroppingDurableLookupPart();

      assertEquals(
        testData.substrate().length,
        storageUnderTest().size(),
        "Storage must contain " + testData.substrate().length + " items"
      );
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        assertEquals(
          storageUnderTest().get(key),
          value,
          "Storage must contain (" + key + ", " + value + ") entry"
        );
      }
    }
  }

  public interface LookupCorruptionRecoveryScenarios<K, V, M extends DurableMap<K, V>> extends DurableMapTestContext<K, V, M> {
    @Test
    default void mapContent_WithManyMappingsAddedAndRemoved_CouldBeRestored_ifHashToIdMapping_IsCorrupted(
      KeyValueTestData<K, V> testData
    ) throws Exception {
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        storageUnderTest().put(key, value);
        storageUnderTest().remove(key);
      }
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        storageUnderTest().put(key, value);
      }

      reopenAfterImproperClose();

      assertEquals(
        testData.substrate().length,
        storageUnderTest().size(),
        "Storage must contain " + testData.substrate().length + " items"
      );
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        assertEquals(
          storageUnderTest().get(key),
          value,
          "Storage must contain (" + key + ", " + value + ") entry"
        );
      }
    }
  }
}
