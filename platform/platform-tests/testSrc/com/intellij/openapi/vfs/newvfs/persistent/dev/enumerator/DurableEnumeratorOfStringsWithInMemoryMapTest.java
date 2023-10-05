// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.util.io.StringEnumeratorTestBase;
import com.intellij.util.io.dev.StorageFactory;
import com.intellij.util.io.dev.enumerator.StringAsUTF8;
import com.intellij.util.io.dev.intmultimaps.DurableIntToMultiIntMap;
import com.intellij.util.io.dev.intmultimaps.NonDurableNonParallelIntToMultiIntMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;


public class DurableEnumeratorOfStringsWithInMemoryMapTest extends StringEnumeratorTestBase<DurableEnumerator<String>> {

  public DurableEnumeratorOfStringsWithInMemoryMapTest() {
    super(/*valuesToTestOn: */ 500_000);
  }

  @Override
  protected DurableEnumerator<String> openEnumeratorImpl(@NotNull Path storagePath) throws IOException {
    return DurableEnumeratorFactory.defaultWithDurableMap(StringAsUTF8.INSTANCE)
      .valuesLogFactory(DurableEnumeratorFactory.DEFAULT_VALUES_LOG_FACTORY)
      .mapFactory((StorageFactory<? extends DurableIntToMultiIntMap>)path -> new NonDurableNonParallelIntToMultiIntMap())
      .rebuildMapIfInconsistent(true)
      .open(storagePath);
  }
}