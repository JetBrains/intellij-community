/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.IntObjectCache;
import com.intellij.util.io.storage.AbstractStorage;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 19, 2007
 */
public class PersistentMapTest extends PersistentMapTestBase {
  public void testMap() throws IOException {
    myMap.put("AAA", "AAA_VALUE");

    assertEquals("AAA_VALUE", myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(new HashSet<>(Arrays.asList("AAA")), new HashSet<>(myMap.getAllKeysWithExistingMapping()));

    myMap.put("BBB", "BBB_VALUE");
    assertEquals("BBB_VALUE", myMap.get("BBB"));
    assertEquals(new HashSet<>(Arrays.asList("AAA", "BBB")), new HashSet<>(myMap.getAllKeysWithExistingMapping()));

    myMap.put("AAA", "ANOTHER_AAA_VALUE");
    assertEquals("ANOTHER_AAA_VALUE", myMap.get("AAA"));
    assertEquals(new HashSet<>(Arrays.asList("AAA", "BBB")), new HashSet<>(myMap.getAllKeysWithExistingMapping()));

    myMap.remove("AAA");
    assertNull(myMap.get("AAA"));
    assertEquals("BBB_VALUE", myMap.get("BBB"));
    assertEquals(new HashSet<>(Arrays.asList("BBB")), new HashSet<>(myMap.getAllKeysWithExistingMapping()));

    myMap.remove("BBB");
    assertNull(myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(new HashSet<>(), new HashSet<>(myMap.getAllKeysWithExistingMapping()));

    myMap.put("AAA", "FINAL_AAA_VALUE");
    assertEquals("FINAL_AAA_VALUE", myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(new HashSet<>(Arrays.asList("AAA")), new HashSet<>(myMap.getAllKeysWithExistingMapping()));
  }

  public void testOpeningClosing() throws IOException {
    List<String> strings = new ArrayList<>(2000);
    for (int i = 0; i < 2000; ++i) {
      strings.add(createRandomString());
    }
    for (int i = 0; i < 2000; ++i) {
      final String key = strings.get(i);
      myMap.put(key, key + "_value");
      myMap.close();
      myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
    }
    for (int i = 0; i < 2000; ++i) {
      final String key = strings.get(i);
      final String value = key + "_value";
      assertEquals(value, myMap.get(key));

      myMap.put(key, value);
      assertTrue(myMap.isDirty());

      myMap.close();
      myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
    }
    for (int i = 0; i < 2000; ++i) {
      assertTrue(!myMap.isDirty());
      myMap.close();
      myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
    }
    final String randomKey = createRandomString();
    myMap.put(randomKey, randomKey + "_value");
    assertTrue(myMap.isDirty());
  }

  public void testPutCompactGet() throws IOException {
    myMap.put("a", "b");
    myMap.compact();
    assertEquals("b", myMap.get("a"));
  }

  public void testOpeningWithCompact() throws IOException {
    final int stringsCount = 5/*1000000*/;
    Set<String> strings = new HashSet<>(stringsCount);
    for (int i = 0; i < stringsCount; ++i) {
      final String key = createRandomString();
      strings.add(key);
      myMap.put(key, key + "_value");
    }
    myMap.close();
    myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);

    { // before compact
      final Collection<String> allKeys = new HashSet<>(myMap.getAllKeysWithExistingMapping());
      assertEquals(strings, allKeys);
      for (String key : allKeys) {
        final String val = myMap.get(key);
        assertEquals(key + "_value", val);
      }
    }
    myMap.compact();

    { // after compact
      final Collection<String> allKeys = new HashSet<>(myMap.getAllKeysWithExistingMapping());
      assertEquals(strings, allKeys);
      for (String key : allKeys) {
        final String val = myMap.get(key);
        assertEquals(key + "_value", val);
      }
    }
  }

  public void testGarbageSizeUpdatedAfterCompact() throws IOException {
    final int stringsCount = 5/*1000000*/;
    Set<String> strings = new HashSet<>(stringsCount);
    for (int i = 0; i < stringsCount; ++i) {
      final String key = createRandomString();
      strings.add(key);
      myMap.put(key, key + "_value");
    }

    // create some garbage
    for (String string : strings) {
      myMap.remove(string);
    }
    strings.clear();

    for (int i = 0; i < stringsCount; ++i) {
      final String key = createRandomString();
      strings.add(key);
      myMap.put(key, key + "_value");
    }

    myMap.close();

    final int garbageSizeOnClose = myMap.getGarbageSize();

    myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);

    final int garbageSizeOnOpen = myMap.getGarbageSize();

    assertEquals(garbageSizeOnClose, garbageSizeOnOpen);

    { // before compact
      final Collection<String> allKeys = new HashSet<>(myMap.getAllKeysWithExistingMapping());
      assertEquals(strings, allKeys);
      for (String key : allKeys) {
        final String val = myMap.get(key);
        assertEquals(key + "_value", val);
      }
    }

    myMap.compact();

    assertEquals(0, myMap.getGarbageSize());

    myMap.close();
    myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);

    final int garbageSizeAfterCompact = myMap.getGarbageSize();
    assertEquals(0, garbageSizeAfterCompact);

    { // after compact
      final Collection<String> allKeys = new HashSet<>(myMap.getAllKeysWithExistingMapping());
      assertEquals(strings, allKeys);
      for (String key : allKeys) {
        final String val = myMap.get(key);
        assertEquals(key + "_value", val);
      }
    }
  }

