// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap;

import com.intellij.openapi.vfs.newvfs.persistent.StorageTestingUtils;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.dev.intmultimaps.IntToMultiIntMapTestBase;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory.NotClosedProperlyAction.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExtendibleHashMapTest extends IntToMultiIntMapTestBase<ExtendibleHashMap> {


  public ExtendibleHashMapTest() {
    super(/*entriesToTest: */1_000_000);
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
  public void notProperlyClosedMap_isDetectedAsNotProperlyClosed_onReopen() throws Exception {
    multimap.put(1, 2);//put anything so map is 'modified'
    StorageTestingUtils.emulateImproperClose(multimap);

    multimap = ExtendibleMapFactory.defaults()
      .open(storagePath);
    assertFalse(
      multimap.wasProperlyClosed(),
      "Not-closed ExtendibleHashMap is considered NOT 'properly closed'"
    );
  }

  @Test
  public void notProperlyClosedMap_isReopenedAsIs_ifFactoryConfigured_IGNORE_AND_HOPE_FOR_THE_BEST() throws Exception {
    multimap.put(1, 2);//put anything so map is 'modified'
    StorageTestingUtils.emulateImproperClose(multimap);

    multimap = ExtendibleMapFactory.defaults()
      .ifNotClosedProperly(IGNORE_AND_HOPE_FOR_THE_BEST)
      .open(storagePath);
    assertFalse(
      multimap.wasProperlyClosed(),
      "Not-closed ExtendibleHashMap is considered NOT 'properly closed'"
    );
    assertEquals(
      1,
      multimap.size(),
      "reopened map has single entry it was put before"
    );
  }

  @Test
  public void notProperlyClosedMap_IsDroppedAndCreatedEmpty_ifFactoryConfigured_DROP_AND_CREATE_EMPTY_MAP() throws Exception {
    multimap.put(1, 2);//put anything so map is 'modified'
    StorageTestingUtils.emulateImproperClose(multimap);

    multimap = ExtendibleMapFactory.defaults()
      .ifNotClosedProperly(DROP_AND_CREATE_EMPTY_MAP)
      .open(storagePath);
    assertTrue(
      multimap.wasProperlyClosed(),
      "Must be 'properly closed' since the map was re-created from 0 (factory's strategy=DROP_AND_CREATE_EMPTY_MAP)"
    );
    assertEquals(
      0,
      multimap.size(),
      "Must be empty map, since it wasn't properly-closed, and factory's strategy=DROP_AND_CREATE_EMPTY_MAP"
    );
  }

  @Test
  public void notProperlyClosedMap_FailsToReopen_ifFactoryConfiguredTo_FAIL_SPECTACULARLY() throws Exception {
    multimap.put(1, 2);//put anything so map is 'modified'
    StorageTestingUtils.emulateImproperClose(multimap);

    assertThrows(
      CorruptedException.class,
      () -> {
        multimap = ExtendibleMapFactory.defaults()
          .ifNotClosedProperly(FAIL_SPECTACULARLY)
          .open(storagePath);
      });
  }


  private Path storagePath;

  @Override
  protected ExtendibleHashMap create(@NotNull Path tempDir) throws IOException {
    storagePath = tempDir.resolve("map.map");
    return ExtendibleMapFactory.defaults().open(storagePath);
  }
}