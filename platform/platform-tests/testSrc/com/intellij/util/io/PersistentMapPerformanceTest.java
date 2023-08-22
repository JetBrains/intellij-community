// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.idea.HardwareAgentRequired;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.IntObjectCache;
import com.intellij.util.io.storage.AbstractStorage;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Eugene Zhuravlev
 */
@SkipSlowTestLocally
@HardwareAgentRequired
public class PersistentMapPerformanceTest extends PersistentMapTestBase {
  interface MapConstructor<T, T2> {
    PersistentHashMap<T, T2> createMap(File file) throws IOException;
  }

  interface MapIntSetter<T, T2> {
    void putValue(PersistentHashMap<T, T2> map, int indexKey, T2 value) throws IOException;
  }

  interface MapGetter<T, T2> {
    T2 readValue(PersistentHashMap<T, T2> map, T key) throws IOException;
  }

  interface MapIntGetter<T, T2> {
    T2 readValue(PersistentHashMap<T, T2> map, int indexKey) throws IOException;
  }

  interface MapIntRemover<T> {
    void remove(PersistentHashMap<T, ?> map, int indexKey) throws IOException;
  }

  interface MapIntAppender<T> {
    void append(PersistentHashMap<T, ?> map, int indexKey, AppendablePersistentMap.ValueDataAppender appender) throws IOException;
  }

  private static <T> void run2GTest(MapConstructor<T, String> constructor, MapIntSetter<T, String> setter, MapGetter<T, String> getter)
    throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");
    FileUtil.createParentDirs(file);

    try(PersistentHashMap<T, String> map = constructor.createMap(file)) {
      for (int i = 0; i < 12000; i++) {
        setter.putValue(map, i, StringUtil.repeat("0123456789", 10000));
      }
    }

