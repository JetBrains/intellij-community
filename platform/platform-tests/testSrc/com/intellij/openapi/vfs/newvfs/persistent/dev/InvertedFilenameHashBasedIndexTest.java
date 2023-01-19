// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 *
 */
public class InvertedFilenameHashBasedIndexTest {

  private static final int ENOUGH_ENTRIES_TO_CHECK = 200_000;
  /**
   * Not too many because test ~O(N^2) on that
   */
  private static final int ENOUGH_COLLISIONS_TO_CHECK = 1000;

  private final InvertedFilenameHashBasedIndex index = new InvertedFilenameHashBasedIndex();

  @Test
  public void indexIsAbleToDealWithZeroHashCodeNames() {
    final String stringWithZeroHash = "\u0000";

    assertEquals("'\\u0000'.hashCode() should be 0", stringWithZeroHash.hashCode(), 0);
    index.addFileName(1, stringWithZeroHash); //no exceptions
  }

  @Test
  public void manyFilesWithSameNameIsOK() {
    final int[] fileIds = IntStream.range(1, ENOUGH_COLLISIONS_TO_CHECK).toArray();
    final String fileName = "A";
    for (final int fileId : fileIds) {
      index.addFileName(fileId, fileName);
    }
    final IntArraySet fileIdsFound = lookupIndexByFileName(index, fileName);
    assertEquals(
      "All fileIds with the same name should be found",
      new IntArraySet(fileIds),
      fileIdsFound
    );
  }

  @Test
  public void allFileIdsAddedToIndexCouldBeFoundByName() {
    final Int2ObjectMap<String> fileIdToName = generateFileNames(ENOUGH_ENTRIES_TO_CHECK);
    final Map<String, IntArraySet> etalon = new Object2ObjectOpenHashMap<>();
    for (Int2ObjectMap.Entry<String> e : fileIdToName.int2ObjectEntrySet()) {
      final int fileId = e.getIntKey();
      final String fileName = e.getValue();
      index.addFileName(fileId, fileName);
      etalon.computeIfAbsent(fileName, _name -> new IntArraySet())
        .add(fileId);
    }

    for (String fileName : new HashSet<>(fileIdToName.values())) {
      final IntArraySet trueFileIds = etalon.get(fileName);
      final IntArraySet fileIdsFromIndex = lookupIndexByFileName(index, fileName);

      assertTrue(
        "index _must_ return all 'true' fileIds (+maybe few 'false' ones)",
        fileIdsFromIndex.containsAll(trueFileIds)
      );
    }
  }

  @Test
  public void afterManyFileIdsAddedAndRemovedFromIndex_NothingCouldBeFoundByName() {
    final Int2ObjectMap<String> fileIdToName = generateFileNames(ENOUGH_ENTRIES_TO_CHECK);
    for (Int2ObjectMap.Entry<String> e : fileIdToName.int2ObjectEntrySet()) {
      final int fileId = e.getIntKey();
      final String fileName = e.getValue();
      index.addFileName(fileId, fileName);
      assertTrue(
        "Index must return fileId just added",
        lookupIndexByFileName(index, fileName).contains(fileId)
      );
    }

    for (Int2ObjectMap.Entry<String> e : fileIdToName.int2ObjectEntrySet()) {
      final int fileId = e.getIntKey();
      final String fileName = e.getValue();
      index.removeFileName(fileId, fileName);
      assertFalse(
        "Index must NOT return fileId just removed",
        lookupIndexByFileName(index, fileName).contains(fileId)
      );
    }

    for (String fileName : new HashSet<>(fileIdToName.values())) {
      final IntArraySet fileIdsFromIndex = lookupIndexByFileName(index, fileName);

      assertTrue(
        "Index must return NOTHING after all entries removed",
        fileIdsFromIndex.isEmpty()
      );
    }
  }

  @Test//test is flaky by its nature, but failing probability should be quite low
  public void notTooManyFalsePositivesInIndexLookups() {
    final Int2ObjectMap<String> fileIdToName = generateFileNames(ENOUGH_ENTRIES_TO_CHECK);
    final Map<String, IntArraySet> etalon = new Object2ObjectOpenHashMap<>();
    for (Int2ObjectMap.Entry<String> e : fileIdToName.int2ObjectEntrySet()) {
      final int fileId = e.getIntKey();
      final String fileName = e.getValue();
      index.addFileName(fileId, fileName);
      etalon.computeIfAbsent(fileName, _name -> new IntArraySet())
        .add(fileId);
    }

    int surplus = 0;
    for (String fileName : new HashSet<>(fileIdToName.values())) {
      final IntArraySet trueFileIds = etalon.get(fileName);
      final IntArraySet fileIdsFromIndex = lookupIndexByFileName(index, fileName);

      surplus += fileIdsFromIndex.size() - trueFileIds.size();
    }

    //Single collision probability is ~1-exp(-ENTRIES^2/2^33) ~ 1e-4 = 0.01% (see birthday paradox)
    final double collisionProbability =
      -Math.expm1(-(ENOUGH_ENTRIES_TO_CHECK * 1.0 * ENOUGH_ENTRIES_TO_CHECK) / 2.0 / 2.0 / Integer.MAX_VALUE);

    // put strong upper bound on false positives count:
    final double falsePositivesProbabilityUpperBound = 4 * collisionProbability;
    assertTrue(
      "Expect < " + falsePositivesProbabilityUpperBound + " 'false positives' fraction, " +
      "but got " + (surplus * 1.0 / ENOUGH_ENTRIES_TO_CHECK) + " (" + surplus + " FP out of " + ENOUGH_ENTRIES_TO_CHECK + " total entries)",
      surplus <= falsePositivesProbabilityUpperBound * ENOUGH_ENTRIES_TO_CHECK
    );
  }


  @NotNull
  private static IntArraySet lookupIndexByFileName(final InvertedFilenameHashBasedIndex index,
                                                   final String fileName) {
    final IntArraySet fileIds = new IntArraySet();
    index.likelyFilesWithNames(Set.of(fileName), fileId -> {
      fileIds.add(fileId);
      return true;
    });
    return fileIds;
  }

  @NotNull
  private static Int2ObjectMap<String> generateFileNames(final int count) {
    final Int2ObjectOpenHashMap<String> fileIdToName = new Int2ObjectOpenHashMap<>(count);
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();

    final int maxNameSize = 50;
    final List<String> names = IntStream.range(0, count / 2)// E.V. 2 files  per each name
      .mapToObj(
        i -> randomAlphanumericString(rnd, rnd.nextInt(1, maxNameSize))
      ).toList();

    for (int i = 0; i < count; i++) {
      int fileId;
      do {
        fileId = rnd.nextInt(Integer.MAX_VALUE);
      }
      while (fileIdToName.containsKey(fileId));

      final String name = names.get(rnd.nextInt(names.size()));
      fileIdToName.put(fileId, name);
    }
    return fileIdToName;
  }

  @NotNull
  private static String randomAlphanumericString(final ThreadLocalRandom rnd,
                                                 final int size) {
    final char[] chars = new char[size];
    for (int i = 0; i < chars.length; i++) {
      chars[i] = Character.forDigit(rnd.nextInt(0, 36), 36);
    }
    return new String(chars);
  }
}