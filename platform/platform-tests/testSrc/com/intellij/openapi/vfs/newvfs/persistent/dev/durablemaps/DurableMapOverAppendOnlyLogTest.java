// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.durablemaps;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogFactory;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.io.KeyValueStoreTestBase;
import com.intellij.util.io.dev.StorageFactory;
import com.intellij.util.io.dev.enumerator.StringAsUTF8;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DurableMapOverAppendOnlyLogTest extends KeyValueStoreTestBase<DurableMapOverAppendOnlyLog<String, String>> {

  @Override
  protected @NotNull StorageFactory<DurableMapOverAppendOnlyLog<String, String>> factory() {
    return storagePath ->
      AppendOnlyLogFactory.withDefaults().wrapStorageSafely(
        storagePath,
        aoLog -> ExtendibleMapFactory.defaults().wrapStorageSafely(
          storagePath.resolveSibling(storagePath.getFileName() + ".map"),
          map -> new DurableMapOverAppendOnlyLog<>(aoLog,
                                                   map,
                                                   StringAsUTF8.INSTANCE,
                                                   StringAsUTF8.INSTANCE
          )
        )
      );
  }

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
  public void containsMappingReturnsTrue_forEachOfManyMappingsPut() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      storage.put(entry.getKey(), entry.getValue());
    }

    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();
      assertTrue(storage.containsMapping(key),
                 "store[" + key + "] must contain mapping");
    }
  }

  @Test
  public void forEach_ListsSingleMappingPut() throws IOException {
    String key = "testKey";
    String value = "testValue";

    storage.put(key, value);
    List<Map.Entry<String, String>> entries = listAllEntries(storage);
    assertEquals(
      List.of(Map.entry(key, value)),
      entries,
      "forEach() must list the single entry that was put"
    );
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
  public void forEachOfManyKeys_putWithSameKey_overridesValuesPreviouslyPut() throws IOException {
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
  public void forEach_ListsAllEntriesThatWerePutBefore() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();

      storage.put(key, value);
    }

    List<Map.Entry<String, String>> entries = listAllEntries(storage);
    Set<Map.Entry<String, String>> entiresSet = new ObjectOpenHashSet<>(entries);
    assertEquals(
      entries.size(),
      entiresSet.size(),
      ".forEachEntry() must list entries without duplicates"
    );
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      assertTrue(
        entiresSet.contains(entry),
        () -> "forEach() must list the " + entry + " that was put"
      );
    }
  }


  @Test
  public void forEachOfManyMappings_AfterPutAndRemove_containsMappingReturnsFalse() throws IOException {
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
    List<Map.Entry<String, String>> entries = listAllEntries(storage);
    assertTrue(entries.isEmpty(),
               ".forEachEntry() must list nothing after removing all the entries");
  }

  ///////

  private static @NotNull List<Map.Entry<String, String>> listAllEntries(@NotNull DurableMapOverAppendOnlyLog<String, String> storage1)
    throws IOException {
    List<Map.Entry<String, String>> entries = new ArrayList<>();
    storage1.forEachEntry((k, v) -> {
      entries.add(Map.entry(k, v));
      return true;
    });
    return entries;
  }
}
