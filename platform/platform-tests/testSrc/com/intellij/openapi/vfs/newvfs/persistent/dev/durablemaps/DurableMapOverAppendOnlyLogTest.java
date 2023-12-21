// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.durablemaps;

import com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog.AppendOnlyLogFactory;
import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory;
import com.intellij.util.io.KeyValueStoreTestBase;
import com.intellij.util.io.dev.StorageFactory;
import com.intellij.util.io.dev.enumerator.StringAsUTF8;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

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
                 "Storage must be empty after removing all the keys");
    assertTrue(storage.isEmpty(),
               "Storage must be empty after removing all the keys");
  }

  //TODO RC: test for value overwrite: same key, different values

  //TODO RC: test for key.hash collision: map able to distinguish keys with same hash
  //         ideally it should be many such keys, so everything above could be tested on them
}