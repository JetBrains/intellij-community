// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.intmultimaps;

import org.jetbrains.annotations.NotNull;
import org.junit.AssumptionViolatedException;

import java.io.IOException;
import java.nio.file.Path;

public class NonParallelNonPersistentInToMultiIntMapTest extends DurableIntToMultiIntMapTestBase<NonDurableNonParallelIntToMultiIntMap> {

  public NonParallelNonPersistentInToMultiIntMapTest() {
    super(500_000);
  }

  @Override
  protected NonDurableNonParallelIntToMultiIntMap openInDir(@NotNull Path tempDir) {
    return new NonDurableNonParallelIntToMultiIntMap();
  }

  @Override
  public void ZERO_IS_PROHIBITED_KEY() throws IOException {
    throw new AssumptionViolatedException("NonDurableNonParallelIntToMultiIntMap is implemented it differently");
  }

  @Override
  public void ZERO_IS_PROHIBITED_VALUE() throws IOException {
    throw new AssumptionViolatedException("NonDurableNonParallelIntToMultiIntMap is implemented it differently");
  }
}
