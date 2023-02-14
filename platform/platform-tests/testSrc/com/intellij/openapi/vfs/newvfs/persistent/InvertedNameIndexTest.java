// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.intellij.openapi.vfs.newvfs.persistent.InvertedNameIndex.NULL_NAME_ID;
import static org.junit.Assert.*;

/**
 *
 */
public class InvertedNameIndexTest {

  private static final int ENOUGH_MAPPINGS = 100_000;

  @Test
  public void singleFileIdMappedToNameIdCouldBeListedBack() {
    final int fileId = 1;
    final int nameId = 42;
    InvertedNameIndex.updateFileName(fileId, nameId, NULL_NAME_ID);

    final IntArraySet fileIds = fileIdsByNameId(nameId);
    assertTrue(
      "fileId(" + fileId + ") indexed must be the one reported back",
      fileIds.size() == 1 && fileIds.contains(fileId)
    );
  }

  @Test
  public void allFileIdsMappedToSameNameIdCouldAllBeListedBack() {
    final int[] fileIds = new int[]{1, 2, 3, 4, 5};
    final int nameId = 42;
    for (int fileId : fileIds) {
      InvertedNameIndex.updateFileName(fileId, nameId, NULL_NAME_ID);
    }

    final IntArraySet fileIdsReported = fileIdsByNameId(nameId);

    assertTrue(
      "fileIds(" + Arrays.toString(fileIds) + ") indexed must be the all reported back",
      fileIdsReported.size() == fileIds.length
      && fileIdsReported.containsAll(new IntArraySet(fileIds))
    );
  }

  @Test
  public void singleFileIdToNameIdMappingAddedAndRemovedNotListedBack() {
    final int fileId = 1;
    final int nameId = 11;
    //add fileId -> nameId mapping
    InvertedNameIndex.updateFileName(fileId, nameId, NULL_NAME_ID);
    //remove fileId -> nameId mapping
    InvertedNameIndex.updateFileName(fileId, NULL_NAME_ID, nameId);

    final IntArraySet fileIds = fileIdsByNameId(nameId);

    assertTrue(
      "fileId(" + fileId + ") mapping was removed, nothing should be listed back",
      fileIds.isEmpty()
    );
  }

  @Test
  public void manyFileIdToNameIdMappingsAddedAndRemovedCouldBeListedBack() {
    final Int2ObjectMap<IntArraySet> fileIdToNameId = generateEnoughMappings(ENOUGH_MAPPINGS);
    //add mappings one-by-one, and check each mapping is _absent_ in index before
    // it is added, and _present_ in the index just after it has been added:
    for (Map.Entry<Integer, IntArraySet> entry : fileIdToNameId.int2ObjectEntrySet()) {
      final int fileId = entry.getKey();
      final IntArraySet nameIds = entry.getValue();
      for (int nameId : nameIds) {
        assertFalse(
          "It should be no fileId(" + fileId + ")->nameId(" + nameId + ") mapping yet",
          fileIdsByNameId(nameId).contains(fileId));

        InvertedNameIndex.updateFileName(fileId, nameId, NULL_NAME_ID);

        assertTrue(
          "It should be fileId(" + fileId + ")->nameId(" + nameId + ") mapping now",
          fileIdsByNameId(nameId).contains(fileId));
      }
    }

    //Now _remove_ mappings one-by-one, and check each mapping is _present_ in index before
    // it is removed, and _absent_ in the index just after it has been removed:
    for (Map.Entry<Integer, IntArraySet> entry : fileIdToNameId.int2ObjectEntrySet()) {
      final int fileId = entry.getKey();
      final IntArraySet nameIds = entry.getValue();
      for (int nameId : nameIds) {
        assertTrue(
          "It should be fileId(" + fileId + ")->nameId(" + nameId + ") mapping in the index",
          fileIdsByNameId(nameId).contains(fileId));

        //remove mapping (map fileId -> NULL instead of nameId)
        InvertedNameIndex.updateFileName(fileId, NULL_NAME_ID, nameId);

        assertFalse(
          "It should be NO fileId(" + fileId + ")->nameId(" + nameId + ") mapping in the index anymore",
          fileIdsByNameId(nameId).contains(fileId));
      }
    }
  }


  @Before
  public void setUp() throws Exception {
    InvertedNameIndex.clear();
  }

  @After
  public void tearDown() throws Exception {
    InvertedNameIndex.checkConsistency();
    InvertedNameIndex.clear();
  }

  /* ============================ infrastructure ===================================================== */

  @NotNull
  private static IntArraySet fileIdsByNameId(final int nameId) {
    final IntArraySet nameIds = new IntArraySet(new int[]{nameId});
    final IntArraySet fileIds = new IntArraySet();
    InvertedNameIndex.forEachFileIds(nameIds, fId -> {
      fileIds.add(fId);
      return true;
    });
    return fileIds;
  }

  private static Int2ObjectMap<IntArraySet> generateEnoughMappings(final int size) {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final Int2ObjectMap<IntArraySet> fileIdToNameId = new Int2ObjectOpenHashMap<>();
    rnd.ints()
      .distinct()
      .limit(size)
      .forEach(
        i -> {
          final int fileId = Math.abs(i);
          final int[] nameIds = rnd.ints().distinct().limit(20).toArray();
          fileIdToNameId.put(fileId, new IntArraySet(nameIds));
        });
    return fileIdToNameId;
  }
}