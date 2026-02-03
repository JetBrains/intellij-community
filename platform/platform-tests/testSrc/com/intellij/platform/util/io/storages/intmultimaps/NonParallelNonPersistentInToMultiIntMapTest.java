// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class NonParallelNonPersistentInToMultiIntMapTest extends DurableIntToMultiIntMapTestBase<NonDurableNonParallelIntToMultiIntMap> {

  public NonParallelNonPersistentInToMultiIntMapTest() {
    super(500_000);
  }

  @Override
  protected NonDurableNonParallelIntToMultiIntMap openInDir(@NotNull Path tempDir) {
    return new NonDurableNonParallelIntToMultiIntMap();
  }

}
