// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.StorageFactory;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;
import static org.junit.jupiter.api.Assertions.*;

public class DurableMapOverAppendOnlyLogTest extends DurableMapTestBase<String, String, DurableMapOverAppendOnlyLog<String, String>> {

  public DurableMapOverAppendOnlyLogTest() {
    super(STRING_SUBSTRATE_DECODER);
  }

  @Override
  protected @NotNull StorageFactory<DurableMapOverAppendOnlyLog<String, String>> factory() {
    return DurableMapFactory.withDefaults(stringAsUTF8(), stringAsUTF8());
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
  public void forEach_ListsSingleMappingPut() throws IOException {
    String key = "testKey";
    String value = "testValue";

    storage.put(key, value);
    List<Map.Entry<String, String>> entries = DurableMapOverAppendOnlyLogTest.listAllEntries(storage);
    assertEquals(
      List.of(Map.entry(key, value)),
      entries,
      "forEach() must list the single entry that was put"
    );
  }

  @Test
  public void forEach_ListsNothing_afterManyMappingsPut_AndRemoved() throws IOException {
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

    List<Map.Entry<String, String>> entries = DurableMapOverAppendOnlyLogTest.listAllEntries(storage);
    assertTrue(entries.isEmpty(),
                          ".forEachEntry() must list nothing after removing all the entries");
  }


  ///////

  private static @NotNull List<Map.Entry<String, String>> listAllEntries(@NotNull DurableMapOverAppendOnlyLog<String, String> storage)
    throws IOException {
    List<Map.Entry<String, String>> entries = new ArrayList<>();
    storage.forEachEntry((k, v) -> {
      entries.add(Map.entry(k, v));
      return true;
    });
    return entries;
  }
}
