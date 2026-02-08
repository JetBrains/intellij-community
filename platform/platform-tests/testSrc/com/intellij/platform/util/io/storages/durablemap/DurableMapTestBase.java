// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.util.io.storages.KeyValueStoreTestBase;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.io.Unmappable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class DurableMapTestBase<K, V, M extends DurableMap<K, V>> extends KeyValueStoreTestBase<K, V, M> {

  protected DurableMapTestBase(@NotNull IntFunction<? extends Map.Entry<K, V>> decoder) {
    super(decoder);
  }

  /** should return true if the map is mostly append-only */
  protected abstract boolean isAppendOnly();

  //TODO RC: most of the tests are actual for PersistentMap also! So better extract them into
  //         the superclass, if DurableMap/PersistentMap ifaces merge.

  @Test
  public void initially_MapIsEmpty() throws IOException {
    assertTrue(storage.isEmpty());
    assertEquals(0, storage.size());
  }

  @Test
  public void initially_MapIsNotClosed() throws IOException {
    assertFalse(storage.isClosed(),
                "Map should be !closed initially");
  }

  @Test
  public void closedMap_reportedAsClosed() throws IOException {
    storage.close();
    assertTrue(storage.isClosed(),
               "Map should report itself closed after .close()");
  }

  @Test
  public void containsMappingReturnsTrue_forSingleMappingPut() throws IOException {
    Map.Entry<K, V> entry = keyValue(43);
    K key = entry.getKey();
    V value = entry.getValue();

    assertNull(storage.get(key),
               "Empty store contains no keys");

    storage.put(key, value);

    assertTrue(storage.containsMapping(key));
  }

  @Test
  public void ifSingleMappingPut_AndRemoved_containsMappingReturnsFalse() throws IOException {
    Map.Entry<K, V> entry = keyValue(43);
    K key = entry.getKey();
    V value = entry.getValue();

    assertNull(storage.get(key),
               "Empty store contains no keys");

    storage.put(key, value);
    storage.remove(key);

    assertFalse(storage.containsMapping(key),
                "Must be no mapping since it was just removed");
  }

  @Test
  public void secondPutWithSameKey_overridesValuePreviouslyPut() throws IOException {
    Map.Entry<K, V> entry = keyValue(43);
    K key = entry.getKey();
    V value = entry.getValue();

    storage.put(key, value);

    V anotherValue = keyValue(45).getValue();
    storage.put(key, anotherValue);

    assertEquals(anotherValue,
                 storage.get(key),
                 "New value put must overwrite previous one");
  }

  @Test
  public void compactionReturnsMapWithSameMapping_afterManyDifferentMappingsPut(@TempDir Path tempDir) throws Exception {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
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
        Map.Entry<K, V> entry = keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();
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
      Map.Entry<K, V> entry = keyValue(substrate);
      storage.put(entry.getKey(), entry.getValue());
    }
    //overwrite with new values:
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V newValue = differentValue(substrate, 17);

      storage.put(key, newValue);
    }

    if (isAppendOnly()) {
      //append-only map should have ~50% space wasted after all the keys were overwritten:
      assertFalse(
        storage.compactionScore().compactionNotNeeded(),
        "There are quite a lot of of wasted space in map, compaction can't be 'not needed' at all: " + storage.compactionScore()
      );
    }

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
        Map.Entry<K, V> entry = keyValue(substrate);
        K key = entry.getKey();
        V value = differentValue(substrate, 17);
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
      Map.Entry<K, V> entry = keyValue(substrate);
      storage.put(entry.getKey(), entry.getValue());
    }

    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      assertTrue(storage.containsMapping(key),
                 "store[" + key + "] must contain mapping");
    }
  }

  @Test
  public void forManyMappings_Put_processKeys_ListsAllTheKeysAdded() throws IOException {
    Set<K> addedKeys = CollectionFactory.createSmallMemoryFootprintSet();
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      storage.put(entry.getKey(), entry.getValue());
      
      addedKeys.add(entry.getKey());
    }

    List<K> keysReportedByProcessKeys = new ArrayList<>();
    storage.processKeys(key -> keysReportedByProcessKeys.add(key));
    
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
  public void storageIsEmpty_afterManyMappingsPut_AndRemoved() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

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

    HashSet<K> keys = new HashSet<>();
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
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

      storage.put(key, value);
    }

    //overwrite with new values:
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V newValue = differentValue(substrate, 17);

      storage.put(key, newValue);
    }

    //check new values is returned:
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V expectedValue = differentValue(substrate, 17);
      assertEquals(expectedValue,
                   storage.get(key),
                   "store[" + key + "] must return value from last put()");
    }
  }

  @Test
  public void forManyMappings_AfterPutAndRemove_containsMappingReturnsFalse() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

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
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      assertFalse(storage.containsMapping(key),
                  "store[" + key + "] must NOT contain mapping after .remove()");

      assertNull(storage.get(key),
                 "store[" + key + "] must return null after .remove()");
    }
  }


  @Test
  public void forEach_ListsAllEntriesThatWerePutBefore() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

      storage.put(key, value);
    }

    List<Map.Entry<K, V>> entries = listAllEntries(storage);
    Set<Map.Entry<K, V>> entiresSet = CollectionFactory.createSmallMemoryFootprintSet(entries);
    assertEquals(
      entries.size(),
      entiresSet.size(),
      ".forEachEntry() must list entries without duplicates"
    );
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      assertTrue(
        entiresSet.contains(entry),
        () -> "forEach() must list the " + entry + " that was put"
      );
    }
  }

  @Test
  public void forEach_ListsSingleMappingPut() throws IOException {
    Map.Entry<K, V> entry = keyValue(42);
    K key = entry.getKey();
    V value = entry.getValue();

    storage.put(key, value);
    List<Map.Entry<K, V>> entries = listAllEntries(storage);
    assertEquals(
      List.of(Map.entry(key, value)),
      entries,
      "forEach() must list the single entry that was put"
    );
  }

  @Test
  public void forEach_ListsNothing_afterManyMappingsPut_AndRemoved() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

      storage.put(key, value);

      assertTrue(storage.containsMapping(key),
                 "store[" + key + "] must contain mapping after .put()");

      storage.remove(key);

      assertFalse(storage.containsMapping(key),
                  "store[" + key + "] must NOT contain mapping after .remove()");

      assertNull(storage.get(key),
                 "store[" + key + "] must return null after .remove()");
    }

    List<Map.Entry<K, V>> entries = listAllEntries(storage);
    assertTrue(entries.isEmpty(),
               ".forEachEntry() must list nothing after removing all the entries");
  }

  @Test
  public void mapContent_WithManyMappingsAddedAndRemoved_CouldBeRestored_ifHashToIdMapping_IsLost() throws IOException {
    //RC: real scenario we're testing is 'improper termination' there ExtendibleMap content is corrupted, hence
    //    needs to be re-created from main entriesLog (see DurableMapFactory.rebuildMapFromLogIfInconsistent branch).

    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

      storage.put(key, value);
      storage.remove(key);
    }
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

      storage.put(key, value);
    }

    ((Unmappable)storage).closeAndUnsafelyUnmap();

    //force a recovery: remove .map file, and reopen storage
    {
      //In this test I want to test recovery itself, so don't bother emulating map corruption -- and just remove the whole
      //   .map file instead:
      var mapPath = storagePath().resolveSibling(storagePath().getFileName() + ".map");
      assertTrue(
        Files.exists(mapPath),
        mapPath + " must exists"
      );
      FileUtil.delete(mapPath);
      reopenStorage();
    }

    assertEquals(
      keyValuesSubstrate.length,
      storage.size(),
      "Storage must contain " + keyValuesSubstrate.length + " items"
    );
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

      assertEquals(
        storage.get(key),
        value,
        "Storage must contain (" + key + ", " + value + ") entry"
      );
    }
  }

  @Test
  public void mapContent_WithManyMappingsAddedAndRemoved_CouldBeRestored_ifHashToIdMapping_IsCorrupted() throws Exception {
    //RC: real scenario we're testing is 'improper termination' there ExtendibleMap content is corrupted, hence
    //    needs to be re-created from main entriesLog (see DurableMapFactory.rebuildMapFromLogIfInconsistent branch).

    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

      storage.put(key, value);
      storage.remove(key);
    }
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

      storage.put(key, value);
    }

    //Force a recovery: map is 'improperly closed'
    StorageTestingUtils.emulateImproperClose(storage);
    //.emulateImproperClose() already messes up the storage internals entirely, and an attempt to .close() it quite
    // likely leads to JVM crash -- so pretend storage doesn't need to be closed:
    storage = null;
    reopenStorage();

    assertEquals(
      keyValuesSubstrate.length,
      storage.size(),
      "Storage must contain " + keyValuesSubstrate.length + " items"
    );
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

      assertEquals(
        storage.get(key),
        value,
        "Storage must contain (" + key + ", " + value + ") entry"
      );
    }
  }



  /* ============================== infrastructure ============================================================ */


  /** given salt!=0 return value that is different from keyValue(substrate).getValue() */
  protected V differentValue(int substrate,
                             int salt) {
    V originalValue = keyValue(substrate).getValue();
    int differentSubstrate = substrate + salt;
    V differentValue = keyValue(differentSubstrate).getValue();
    if (Objects.equals(originalValue, differentValue)) {
      //implicit assumption: (key, value) pairs are uniquely identified by substrate
      // so modifying substrate => we must get different _both_ key and value
      throw new AssertionError("value(" + substrate + ")(=" + originalValue + ") happens to be == " +
                               "value(" + differentSubstrate + ")(=" + differentValue + ")");
    }
    return differentValue;
  }

  protected static <K, V> @NotNull List<Map.Entry<K, V>> listAllEntries(@NotNull DurableMap<K, V> storage)
    throws IOException {
    List<Map.Entry<K, V>> entries = new ArrayList<>();
    storage.forEachEntry((k, v) -> {
      entries.add(Map.entry(k, v));
      return true;
    });
    return entries;
  }
}
