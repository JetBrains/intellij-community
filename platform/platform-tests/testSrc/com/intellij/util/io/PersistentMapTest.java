package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.IntObjectCache;
import com.intellij.util.io.storage.Storage;
import junit.framework.TestCase;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 19, 2007
 */
public class PersistentMapTest extends TestCase {
  
  private PersistentHashMap<String, String> myMap;
  private File myFile;
  private File myDataFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFile = FileUtil.createTempFile("persistent", "map");
    myDataFile = new File(myFile.getParentFile(), myFile.getName() + PersistentHashMap.DATA_FILE_EXTENSION);
    myMap = new PersistentHashMap<String, String>(myFile, new EnumeratorStringDescriptor(), new EnumeratorStringDescriptor());
  }

  @Override
  protected void tearDown() throws Exception {
    clearMap(myFile, myMap);
    super.tearDown();
  }

  private static void clearMap(final File file1, PersistentHashMap<?, ?> map) throws IOException {
    map.close();

    final File[] files = file1.getParentFile().listFiles(new FileFilter() {
      @Override
      public boolean accept(final File pathname) {
        return pathname.getName().startsWith(file1.getName());
      }
    });

    if (files != null) {
      for (File file : files) {
        FileUtil.delete(file);
        assertTrue(!file.exists());
      }
    }
  }

  public void testMap() throws IOException {
    myMap.put("AAA", "AAA_VALUE");
    
    assertEquals("AAA_VALUE", myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(new HashSet<String>(Arrays.asList("AAA")), new HashSet<String>(myMap.getAllKeysWithExistingMapping()));
    
    myMap.put("BBB", "BBB_VALUE");
    assertEquals("BBB_VALUE", myMap.get("BBB"));
    assertEquals(new HashSet<String>(Arrays.asList("AAA", "BBB")), new HashSet<String>(myMap.getAllKeysWithExistingMapping()));
    
    myMap.put("AAA", "ANOTHER_AAA_VALUE");
    assertEquals("ANOTHER_AAA_VALUE", myMap.get("AAA"));
    assertEquals(new HashSet<String>(Arrays.asList("AAA", "BBB")), new HashSet<String>(myMap.getAllKeysWithExistingMapping()));
    
    myMap.remove("AAA");
    assertNull(myMap.get("AAA"));
    assertEquals("BBB_VALUE", myMap.get("BBB"));
    assertEquals(new HashSet<String>(Arrays.asList("BBB")), new HashSet<String>(myMap.getAllKeysWithExistingMapping()));
    
    myMap.remove("BBB");
    assertNull(myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(new HashSet<String>(), new HashSet<String>(myMap.getAllKeysWithExistingMapping()));
    
    myMap.put("AAA", "FINAL_AAA_VALUE");
    assertEquals("FINAL_AAA_VALUE", myMap.get("AAA"));
    assertNull(myMap.get("BBB"));
    assertEquals(new HashSet<String>(Arrays.asList("AAA")), new HashSet<String>(myMap.getAllKeysWithExistingMapping()));
  }
  
  public void testOpeningClosing() throws IOException {
    List<String> strings = new ArrayList<String>(2000);
    for (int i = 0; i < 2000; ++i) {
      strings.add(createRandomString());
    }
    for (int i = 0; i < 2000; ++i) {
      final String key = strings.get(i);
      myMap.put(key, key + "_value");
      myMap.close();
      myMap = new PersistentHashMap<String, String>(myFile, new EnumeratorStringDescriptor(), new EnumeratorStringDescriptor());
    }
    for (int i = 0; i < 2000; ++i) {
      final String key = strings.get(i);
      final String value = key + "_value";
      assertEquals(value, myMap.get(key));
      
      myMap.put(key, value);
      assertTrue(myMap.isDirty());
      
      myMap.close();
      myMap = new PersistentHashMap<String, String>(myFile, new EnumeratorStringDescriptor(), new EnumeratorStringDescriptor());
    }
    for (int i = 0; i < 2000; ++i) {
      assertTrue(!myMap.isDirty());
      myMap.close();
      myMap = new PersistentHashMap<String, String>(myFile, new EnumeratorStringDescriptor(), new EnumeratorStringDescriptor());
    }
    final String randomKey = createRandomString();
    myMap.put(randomKey, randomKey + "_value");
    assertTrue(myMap.isDirty());
  }

  public void testOpeningWithCompact() throws IOException {
    final int stringsCount = 5/*1000000*/;
    Set<String> strings = new HashSet<String>(stringsCount);
    for (int i = 0; i < stringsCount; ++i) {
      final String key = createRandomString();
      strings.add(key);
      myMap.put(key, key + "_value");
    }
    myMap.close();
    myMap = new PersistentHashMap<String, String>(myFile, new EnumeratorStringDescriptor(), new EnumeratorStringDescriptor());

    { // before compact
      final Collection<String> allKeys = new HashSet<String>(myMap.getAllKeysWithExistingMapping());
      assertEquals(strings, allKeys);
      for (String key : allKeys) {
        final String val = myMap.get(key);
        assertEquals(key + "_value", val);
      }
    }
    myMap.compact();

    { // after compact
      final Collection<String> allKeys = new HashSet<String>(myMap.getAllKeysWithExistingMapping());
      assertEquals(strings, allKeys);
      for (String key : allKeys) {
        final String val = myMap.get(key);
        assertEquals(key + "_value", val);
      }
    }
  }

  public void testGarbageSizeUpdatedAfterCompact() throws IOException {
    final int stringsCount = 5/*1000000*/;
    Set<String> strings = new HashSet<String>(stringsCount);
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

    myMap = new PersistentHashMap<String, String>(myFile, new EnumeratorStringDescriptor(), new EnumeratorStringDescriptor());

    final int garbageSizeOnOpen = myMap.getGarbageSize();

    assertEquals(garbageSizeOnClose, garbageSizeOnOpen);

    { // before compact
      final Collection<String> allKeys = new HashSet<String>(myMap.getAllKeysWithExistingMapping());
      assertEquals(strings, allKeys);
      for (String key : allKeys) {
        final String val = myMap.get(key);
        assertEquals(key + "_value", val);
      }
    }

    myMap.compact();

    assertEquals(0, myMap.getGarbageSize());

    myMap.close();
    myMap = new PersistentHashMap<String, String>(myFile, new EnumeratorStringDescriptor(), new EnumeratorStringDescriptor());

    final int garbageSizeAfterCompact = myMap.getGarbageSize();
    assertEquals(0, garbageSizeAfterCompact);

    { // after compact
      final Collection<String> allKeys = new HashSet<String>(myMap.getAllKeysWithExistingMapping());
      assertEquals(strings, allKeys);
      for (String key : allKeys) {
        final String val = myMap.get(key);
        assertEquals(key + "_value", val);
      }
    }
  }

  public void testOpeningWithCompact2() throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");

    PersistentHashMap<Integer, String> map = new PersistentHashMap<Integer, String>(file, new IntInlineKeyDescriptor(), new EnumeratorStringDescriptor());
    try {
      final int stringsCount = 5/*1000000*/;
      Map<Integer, String> testMapping = new LinkedHashMap<Integer, String>(stringsCount);
      for (int i = 0; i < stringsCount; ++i) {
        final String key = createRandomString();
        String value = key + "_value";
        testMapping.put(i, value);
        map.put(i, value);
      }
      map.close();
      map = new PersistentHashMap<Integer, String>(file, new IntInlineKeyDescriptor(), new EnumeratorStringDescriptor());

      { // before compact
        final Collection<Integer> allKeys = new HashSet<Integer>(map.getAllKeysWithExistingMapping());
        assertEquals(new HashSet<Integer>(testMapping.keySet()), allKeys);
        for (Integer key : allKeys) {
          final String val = map.get(key);
          assertEquals(testMapping.get(key), val);
        }
      }
      map.compact();

      { // after compact
        final Collection<Integer> allKeys = new HashSet<Integer>(map.getAllKeysWithExistingMapping());
        assertEquals(new HashSet<Integer>(testMapping.keySet()), allKeys);
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
    final IntObjectCache<String> stringCache = new IntObjectCache<String>(2000);
    final IntObjectCache.DeletedPairsListener listener = new IntObjectCache.DeletedPairsListener() {
      @Override
      public void objectRemoved(final int key, final Object mapKey) {
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
      }
    };

    PlatformTestUtil.startPerformanceTest("Perforamnce", 9000, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
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
      }
    }).ioBound().assertTiming();

    myMap.close();
    System.out.printf("File size = %d bytes\n", myFile.length());
    System.out
      .printf("Data file size = %d bytes\n", new File(myDataFile.getParentFile(), myDataFile.getName() + Storage.DATA_EXTENSION).length());
  }

  public void testPerformance1() throws IOException {
    final List<String> strings = new ArrayList<String>(2000);
    for (int i = 0; i < 100000; ++i) {
      strings.add(createRandomString());
    }

    PlatformTestUtil.startPerformanceTest("perf1",5000, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
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
      }
    }).assertTiming();
    myMap.close();
    System.out.printf("File size = %d bytes\n", myFile.length());
    System.out
      .printf("Data file size = %d bytes\n", new File(myDataFile.getParentFile(), myDataFile.getName() + Storage.DATA_EXTENSION).length());
  }

  private static final boolean DO_SLOW_TEST = false;

  public void test2GLimit() throws IOException {
    if (!DO_SLOW_TEST) return;
    File file = FileUtil.createTempFile("persistent", "map");
    FileUtil.createParentDirs(file);
    EnumeratorStringDescriptor stringDescriptor = new EnumeratorStringDescriptor();
    PersistentHashMap<String, String> map = new PersistentHashMap<String, String>(file, stringDescriptor, stringDescriptor);
    for (int i = 0; i < 12000; i++) {
      map.put("abc" + i, StringUtil.repeat("0123456789", 10000));
    }
    map.close();

    map = new PersistentHashMap<String, String>(file,
                                                stringDescriptor, stringDescriptor);
    long len = 0;
    for (String key : map.getAllKeysWithExistingMapping()) {
      len += map.get(key).length();
    }
    map.close();
    assertEquals(1200000000L, len);
  }

  private static String createRandomString() {
    return StringEnumeratorTest.createRandomString();
  }
}
