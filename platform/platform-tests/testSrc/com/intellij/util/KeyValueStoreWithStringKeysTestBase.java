// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.util.io.KeyValueStore;
import com.intellij.util.io.KeyValueStoreTestBase;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public abstract class KeyValueStoreWithStringKeysTestBase<V, S extends KeyValueStore<String, V>> extends KeyValueStoreTestBase<String, V, S> {

  public KeyValueStoreWithStringKeysTestBase(@NotNull IntFunction<Map.Entry<String, V>> decoder) {
    super(decoder);
  }

  @Test
  public void emptyKeyValuePut_returnedByGet() throws IOException {
    Map.Entry<String, V> entry = keyValue(42);
    //I want to check key with serialized length=0 is treated normally by the map
    // I implicitly assume empty string is serialized to the byte[0]
    String key = "";
    V value = entry.getValue();

    assertNull(storage.get(key),
               "Empty store contains no keys");

    storage.put(key, value);

    assertEquals(value,
                 storage.get(key));
  }


  @Test
  public void keysWithCollidingHashes_AreStillPutAndGetDifferently() throws IOException {
    Map.Entry<String, V> entry = keyValue(42);

    String key = entry.getKey();
    V value = entry.getValue();
    List<String> collidingKeys = generateHashCollisions(key);

    storage.put(key, value);
    for (String collidingKey : collidingKeys) {
      storage.put(collidingKey, value);
    }


    assertEquals(key,
                 storage.get(key));
    for (String collidingKey : collidingKeys) {
      assertEquals(value,
                   storage.get(collidingKey));
    }
  }

  /** @return >1 strings with same hash-code as sample */
  protected static List<String> generateHashCollisions(@NotNull String sample) {
    if (sample.length() <= 1) {
      //just too hard
      throw new IllegalArgumentException("hash-collisions for empty and 1-char strings are not implemented");
    }

    char ch1 = sample.charAt(0);
    char ch2 = sample.charAt(1);
    String suffix = sample.substring(2);

    //31*ch1 + ch2 == 31*x + y
    List<String> hashCollisions = List.of(
      Character.toString(ch1 - 1) + Character.toString(ch2 + 31) + suffix,
      Character.toString(ch1 - 2) + Character.toString(ch2 + 31 * 2) + suffix,
      Character.toString(ch1 - 3) + Character.toString(ch2 + 31 * 3) + suffix
    );
    for (String collision : hashCollisions) {
      if (collision.hashCode() != sample.hashCode()) {
        throw new AssertionError("[" + collision + "].hash=" + collision.hashCode() + " != [" + sample + "].hash=" + sample.hashCode());
      }
    }
    return hashCollisions;
  }
}
