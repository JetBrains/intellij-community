// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.enumerator;

import com.intellij.util.io.StringEnumeratorTestBase;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.intmultimaps.DurableIntToMultiIntMap;
import com.intellij.platform.util.io.storages.intmultimaps.NonDurableNonParallelIntToMultiIntMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8;


//TODO RC: move to platform.util.storage module's tests
public class DurableEnumeratorOfStringsWithInMemoryMapTest extends StringEnumeratorTestBase<DurableEnumerator<String>> {

  public DurableEnumeratorOfStringsWithInMemoryMapTest() {
    super(/*valuesToTestOn: */ 500_000);
  }

  @Override
  protected DurableEnumerator<String> openEnumeratorImpl(@NotNull Path storagePath) throws IOException {
    return DurableEnumeratorFactory.defaultWithDurableMap(stringAsUTF8())
      .valuesLogFactory(DurableEnumeratorFactory.DEFAULT_VALUES_LOG_FACTORY)
      .mapFactory((StorageFactory<? extends DurableIntToMultiIntMap>)path -> new NonDurableNonParallelIntToMultiIntMap())
      .rebuildMapIfInconsistent(true)
      .open(storagePath);
  }
}