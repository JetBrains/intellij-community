// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.mvstore.MVStore;
import org.jetbrains.mvstore.index.MVStorePersistentHashMap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PersistentMapComparisonPerformanceTest extends UsefulTestCase {
  @Nullable
  private static List<Pair<Integer, String>> ourData;
  @NotNull
  private Path myTempDirectory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getData();
    myTempDirectory = FileUtil.createTempDirectory("persistent-map", "comparison").toPath();
  }

  public synchronized static @NotNull List<Pair<Integer, String>> getData() {
    if (ourData == null) {
      int size = 500_000;
      ourData = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        ourData.add(Pair.create(i, PersistentMapTestBase.createRandomString()));
      }
      ourData.sort(Comparator.comparingLong(p -> p.getSecond().length()));
    }
    return ourData;
  }

  public void testMVStoreMap() throws IOException {
    MVStore mvStore = new MVStore
      .Builder()
      .autoCommitDelay(5_000)
      .compressHigh()
      .openOrNewOnIoError(myTempDirectory.resolve("mvstore"), true, e -> LOG.error(e));
    MVStorePersistentHashMap<Integer, String> map =
      new MVStorePersistentHashMap<>("test-map",
                                     mvStore,
                                     EnumeratorIntegerDescriptor.INSTANCE,
                                     EnumeratorStringDescriptor.INSTANCE);
    try {
      PlatformTestUtil.startPerformanceTest("put/get MVStore", 2000, () -> {
        doPutGetTest(map);
      }).ioBound().assertTiming();
    }
    finally {
      map.close();
      mvStore.close();
    }
  }

  public void testPersistentHashMap() throws IOException {
    PersistentHashMap<Integer, String> phm = PersistentHashMapBuilder
      .newBuilder(myTempDirectory.resolve("phm"), EnumeratorIntegerDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE).build();
    try {
      PlatformTestUtil.startPerformanceTest("put/get PHM", 2000, () -> {
        doPutGetTest(phm);
      }).ioBound().assertTiming();
    }
    finally {
      phm.close();
    }
  }

  private static void doPutGetTest(@NotNull AppendablePersistentMap<Integer, String> mapBase) throws IOException {
    for (Pair<Integer, String> datum : getData()) {
      mapBase.put(datum.getFirst(), datum.getSecond());
    }

    for (Pair<Integer, String> datum : getData()) {
      assertEquals(datum.getSecond(), mapBase.get(datum.getFirst()));
    }
  }
}