  public void testOpeningWithCompact2() throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");

    PersistentHashMap<Integer, String> map = new PersistentHashMap<>(file, new IntInlineKeyDescriptor(), EnumeratorStringDescriptor.INSTANCE);
    try {
      final int stringsCount = 5/*1000000*/;
      Map<Integer, String> testMapping = new LinkedHashMap<>(stringsCount);
      for (int i = 0; i < stringsCount; ++i) {
        final String key = createRandomString();
        String value = key + "_value";
        testMapping.put(i, value);
        map.put(i, value);
      }
      map.close();
      map = new PersistentHashMap<>(file, new IntInlineKeyDescriptor(), EnumeratorStringDescriptor.INSTANCE);

      { // before compact
        final Collection<Integer> allKeys = new HashSet<>(map.getAllKeysWithExistingMapping());
        assertEquals(new HashSet<>(testMapping.keySet()), allKeys);
        for (Integer key : allKeys) {
          final String val = map.get(key);
          assertEquals(testMapping.get(key), val);
        }
      }
      map.compact();

      { // after compact
        final Collection<Integer> allKeys = new HashSet<>(map.getAllKeysWithExistingMapping());
        assertEquals(new HashSet<>(testMapping.keySet()), allKeys);
        for (Integer key : allKeys) {
          final String val = map.get(key);
          assertEquals(testMapping.get(key), val);
        }
      }
    }
    finally {
      clearMap(file, map);
    }
  }

  public void testPerformance() throws IOException {
    final IntObjectCache<String> stringCache = new IntObjectCache<>(2000);
    final IntObjectCache.DeletedPairsListener listener = (key, mapKey) -> {
      try {
        final String _mapKey = (String)mapKey;
        assertEquals(myMap.enumerate(_mapKey), key);

        final String expectedMapValue = _mapKey == null ? null : _mapKey + "_value";
        final String actual = myMap.get(_mapKey);
        assertEquals(expectedMapValue, actual);

        myMap.remove(_mapKey);

        assertNull(myMap.get(_mapKey));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    };

    PlatformTestUtil.startPerformanceTest("put/remove", 5000, () -> {
      try {
        stringCache.addDeletedPairsListener(listener);
        for (int i = 0; i < 100000; ++i) {
          final String string = createRandomString();
          final int id = myMap.enumerate(string);
          stringCache.put(id, string);
          myMap.put(string, string + "_value");
        }
        stringCache.removeDeletedPairsListener(listener);
        for (String key : stringCache) {
          myMap.remove(key);
        }
        stringCache.removeAll();
        myMap.compact();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }).ioBound().assertTiming();

    myMap.close();
    LOG.debug(String.format("File size = %d bytes\n", myFile.length()));
    LOG.debug(String.format("Data file size = %d bytes\n",
                            new File(myDataFile.getParentFile(), myDataFile.getName() + AbstractStorage.DATA_EXTENSION).length()));
  }

  public void testPerformance1() throws IOException {
    final List<String> strings = new ArrayList<>(2000);
    for (int i = 0; i < 100000; ++i) {
      strings.add(createRandomString());
    }

    PlatformTestUtil.startPerformanceTest("put/remove", 1500, () -> {
      for (int i = 0; i < 100000; ++i) {
        final String string = strings.get(i);
        myMap.put(string, string);
      }

      for (int i = 0; i < 100000; ++i) {
        final String string = createRandomString();
        myMap.get(string);
      }

      for (int i = 0; i < 100000; ++i) {
        final String string = createRandomString();
        myMap.remove(string);
      }

      for (String string : strings) {
        myMap.remove(string);
      }
    }).assertTiming();
    myMap.close();
    LOG.debug(String.format("File size = %d bytes\n", myFile.length()));
    LOG.debug(String.format("Data file size = %d bytes\n",
                            new File(myDataFile.getParentFile(), myDataFile.getName() + AbstractStorage.DATA_EXTENSION).length()));
  }

  public void testReadonlyMap() throws IOException {
    myMap.put("AAA", "AAA_VALUE");

    myMap.close();
    myMap = new PersistentHashMap<String, String>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE) {
      @Override
      protected boolean isReadOnly() {
        return true;
      }
    };

    try {
      myMap.compact();
      fail();
    } catch (IncorrectOperationException ignore) {}

    try {
      myMap.put("AAA", "AAA_VALUE2");
      fail();
    } catch (IncorrectOperationException ignore) {}

    assertEquals("AAA_VALUE", myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(new HashSet<>(Arrays.asList("AAA")), new HashSet<>(myMap.getAllKeysWithExistingMapping()));

    try {
      myMap.remove("AAA");
      fail();
    } catch (IncorrectOperationException ignore) {}

    try {
      myMap.appendData("AAA", out -> out.writeUTF("BAR"));
      fail();
    } catch (IncorrectOperationException ignore) {}
  }

  public void testCreatePersistentMapWithoutCompression() throws IOException {
    clearMap(myFile, myMap);
    Boolean compressionFlag = PersistentHashMapValueStorage.CreationTimeOptions.DO_COMPRESSION.get();
    try {
      PersistentHashMapValueStorage.CreationTimeOptions.DO_COMPRESSION.set(Boolean.FALSE);
      myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
      myMap.put("Foo", "Bar");
      assertTrue(myMap.containsMapping("Foo"));
      myMap.close();
      assertEquals(55,PersistentHashMap.getDataFile(myFile).length());
    }
    finally {
      PersistentHashMapValueStorage.CreationTimeOptions.DO_COMPRESSION.set(compressionFlag);
    }
  }
  
  public void testFailedReadWriteSetsCorruptedFlag() throws IOException {
    EnumeratorStringDescriptor throwingException = new EnumeratorStringDescriptor() {
      @Override
      public void save(@NotNull DataOutput storage, @NotNull String value) throws IOException {
        throw new IOException("test");
      }

      @Override
      public String read(@NotNull DataInput storage) throws IOException {
        throw new IOException("test");
      }
    };

    PersistentMapPerformanceTest.MapConstructor<String, String> mapConstructorWithBrokenKeyDescriptor =
      (file) -> IOUtil.openCleanOrResetBroken(
        () -> new PersistentHashMap<>(file, throwingException, EnumeratorStringDescriptor.INSTANCE), file);

    PersistentMapPerformanceTest.MapConstructor<String, String> mapConstructorWithBrokenValueDescriptor =
      (file) -> IOUtil.openCleanOrResetBroken(
        () -> new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, throwingException), file);

    runIteration(mapConstructorWithBrokenKeyDescriptor);
    runIteration(mapConstructorWithBrokenValueDescriptor);
  }

  private void runIteration(PersistentMapPerformanceTest.MapConstructor<String, String> brokenMapDescritor) throws IOException {
    String key = "AAA";
    String value = "AAA_VALUE";

    PersistentMapPerformanceTest.MapConstructor<String, String> defaultMapConstructor =
      (file) -> IOUtil.openCleanOrResetBroken(
        () -> new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE), file);

    createInitializedMap(key, value, defaultMapConstructor);

    myMap = brokenMapDescritor.createMap(myFile);

    try {
      myMap.get(key);
      fail();
    } catch (IOException ignore) {
      assertTrue(myMap.isCorrupted());
    }

    createInitializedMap(key, value, defaultMapConstructor);

    myMap = brokenMapDescritor.createMap(myFile);

    try {
      myMap.put(key, value + value);
      fail();
    } catch (IOException ignore) {
      assertTrue(myMap.isCorrupted());
    }

    createInitializedMap(key, value, defaultMapConstructor);

    myMap = brokenMapDescritor.createMap(myFile);

    try {
      myMap.appendData(key, new PersistentHashMap.ValueDataAppender() {
        @Override
        public void append(DataOutput out) throws IOException {
          throw new IOException();
        }
      });
      fail();
    } catch (IOException ignore) {
      assertTrue(myMap.isCorrupted());
    }
  }

  private void closeMapSilently() {
    try {
      myMap.close();
    } catch (IOException ignore) {}
  }

  private void createInitializedMap(String key,
                                    String value,
                                    PersistentMapPerformanceTest.MapConstructor<String, String> defaultMapConstructor)
    throws IOException {
    closeMapSilently();
    myMap = defaultMapConstructor.createMap(myFile);
    myMap.put(key, value);
    closeMapSilently();
  }
}
