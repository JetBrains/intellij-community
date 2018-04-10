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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import gnu.trove.THashSet;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Collection;
import java.util.Random;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
@SkipSlowTestLocally
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
    void append(PersistentHashMap<T, ?> map, int indexKey, PersistentHashMap.ValueDataAppender appender) throws IOException;
  }

  private static <T> void run2GTest(MapConstructor<T, String> constructor, MapIntSetter<T, String> setter, MapGetter<T, String> getter)
    throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");
    FileUtil.createParentDirs(file);
    PersistentHashMap<T, String> map = null;

    try {
      map = constructor.createMap(file);
      for (int i = 0; i < 12000; i++) {
        setter.putValue(map, i, StringUtil.repeat("0123456789", 10000));
      }
      map.close();

      map = constructor.createMap(file);
      long len = 0;
      for (T key : map.getAllKeysWithExistingMapping()) {
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
      final boolean isSmall = stringsCount < 1000000;
      assertTrue(isSmall || map.makesSenseToCompact());
      long started = System.currentTimeMillis();

      map = constructor.createMap(file);
      if (isSmall) {
        map.compact();
      }
      else {
        assertTrue(map.isDirty());  // autocompact on open should leave the map dirty
      }
      assertTrue(!map.makesSenseToCompact());
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
    TIntIntHashMap checkMap = new TIntIntHashMap(size);
    Random r = new Random(1);
    while (size != checkMap.size()) {
      if (checkMap.size() == 0) {
        checkMap.put(r.nextInt(), 0);
        checkMap.put(r.nextInt(), 0);
        checkMap.put(0, Math.abs(r.nextInt()));
      }
      else {
        checkMap.put(r.nextInt(), Math.abs(r.nextInt()));
      }
    }

    long started = System.currentTimeMillis();
    PersistentHashMap<Integer, Integer> map = null;

    try {
      map = new PersistentHashMap<Integer, Integer>(file, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorIntegerDescriptor.INSTANCE) {
        @Override
        protected boolean wantNonnegativeIntegralValues() {
          return true;
        }
      };

      final PersistentHashMap<Integer, Integer> mapFinal = map;
      boolean result = checkMap.forEachEntry((a, b) -> {
        try {
          mapFinal.put(a, b);
        }
        catch (IOException e) {
          e.printStackTrace();
          assertTrue(false);
          return false;
        }
        return true;
      });
      assertTrue(result);
      map.close();
      LOG.debug("Done:" + (System.currentTimeMillis() - started));
      started = System.currentTimeMillis();
      map = new PersistentHashMap<Integer, Integer>(file, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorIntegerDescriptor.INSTANCE) {
        @Override
        protected boolean wantNonnegativeIntegralValues() {
          return true;
        }
      };
      final PersistentHashMap<Integer, Integer> mapFinal2 = map;
      result = checkMap.forEachEntry((a, b) -> {
        try {
          assertTrue(b == mapFinal2.get(a));
        }
        catch (IOException e) {
          e.printStackTrace();
          assertTrue(false);
          return false;
        }
        return true;
      });
      assertTrue(result);

      LOG.debug("Done 2:" + (System.currentTimeMillis() - started));
    }
    finally {
      clearMap(file, map);
    }
  }

  private static class PathCollectionExternalizer implements DataExternalizer<Collection<String>> {
    static final PathCollectionExternalizer INSTANCE = new PathCollectionExternalizer();

    public void save(@NotNull DataOutput out, Collection<String> value) throws IOException {
      for (String str : value) {
        IOUtil.writeString(str, out);
      }
    }

    public Collection<String> read(@NotNull DataInput in) throws IOException {
      final Set<String> result = new THashSet<>(FileUtil.PATH_HASHING_STRATEGY);
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

      for (T key : map.getAllKeysWithExistingMapping()) {
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
}
