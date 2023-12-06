// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap;

import com.intellij.openapi.vfs.newvfs.persistent.StorageTestingUtils;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.dev.intmultimaps.IntToMultiIntMapTestBase;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.extendiblehashmap.ExtendibleMapFactory.NotClosedProperlyAction.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExtendibleHashMapTest extends IntToMultiIntMapTestBase<ExtendibleHashMap> {

  public ExtendibleHashMapTest() {
    super(/*entriesToTest: */4_000_000);
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
    multimap = openFile(storagePath);
    assertTrue(
      multimap.wasProperlyClosed(),
      "Closed and reopened ExtendibleHashMap is considered 'properly closed'"
    );
  }

  @Test
  public void notProperlyClosedMap_isDetectedAsNotProperlyClosed_onReopen() throws Exception {
    multimap.put(1, 2);//put anything so map is 'modified'
    StorageTestingUtils.emulateImproperClose(multimap);

    multimap = openFile(storagePath);
    assertFalse(
      multimap.wasProperlyClosed(),
      "Not-closed ExtendibleHashMap is considered NOT 'properly closed'"
    );
  }

  @Test
  public void notProperlyClosedMap_isReopenedAsIs_ifFactoryConfigured_IGNORE_AND_HOPE_FOR_THE_BEST() throws Exception {
    multimap.put(1, 2);//put anything so map is 'modified'
    StorageTestingUtils.emulateImproperClose(multimap);

    multimap = open(
      ExtendibleMapFactory.defaults().ifNotClosedProperly(IGNORE_AND_HOPE_FOR_THE_BEST),
      storagePath
    );
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

    multimap = open(
      ExtendibleMapFactory.defaults().ifNotClosedProperly(DROP_AND_CREATE_EMPTY_MAP),
      storagePath
    );
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
        multimap = open(
          ExtendibleMapFactory.defaults().ifNotClosedProperly(FAIL_SPECTACULARLY),
          storagePath
        );
      });
  }

  @Test
  public void closeAndClean_RemovesTheUnderlyingFile() throws IOException {
    //RC: it is over-specification -- .closeAndClean() doesn't require to remove the file, only to clean the
    //    content so new storage opened on top of it will be as-new. But this is the current implementation
    //    of that spec:
    multimap.closeAndClean();
    assertFalse(
      Files.exists(storagePath),
      "File [" + storagePath + "] must NOT exist after .closeAndClean()"
    );
  }

  //===================== infrastructure ===============================================================

  private Path storagePath;
  private final List<ExtendibleHashMap> multimapsToCloseAndClean = new ArrayList<>();

  @Override
  protected ExtendibleHashMap openInDir(@NotNull Path tempDir) throws IOException {
    if (!Files.isDirectory(tempDir)) {
      throw new IllegalArgumentException(tempDir + " is not a directory");
    }
    Path storagePath = tempDir.resolve("map.map");
    return openFile(storagePath);
  }

  protected ExtendibleHashMap openFile(@NotNull Path storagePath) throws IOException {
    return open(ExtendibleMapFactory.defaults(), storagePath);
  }

  private ExtendibleHashMap open(@NotNull ExtendibleMapFactory factory,
                                 @NotNull Path storagePath) throws IOException {
    this.storagePath = storagePath;
    ExtendibleHashMap multimap = factory.open(storagePath);

    multimapsToCloseAndClean.add(multimap);

    return multimap;
  }

  @AfterEach
  void tearDown() throws IOException {
    //TODO RC: there is no .forEach() & .isClosed() in NonDurableNonParallelIntToMultiIntMap
    //         to use the same approach in base class -- but probably it is worth to do also?
    for (ExtendibleHashMap mapToCheck : multimapsToCloseAndClean) {
      if(!mapToCheck.isClosed()) {
        assertInvariant_ValuesForEachKeysAreUnique(mapToCheck);
      }
    }

    for (ExtendibleHashMap mapToClean : multimapsToCloseAndClean) {
      mapToClean.closeAndUnsafelyUnmap();
    }
    //RC: maybe we can't remove the first once -- because second one is not yet unmapped?
    for (ExtendibleHashMap mapToClean : multimapsToCloseAndClean) {
      mapToClean.closeAndClean();
    }
  }

  private static void assertInvariant_ValuesForEachKeysAreUnique(@NotNull ExtendibleHashMap multimap) throws IOException {
    IntOpenHashSet keys = new IntOpenHashSet();
    multimap.forEach((key, value) -> keys.add(key));
    for (int key : keys) {
      IntOpenHashSet values = new IntOpenHashSet();
      multimap.lookup(key, value -> {
        if (!values.add(value)) {
          fail("get(" + key + ") values are non-unique: value[" + value + "] was already reported " + values);
        }
        return true;
      });
    }
  }
}