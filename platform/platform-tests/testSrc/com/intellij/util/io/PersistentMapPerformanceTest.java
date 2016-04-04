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
 *         Date: Dec 19, 2007
 */
@SkipSlowTestLocally
public class PersistentMapPerformanceTest extends PersistentMapTestBase {
  public void test2GLimit() throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");
    FileUtil.createParentDirs(file);
    PersistentHashMap<String, String> map = null;

    try {
      map = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
      for (int i = 0; i < 12000; i++) {
        map.put("abc" + i, StringUtil.repeat("0123456789", 10000));
      }
      map.close();

      map = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
      long len = 0;
      for (String key : map.getAllKeysWithExistingMapping()) {
        len += map.get(key).length();
      }
      assertEquals(1200000000L, len);
    }
    finally {
      clearMap(file, map);
    }
  }

  public void testOpeningWithCompact3() throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");

    EnumeratorIntegerDescriptor integerDescriptor = new EnumeratorIntegerDescriptor();
    PersistentHashMap<String, Integer> map = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, integerDescriptor);
    try {
      final int stringsCount = 10000002;
      //final int stringsCount =      102;

      for(int t = 0; t < 4; ++t) {
        for (int i = 0; i < stringsCount; ++i) {
          final int finalI = i;
          final int finalT = t;
          PersistentHashMap.ValueDataAppender appender = out -> out.write((finalI + finalT) & 0xFF);
          map.appendData(String.valueOf(i), appender);
        }
      }
      map.close();
      map = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, integerDescriptor);
      for (int i = 0; i < stringsCount; ++i) {
        if (i < 2 * stringsCount / 3) {
          map.remove(String.valueOf(i));
        }
      }
      map.close();
      final boolean isSmall = stringsCount < 1000000;
      assertTrue(isSmall || map.makesSenseToCompact());
      long started = System.currentTimeMillis();

      map = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, integerDescriptor);
      if (isSmall) map.compact();
      else assertTrue(map.isDirty());  // autocompact on open should leave the map dirty
      assertTrue(!map.makesSenseToCompact());
      System.out.println(System.currentTimeMillis() - started);
      for (int i = 0; i < stringsCount; ++i) {
        if (i >= 2 * stringsCount / 3) {
          Integer s = map.get(String.valueOf(i));
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

  public void testIntToIntMapPerformance() throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");
    FileUtil.createParentDirs(file);

    int size = 10000000;
    TIntIntHashMap checkMap = new TIntIntHashMap(size);
    Random r = new Random(1);
    while(size != checkMap.size()) {
      if (checkMap.size() == 0) {
        checkMap.put(r.nextInt(), 0);
        checkMap.put(r.nextInt(), 0);
        checkMap.put(0, Math.abs(r.nextInt()));
      } else {
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
        }catch(IOException e) {
          e.printStackTrace();
          assertTrue(false);
          return  false;
        }
        return true;
      });
      assertTrue(result);
      map.close();
      System.out.println("Done:"+(System.currentTimeMillis() - started));
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
        }catch(IOException e) {
          e.printStackTrace();
          assertTrue(false);
          return false;
        }
        return true;
      });
      assertTrue(result);

      System.out.println("Done 2:"+(System.currentTimeMillis() - started));
    }
    finally {
      clearMap(file, map);
    }
  }

  public void test2GLimitWithAppend() throws IOException {
    File file = FileUtil.createTempFile("persistent", "map");
    FileUtil.createParentDirs(file);
    class PathCollectionExternalizer implements DataExternalizer<Collection<String>> {
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
    PathCollectionExternalizer externalizer = new PathCollectionExternalizer();
    PersistentHashMap<String, Collection<String>> map = null;

    try {
      map = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE,
                                                              externalizer);
      for (int j = 0; j < 7; ++j) {
        for (int i = 0; i < 2000; i++) {
          final int finalJ = j;
          map.appendData("abc" + i, out -> IOUtil.writeString(StringUtil.repeat("0123456789", 10000 + finalJ - 3), out));
        }
        map.force();
      }

      map.close();

      map = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, externalizer);

      long len = 0;

      for (String key : map.getAllKeysWithExistingMapping()) {
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
}
