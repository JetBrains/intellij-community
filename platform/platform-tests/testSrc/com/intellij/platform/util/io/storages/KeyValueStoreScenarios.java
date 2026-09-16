// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.util.io.KeyValueStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Contains reusable test scenarios (unit5 test-interfaces) for [KeyValueStore] implementations
public final class KeyValueStoreScenarios {

  private KeyValueStoreScenarios() { }

  public interface BasicScenarios<K, V, S extends KeyValueStore<K, V>> extends KeyValueStoreTestContext<K, V, S> {

    @Test
    default void emptyStorage_IsNotDirty() {
      assertFalse(storageUnderTest().isDirty());
    }

    @Test
    default void singleKeyValuePut_returnedByGet(KeyValueTestData<K, V> testData) throws IOException {
      Map.Entry<K, V> entry = testData.keyValue(42);

      K key = entry.getKey();
      V value = entry.getValue();

      assertNull(storageUnderTest().get(key),
                 "Empty store contains no keys");

      storageUnderTest().put(key, value);

      assertEquals(value,
                   storageUnderTest().get(key));
    }

    @Test
    default void emptyStorage_containsNothing(KeyValueTestData<K, V> testData) throws IOException {
      S storageUnderTest = storageUnderTest();
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();

        assertNull(storageUnderTest.get(key),
                   "store[" + key + "] must be absent in empty storage");
      }
    }

    @Test
    default void manyKeysValuesPut_areReturnedByGet(KeyValueTestData<K, V> testData) throws IOException {
      S storageUnderTest = storageUnderTest();
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();

        assertNull(storageUnderTest.get(key),
                   "store[" + key + "] must be absent before put");

        storageUnderTest.put(key, value);

        assertEquals(value,
                     storageUnderTest.get(key),
                     "store[" + key + "] must be == [" + value + "]");
      }

      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();
        assertEquals(value,
                     storageUnderTest.get(key),
                     "store[" + key + "] must be == [" + value + "]");
      }
    }

    @Test
    default void manyKeysValuesPut_returnedByGet_afterReopen(KeyValueTestData<K, V> testData) throws IOException {
      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        storageUnderTest().put(entry.getKey(), entry.getValue());
      }

      reopenStorage();

      for (int substrate : testData.substrate()) {
        Map.Entry<K, V> entry = testData.keyValue(substrate);
        K key = entry.getKey();
        V value = entry.getValue();
        assertEquals(value,
                     storageUnderTest().get(key),
                     "store[" + key + "] must be == [" + value + "] even after reopen");
      }
    }

    @Test
    default void close_IsSafeToCallMultipleTimes() throws IOException{
      storageUnderTest().close();
      storageUnderTest().close();//no exception on 2nd call
      storageUnderTest().close();//no exception on 3rd call
    }
  }

  public interface StringKeyScenarios<V, S extends KeyValueStore<String, V>> extends KeyValueStoreTestContext<String, V, S> {

    @Test
    default void emptyKeyValuePut_returnedByGet(KeyValueTestData<String, V> testData) throws IOException {
      V value = testData.keyValue(42).getValue();

      assertNull(storageUnderTest().get(""),
                 "Empty store contains no keys");

      storageUnderTest().put("", value);

      assertEquals(value,
                   storageUnderTest().get(""));
    }

    @Test
    default void keysWithCollidingHashes_AreStillPutAndGetDifferently(KeyValueTestData<String, V> testData) throws IOException {
      String key = testData.keyValue(42).getKey();
      V value = testData.keyValue(42).getValue();
      List<String> collidingKeys = generateHashCollisions(key);

      storageUnderTest().put(key, value);
      for (int i = 0; i < collidingKeys.size(); i++) {
        storageUnderTest().put(collidingKeys.get(i), testData.keyValue(43 + i).getValue());
      }

      assertEquals(value,
                   storageUnderTest().get(key));
      for (int i = 0; i < collidingKeys.size(); i++) {
        String collidingKey = collidingKeys.get(i);
        assertEquals(testData.keyValue(43 + i).getValue(),
                     storageUnderTest().get(collidingKey));
      }
    }

    private static List<String> generateHashCollisions(String sample) {
      if (sample.length() <= 1) {
        throw new IllegalArgumentException("Hash collisions for empty and one-character strings are not implemented");
      }

      char firstChar = sample.charAt(0);
      char secondChar = sample.charAt(1);
      String suffix = sample.substring(2);

      List<String> hashCollisions = List.of(
        Character.toString(firstChar - 1) + Character.toString(secondChar + 31) + suffix,
        Character.toString(firstChar - 2) + Character.toString(secondChar + 31 * 2) + suffix,
        Character.toString(firstChar - 3) + Character.toString(secondChar + 31 * 3) + suffix
      );
      for (String collision : hashCollisions) {
        if (collision.hashCode() != sample.hashCode()) {
          throw new AssertionError("[" + collision + "].hash=" + collision.hashCode() +
                                   " != [" + sample + "].hash=" + sample.hashCode());
        }
      }
      return hashCollisions;
    }
  }

  //TODO RC: 'null' key is not supported by most implementations
  @SuppressWarnings("unused")
  public interface NullKeyScenarios<K, V, S extends KeyValueStore<K, V>> extends KeyValueStoreTestContext<K, V, S> {

    @Test
    default void nullKeyValuePut_returnedByGet(KeyValueTestData<K, V> testData) throws IOException {
      Map.Entry<K, V> entry = testData.keyValue(42);

      K key = null;
      V value = entry.getValue();

      assertNull(storageUnderTest().get(key),
                 "Empty store contains no keys");

      storageUnderTest().put(key, value);

      assertEquals(value,
                   storageUnderTest().get(key));
    }
  }
}
