// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import com.intellij.util.io.Unmappable;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
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

  @Test
  public void mapContent_WithManyMappingsAddedAndRemoved_CouldBeRestored_ifHashToIdMapping_IsLost() throws IOException {
    //RC: real scenario we're testing is 'improper termination' there ExtendibleMap content is corrupted, hence
    //    needs to be re-created from main entriesLog (see DurableMapFactory.rebuildMapFromLogIfInconsistent branch).

    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();

      storage.put(key, value);
      storage.remove(key);
    }
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();

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
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();

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
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();

      storage.put(key, value);
      storage.remove(key);
    }
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();

      storage.put(key, value);
    }

    //Force a recovery: map is 'improperly closed'
    StorageTestingUtils.emulateImproperClose(storage);
    reopenStorage();

    assertEquals(
      keyValuesSubstrate.length,
      storage.size(),
      "Storage must contain " + keyValuesSubstrate.length + " items"
    );
    for (int substrate : keyValuesSubstrate) {
      Map.Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();

      assertEquals(
        storage.get(key),
        value,
        "Storage must contain (" + key + ", " + value + ") entry"
      );
    }
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