    PersistentHashMap<T, String> map = null;
    try {
      map = constructor.createMap(file);
      long len = 0;
      List<T> result = new ArrayList<>();
      map.processKeysWithExistingMapping(new CommonProcessors.CollectProcessor<>(result));
      for (T key : result) {
        len += getter.readValue(map, key).length();
      }
      assertEquals(1200000000L, len);
    }
    finally {
      clearMap(file, map);
    }
  }

  public void test2GLimit() throws IOException {
    run2GTest(
      file -> new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE),
      (map, indexKey, value) -> map.put("abc" + indexKey, value),
      PersistentHashMap::get
    );
  }

  public void test2GLimit2() throws IOException {
    run2GTest(
      file -> new PersistentHashMap<>(file, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE),
      PersistentHashMap::put,
      PersistentHashMap::get
    );
  }

  private static <T> void runOpeningWithCompact3Test(MapConstructor<T, Integer> constructor, MapIntGetter<T, Integer> intGetter,
                                                     MapIntAppender<T> appender, MapIntRemover<T> remover) throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");

    PersistentHashMap<T, Integer> map = constructor.createMap(file);
    try {
      final int stringsCount = 10000002;
      //final int stringsCount =      102;

      for (int t = 0; t < 4; ++t) {
        for (int i = 0; i < stringsCount; ++i) {
          final int finalI = i;
          final int finalT = t;

          appender.append(map, i, out -> out.write((finalI + finalT) & 0xFF));
        }
      }
      map.close();
      map = constructor.createMap(file);
      for (int i = 0; i < stringsCount; ++i) {
        if (i < 2 * stringsCount / 3) {
          remover.remove(map, i);
        }
      }
      map.close();
      //noinspection ConstantConditions
      final boolean isSmall = stringsCount < 1000000;
      assertTrue(makesSenseToCompact(map));
      long started = System.currentTimeMillis();

      map = constructor.createMap(file);
      //noinspection ConstantConditions
      if (isSmall) {
        PersistentMapImpl.unwrap(map).compact();
      }
      else {
        assertTrue(map.isDirty());  // autocompact on open should leave the map dirty
      }
      assertFalse(makesSenseToCompact(map));
      LOG.debug(String.valueOf(System.currentTimeMillis() - started));
      for (int i = 0; i < stringsCount; ++i) {
        if (i >= 2 * stringsCount / 3) {
          Integer s = intGetter.readValue(map, i);
          assertEquals((s & 0xFF), ((i + 3) & 0xFF));
          assertEquals(((s >>> 8) & 0xFF), ((i + 2) & 0xFF));
          assertEquals((s >>> 16) & 0xFF, ((i + 1) & 0xFF));
          assertEquals((s >>> 24) & 0xFF, (i & 0xFF));
        }
      }
    }
    finally {
      clearMap(file, map);
    }
  }

  private static <T> boolean makesSenseToCompact(PersistentHashMap<T, Integer> map) {
    return PersistentMapImpl.unwrap(map).makesSenseToCompact();
  }

  public void testOpeningWithCompact3() throws IOException {
    runOpeningWithCompact3Test(
      (file) -> new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, EnumeratorIntegerDescriptor.INSTANCE),
      (map, indexKey) -> map.get(String.valueOf(indexKey)),
      (map, indexKey, appender) -> map.appendData(String.valueOf(indexKey), appender),
      (map, indexKey) -> map.remove(String.valueOf(indexKey))
    );
  }

  public void testOpeningWithCompact3_2() throws IOException {
    runOpeningWithCompact3Test(
      (file) -> new PersistentHashMap<>(file, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorIntegerDescriptor.INSTANCE),
      PersistentHashMap::get,
      PersistentHashMap::appendData,
      PersistentHashMap::remove
    );
  }

  public void testIntToIntMapPerformance() throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");
    FileUtil.createParentDirs(file);

    int size = 10000000;
    Int2IntMap checkMap = new Int2IntOpenHashMap(size);
    Random r = new Random(1);
    while (size != checkMap.size()) {
      if (checkMap.size() == 0) {
        checkMap.put(r.nextInt(), 0);
        checkMap.put(r.nextInt(), 0);
        checkMap.put(0, r.nextInt(Integer.MAX_VALUE));
      }
      else {
        checkMap.put(r.nextInt(), r.nextInt(Integer.MAX_VALUE));
      }
    }

    long started = System.currentTimeMillis();
    PersistentHashMap<Integer, Integer> map = null;

    try {
      map = PersistentMapBuilder
        .newBuilder(file.toPath(), EnumeratorIntegerDescriptor.INSTANCE, EnumeratorIntegerDescriptor.INSTANCE)
        .inlineValues()
        .build();

      final PersistentHashMap<Integer, Integer> mapFinal = map;
      for (Int2IntMap.Entry entry : checkMap.int2IntEntrySet()) {
        try {
          mapFinal.put(entry.getIntKey(), entry.getIntValue());
        }
        catch (IOException e) {
          e.printStackTrace();
          fail();
          break;
        }
      }
      map.close();
      LOG.debug("Done:" + (System.currentTimeMillis() - started));
      started = System.currentTimeMillis();
      map = PersistentMapBuilder.newBuilder(file.toPath(), EnumeratorIntegerDescriptor.INSTANCE, EnumeratorIntegerDescriptor.INSTANCE)
        .inlineValues()
        .build();

      final PersistentHashMap<Integer, Integer> mapFinal2 = map;
      for (Int2IntMap.Entry entry : checkMap.int2IntEntrySet()) {
        try {
          assertEquals(entry.getIntValue(), (int)mapFinal2.get(entry.getIntKey()));
        }
        catch (IOException e) {
          e.printStackTrace();
          fail();
        }
      }

      LOG.debug("Done 2:" + (System.currentTimeMillis() - started));
    }
    finally {
      clearMap(file, map);
    }
  }

  private static class PathCollectionExternalizer implements DataExternalizer<Collection<String>> {
    static final PathCollectionExternalizer INSTANCE = new PathCollectionExternalizer();

    @Override
    public void save(@NotNull DataOutput out, Collection<String> value) throws IOException {
      for (String str : value) {
        IOUtil.writeString(str, out);
      }
    }

    @Override
    public Collection<String> read(@NotNull DataInput in) throws IOException {
      final Set<String> result = CollectionFactory.createFilePathSet();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        final String str = IOUtil.readString(stream);
        result.add(str);
      }
      return result;
    }
  }

  private static <T> void run2GLimitWithAppendTest(MapConstructor<T, Collection<String>> constructor, MapIntAppender<T> intAppender,
                                                   MapIntRemover<T> intRemover)
    throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");
    FileUtil.createParentDirs(file);

    PersistentHashMap<T, Collection<String>> map = null;

    try {
      map = constructor.createMap(file);
      final int max = 2000;
      for (int j = 0; j < 7; ++j) {
        for (int i = 0; i < max; i++) {
          final int finalJ = j;
          intAppender.append(map, i, out -> IOUtil.writeString(StringUtil.repeat("0123456789", 10000 + finalJ - 3), out));
        }
        map.force();
      }

      final int lastKey = max - 1;
      intRemover.remove(map, lastKey);
      for (int j = 0; j < 7; ++j) {
        final int finalJ = j;
        intAppender.append(map, lastKey, out -> IOUtil.writeString(StringUtil.repeat("0123456789", 10000 + finalJ - 3), out));
      }

      map.close();

      map = constructor.createMap(file);

      long len = 0;

      List<T> result = new ArrayList<>();
      map.processKeysWithExistingMapping(new CommonProcessors.CollectProcessor<>(result));
      for (T key : result) {
        for (String k : map.get(key)) {
          len += k.length();
        }
      }

      assertEquals(1400000000L, len);
    }
    finally {
      clearMap(file, map);
    }
  }

  public void test2GLimitWithAppend() throws IOException {
    run2GLimitWithAppendTest(file -> new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE,
                                                             PathCollectionExternalizer.INSTANCE),
                             (map, indexKey, appender) -> map.appendData("abc" + indexKey, appender),
                             (map, indexKey) -> map.remove("abc" + indexKey)
    );
  }

  public void test2GLimitWithAppend2() throws IOException {
    run2GLimitWithAppendTest(file -> new PersistentHashMap<>(file, EnumeratorIntegerDescriptor.INSTANCE,
                                                             PathCollectionExternalizer.INSTANCE),
                             (map, indexKey, appender) -> map.appendData(indexKey, appender),
                             (map, indexKey) -> map.remove(indexKey)
    );
  }

  public void testPerformance() throws IOException {
    final IntObjectCache<String> stringCache = new IntObjectCache<>(2000);
    IntObjectCache.DeletedPairsListener<String> listener = (key, mapKey) -> {
      try {
        final String expectedMapValue = mapKey == null ? null : mapKey + "_value";
        final String actual = myMap.get(mapKey);
        assertEquals(expectedMapValue, actual);

        myMap.remove(mapKey);

        assertNull(myMap.get(mapKey));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    };

    AtomicInteger count = new AtomicInteger();
    PlatformTestUtil.startPerformanceTest("put/remove", 9000, () -> {
      try {
        stringCache.addDeletedPairsListener(listener);
        for (int i = 0; i < 100000; ++i) {
          final String string = createRandomString();
          if (!myMap.containsMapping(string)) {
            stringCache.put(count.incrementAndGet(), string);
            myMap.put(string, string + "_value");
          }
        }
        stringCache.removeDeletedPairsListener(listener);
        for (String key : stringCache) {
          myMap.remove(key);
        }
        stringCache.removeAll();
        PersistentMapImpl.unwrap(myMap).compact();
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
}
