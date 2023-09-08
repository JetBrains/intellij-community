// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap;

import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.IntToMultiIntMapTestBase;
import com.intellij.openapi.vfs.newvfs.persistent.mapped.MMappedFileStorage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 *
 */
public class ExtendibleHashmapTest extends IntToMultiIntMapTestBase<ExtendibleHashmap> {

  public ExtendibleHashmapTest() {
    super(1_000_000);
  }

  @Override
  protected ExtendibleHashmap create(@NotNull Path tempDir) throws IOException {
    Path storagePath = tempDir.resolve("map.map");
    MMappedFileStorage storage = new MMappedFileStorage(storagePath, 1 << 20, 1 << 29);
      return new ExtendibleHashmap(
        storage,
        1 << 16
      );
  }
}