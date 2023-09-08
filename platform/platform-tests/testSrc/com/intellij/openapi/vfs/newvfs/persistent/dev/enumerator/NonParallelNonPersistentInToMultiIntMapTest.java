// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.NonParallelNonPersistentIntToMultiIntMap;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class NonParallelNonPersistentInToMultiIntMapTest extends IntToMultiIntMapTestBase<NonParallelNonPersistentIntToMultiIntMap> {

  public NonParallelNonPersistentInToMultiIntMapTest() {
  }

  @Override
  protected NonParallelNonPersistentIntToMultiIntMap create(@NotNull Path tempDir) {
    return new NonParallelNonPersistentIntToMultiIntMap();
  }
}
