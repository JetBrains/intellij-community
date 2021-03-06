// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class PersistentMapTest extends PersistentMapTestBase {
  public void testRetainWriteOrderWhenCompactingBackward() throws IOException {
    clearMap(myFile, myMap);
    myMap = null;
    
    String longString = StringUtil.repeat("1234567890", 120);
    assertTrue(longString.length() > PersistentHashMapValueStorage.BLOCK_SIZE_TO_WRITE_WHEN_SOFT_MAX_RETAINED_LIMIT_IS_HIT);
    String removalMarker = "\uFFFF";
    PersistentMapPerformanceTest.MapConstructor<Integer, Collection<String>> mapConstructor = 
      (file) -> new PersistentHashMap<>(
        file,
        EnumeratorIntegerDescriptor.INSTANCE,
        new DataExternalizer<Collection<String>>() {
          @Override
          public void save(@NotNull DataOutput out, Collection<String> value) throws IOException {
            for(String str:value) {
              IOUtil.writeUTF(out, str);
            }
          }
  
          @Override
          public Collection<String> read(@NotNull DataInput in) throws IOException {
            List<String> result = new ArrayList<>();
            while(((InputStream)in).available() > 0) {
              String string = IOUtil.readUTF(in);
              if (string.equals(removalMarker)) {
                result.remove(result.size() - 1);
              } else {
                result.add(string);
              }
            }
            return result;
          }
        }
    );
    PersistentHashMap<Integer, Collection<String>> map = mapConstructor.createMap(myFile);
    try {
      int keys = 10_000;
      for(int iteration = 0; iteration < 5; ++iteration) {
        String toAppend = iteration % 2 == 0 ? longString : removalMarker;
        for (int i = 0; i < keys; ++i) {
          map.appendData(i, out -> IOUtil.writeUTF(out, toAppend));
        }
      }

      map.close();
      assertTrue(PersistentMapImpl.unwrap(map).getValueStorage().getSize() > 2 * PersistentHashMapValueStorage.SOFT_MAX_RETAINED_LIMIT);
      map = mapConstructor.createMap(myFile);
      PersistentMapImpl.unwrap(map).compact();

      for (int i = 0; i < keys; ++i) {
        Collection<String> strings = map.get(i);
        assertTrue(strings != null && strings.size() == 1);
        assertEquals(longString, strings.iterator().next());
      }
    } finally {
      clearMap(myFile, map);
    }
  }
  
  public void testMap() throws IOException {
    myMap.put("AAA", "AAA_VALUE");

    assertEquals("AAA_VALUE", myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(ContainerUtil.set("AAA"), new HashSet<>(myMap.getAllKeysWithExistingMapping()));
    assertEquals(1, getMapSize());

    myMap.put("BBB", "BBB_VALUE");
    assertEquals("BBB_VALUE", myMap.get("BBB"));
    assertEquals(ContainerUtil.set("AAA", "BBB"), new HashSet<>(myMap.getAllKeysWithExistingMapping()));
    assertEquals(2, getMapSize());

    myMap.put("AAA", "ANOTHER_AAA_VALUE");
    assertEquals("ANOTHER_AAA_VALUE", myMap.get("AAA"));
    assertEquals(ContainerUtil.set("AAA", "BBB"), new HashSet<>(myMap.getAllKeysWithExistingMapping()));
    assertEquals(2, getMapSize());

    myMap.remove("AAA");
    assertEquals(1, getMapSize());
    assertNull(myMap.get("AAA"));
    assertEquals("BBB_VALUE", myMap.get("BBB"));
    assertEquals(ContainerUtil.set("BBB"), new HashSet<>(myMap.getAllKeysWithExistingMapping()));

    myMap.remove("BBB");
    assertNull(myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(new HashSet<>(), new HashSet<>(myMap.getAllKeysWithExistingMapping()));
    assertEquals(0, getMapSize());

    myMap.put("AAA", "FINAL_AAA_VALUE");
    assertEquals("FINAL_AAA_VALUE", myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(ContainerUtil.set("AAA"), new HashSet<>(myMap.getAllKeysWithExistingMapping()));
    assertEquals(1, getMapSize());
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
    compactMap();
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
    compactMap();

    { // after compact
      final Collection<String> allKeys = new HashSet<>(myMap.getAllKeysWithExistingMapping());
      assertEquals(strings, allKeys);
      for (String key : allKeys) {
        final String val = myMap.get(key);
        assertEquals(key + "_value", val);
      }
    }
  }

  public void testPersistentMapWithoutChunks() throws IOException {
    PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.TRUE);
    try {
      myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
    } finally {
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.FALSE);
    }

    String writeKey = "key";
    String writeKey2 = "key2";

    failIfSucceededWithoutAssertion(
      () -> {
        myMap.appendData(writeKey, out -> out.writeUTF("BAR"));
        myMap.appendData(writeKey, out -> out.writeUTF("BAR"));
      }, 
      "Assertion on writing chunks"
    );

    failIfSucceededWithoutAssertion(
      () -> {
        myMap.appendData(writeKey2, out -> out.writeUTF("BAR"));
        myMap.force();
        myMap.appendData(writeKey2, out -> out.writeUTF("BAR"));
        myMap.force();
      },
      "Assertion on writing chunks 2"
    );
    
    try {
      myMap.close();
    } catch (Throwable ignore) {}
  }

  protected void failIfSucceededWithoutAssertion(ThrowableRunnable<IOException> runnable, String message) throws IOException {
    try {
      runnable.run();
      fail(message);
    }
    catch (AssertionFailedError assertionFailedError) { throw assertionFailedError; }
    catch (AssertionError ignored) {}
  }

  public void testCreationTimeOptionsAffectPersistentMapVersion() throws IOException {
    String keyWithChunks = "keyWithChunks";
    myMap.appendData(keyWithChunks, out -> out.writeUTF("BAR"));
    myMap.force();
    myMap.appendData(keyWithChunks, out -> out.writeUTF("BAR2"));

    myMap.close();

    PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.TRUE);
    try {
      myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
      fail();
    } catch (PersistentEnumeratorBase.VersionUpdatedException ignore) {}
    finally {
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.FALSE);
    }
  }

  public void testForwardCompact() throws IOException {
    clearMap(myFile, myMap);

    Random random = new Random(1);
    PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.TRUE);
    //PersistentHashMapValueStorage.CreationTimeOptions.DO_COMPRESSION.set(Boolean.FALSE);
    
    PersistentHashMap<Integer, String> map = null;
    try {
      map = new PersistentHashMap<>(myFile, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
      
      //final int stringsCount = 100;
      final int stringsCount = 2000000;
      int repetition = 100;
      
      List<String> strings = new ArrayList<>(stringsCount);
      for (int i = 0; i < stringsCount; ++i) {
        final String value = StringEnumeratorTest.createRandomString(random);
        
        map.put(i, StringUtil.repeat(value, repetition));
        if (i % 2 == 0) strings.add(value);
        else map.remove(i); // create some garbage
      }

      map.close();

      map = new PersistentHashMap<>(myFile, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);

      map.close();
      map = new PersistentHashMap<>(myFile, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);

      { // after compact
        final Collection<Integer> allKeys = new HashSet<>(map.getAllKeysWithExistingMapping());
        assertEquals(allKeys.size(), strings.size());
        for (int i = 0; i < stringsCount; ++i) if (i % 2 == 0) assertTrue(allKeys.contains(i));
        allKeys.clear();
                                                              
        for (int i = 0; i < stringsCount / 2; ++i) {
          assertEquals(StringUtil.repeat(strings.get(i), repetition), map.get(i * 2));
        }
      }
    }
    finally {
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.FALSE);
      
      clearMap(myFile, map);
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

    int garbageSizeOnClose = getGarbageSize();
    int sizeOnClose = getMapSize();

    myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);

    final int garbageSizeOnOpen = getGarbageSize();
    int sizeOnOpen = getMapSize();

    assertEquals(garbageSizeOnClose, garbageSizeOnOpen);
    assertEquals(sizeOnClose, sizeOnOpen);
    assertEquals(sizeOnOpen, myMap.getAllKeysWithExistingMapping().size());

    { // before compact
      final Collection<String> allKeys = new HashSet<>(myMap.getAllKeysWithExistingMapping());
      assertEquals(strings, allKeys);
      for (String key : allKeys) {
        final String val = myMap.get(key);
        assertEquals(key + "_value", val);
      }
    }

    compactMap();

    assertEquals(0, getGarbageSize());

    myMap.close();
    myMap = new PersistentHashMap<>(myFile, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);

    final int garbageSizeAfterCompact = getGarbageSize();
    assertEquals(0, garbageSizeAfterCompact);
    assertEquals(sizeOnOpen, myMap.getAllKeysWithExistingMapping().size());

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

    PersistentHashMap<Integer, String> map = new PersistentHashMap<>(file, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
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
      map = new PersistentHashMap<>(file, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);

      { // before compact
        final Collection<Integer> allKeys = new HashSet<>(map.getAllKeysWithExistingMapping());
        assertEquals(new HashSet<>(testMapping.keySet()), allKeys);
        for (Integer key : allKeys) {
          final String val = map.get(key);
          assertEquals(testMapping.get(key), val);
        }
      }
      PersistentMapImpl.unwrap(map).compact();

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

  public void testReadonlyMap() throws IOException {
    myMap.put("AAA", "AAA_VALUE");

    myMap.close();
    myMap = PersistentMapBuilder.newBuilder(myFile.toPath(), EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE).readonly().build();

    try {
      compactMap();
      fail();
    } catch (IncorrectOperationException ignore) {}

    try {
      myMap.put("AAA", "AAA_VALUE2");
      fail();
    } catch (IncorrectOperationException ignore) {}

    assertEquals("AAA_VALUE", myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(ContainerUtil.set("AAA"), new HashSet<>(myMap.getAllKeysWithExistingMapping()));

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
      assertEquals(55, PersistentMapImpl.getDataFile(myFile.toPath()).toFile().length());
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

  public void testExistingKeys() throws IOException {
    myMap.put("key", "_value");
    myMap.put("key", "value");
    myMap.put("key2", "value2");
    myMap.remove("key2");

    HashSet<String> allKeys = new HashSet<>();
    myMap.processKeys(new CommonProcessors.CollectProcessor<>(allKeys));
    HashSet<String> existingKeys = new HashSet<>();
    myMap.processKeysWithExistingMapping(new CommonProcessors.CollectProcessor<>(existingKeys));

    assertEquals(ContainerUtil.newHashSet("key", "key2"), allKeys);
    assertEquals(ContainerUtil.newHashSet("key"), existingKeys);
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
      assertTrue(PersistentMapImpl.unwrap(myMap).isCorrupted());
    }

    createInitializedMap(key, value, defaultMapConstructor);

    myMap = brokenMapDescritor.createMap(myFile);

    try {
      myMap.put(key, value + value);
      fail();
    } catch (IOException ignore) {
      assertTrue(PersistentMapImpl.unwrap(myMap).isCorrupted());
    }

    createInitializedMap(key, value, defaultMapConstructor);

    myMap = brokenMapDescritor.createMap(myFile);

    try {
      myMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(@NotNull DataOutput out) throws IOException {
          throw new IOException();
        }
      });
      fail();
    } catch (IOException ignore) {
      assertTrue(PersistentMapImpl.unwrap(myMap).isCorrupted());
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
