// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap;

import com.intellij.util.io.dev.intmultimaps.IntToMultiIntMapTestBase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public class ExtendibleHashmapTest extends IntToMultiIntMapTestBase<ExtendibleHashMap> {

  public ExtendibleHashmapTest() {
    super(1_000_000);
  }

  @Override
  protected ExtendibleHashMap create(@NotNull Path tempDir) throws IOException {
    Path storagePath = tempDir.resolve("map.map");
    return ExtendibleMapFactory.defaults().open(storagePath);
  }
}