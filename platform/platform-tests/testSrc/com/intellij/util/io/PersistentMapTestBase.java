// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public abstract class PersistentMapTestBase extends UsefulTestCase {
  protected static final Logger LOG = Logger.getInstance(PersistentMapTestBase.class);
  protected PersistentHashMap<String, String> myMap;
  protected File myFile;
  protected File myDataFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    File directory = FileUtil.createTempDirectory("persistent", "map");
    myFile = new File(directory, "map");
    assertTrue(myFile.createNewFile());
    myDataFile = new File(directory, myFile.getName() + PersistentMapImpl.DATA_FILE_EXTENSION);
    myMap = new PersistentHashMap<>(myFile.toPath(), EnumeratorStringDescriptor.INSTANCE, EnumeratorStringDescriptor.INSTANCE);
  }

  @NotNull
  protected PersistentMapImpl<String, String> unwrapMap() {
    return PersistentMapImpl.unwrap(myMap);
  }

  protected int getMapSize() {
    return unwrapMap().getSize();
  }

  protected int getGarbageSize() {
    return unwrapMap().getGarbageSize();
  }

  protected void compactMap() throws IOException {
    unwrapMap().compact();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      clearMap(myFile, myMap);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myMap = null;

      super.tearDown();
    }
  }

  static void clearMap(final File file1, PersistentHashMap<?, ?> map) throws IOException {
    if (map == null) return;
    map.close();

    assertTrue(IOUtil.deleteAllFilesStartingWith(file1));
  }

  static String createRandomString() {
    return StringEnumeratorTest.createRandomString();
  }
}
