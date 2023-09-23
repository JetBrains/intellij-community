// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap;

import com.intellij.util.io.dev.intmultimaps.IntToMultiIntMapTestBase;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage;
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtendibleHashmapTest extends IntToMultiIntMapTestBase<ExtendibleHashMap> {


  public ExtendibleHashmapTest() {
    super(1_000_000);
  }

  @Test
  public void freshlyCreatedMap_isProperlyClosedByDefinition() {
    assertTrue(
      multimap.wasProperlyClosed(),
      "Freshly created ExtendibleHashMap is always 'properly closed'"
    );
  }

  @Test
  public void closedAndReopenedMap_isProperlyClosed() throws IOException {
    multimap.close();
    multimap = ExtendibleMapFactory.defaults().open(storagePath);
    assertTrue(
      multimap.wasProperlyClosed(),
      "Closed and reopened ExtendibleHashMap is considered 'properly closed'"
    );
  }

  @Test
  public void notClosed_isConsideredNotProperlyClosed_onReopen(@TempDir Path tempDir) throws IOException {
    //We need to completely bypass test harness here -- it is not suited for that kind of test:
    Path storagePath = tempDir.resolve("test.mmap");
    {
      MMappedFileStorage mmappedStorage = MMappedFileStorageFactory.DEFAULT.open(storagePath);
      ExtendibleHashMap map = new ExtendibleHashMap(mmappedStorage, ExtendibleHashMap.DEFAULT_SEGMENT_SIZE);
      map.put(1, 2);
      //close _underlying_ storage -- but don't close the map! -> map remains NOT 'properly closed'
      mmappedStorage.close();
    }
    MMappedFileStorage mmappedStorage = MMappedFileStorageFactory.DEFAULT.open(storagePath);
    ExtendibleHashMap map = new ExtendibleHashMap(mmappedStorage, ExtendibleHashMap.DEFAULT_SEGMENT_SIZE);
    assertFalse(
      map.wasProperlyClosed(),
      "Not-closed ExtendibleHashMap is considered NOT 'properly closed'"
    );
  }


  private Path storagePath;

  @Override
  protected ExtendibleHashMap create(@NotNull Path tempDir) throws IOException {
    storagePath = tempDir.resolve("map.map");
    return ExtendibleMapFactory.defaults().open(storagePath);
  }
}