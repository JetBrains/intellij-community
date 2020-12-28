// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.h2.mvstore.MVStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.mvstore.index.MVStorePersistentMap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PersistentMapComparisonPerformanceTest extends UsefulTestCase {
  @Nullable
  private List<Pair<Integer, String>> myData;
  @NotNull
  private Path myTempDirectory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getData();
    myTempDirectory = FileUtil.createTempDirectory("persistent-map", "comparison").toPath();
  }

  private synchronized @NotNull List<Pair<Integer, String>> getData() {
    if (myData == null) {
      int size = 500_000;
      myData = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        myData.add(Pair.create(i, PersistentMapTestBase.createRandomString()));
      }
      myData.sort(Comparator.comparingLong(p -> p.getSecond().length()));
    }
    return myData;
  }

  public void testMVStoreMap() throws IOException {
    MVStore mvStore = new MVStore
      .Builder()
      .compressHigh()
      .fileName(myTempDirectory.resolve("mvstore").toString())
      .open();

    mvStore.setAutoCommitDelay(5_000);

    PersistentHashMap<Integer, String> map =
      new PersistentHashMap<>(
        new MVStorePersistentMap<>("test-map",
                                   mvStore,
                                   EnumeratorIntegerDescriptor.INSTANCE,
                                   EnumeratorStringDescriptor.INSTANCE));

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
    PersistentHashMap<Integer, String> phm = PersistentMapBuilder
      .newBuilder(myTempDirectory.resolve("phm"), EnumeratorIntegerDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE).build();
    try {
      PlatformTestUtil.startPerformanceTest("put/get PHM", 4000, () -> {
        doPutGetTest(phm);
      }).ioBound().assertTiming();
    }
    finally {
      phm.close();
    }
  }

  private void doPutGetTest(@NotNull PersistentMap<Integer, String> mapBase) throws IOException {
    for (Pair<Integer, String> datum : getData()) {
      mapBase.put(datum.getFirst(), datum.getSecond());
    }

    for (Pair<Integer, String> datum : getData()) {
      assertEquals(datum.getSecond(), mapBase.get(datum.getFirst()));
    }
  }
}
