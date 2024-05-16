// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap;

import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMap.HashMapSegmentLayout;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SegmentLayoutTest {

  public static final int SEGMENT_INDEX = 1;
  public static final int SEGMENT_SIZE = 1 << 16;

  private HashMapSegmentLayout segmentLayout;
  private ExtendibleHashMap.BufferSource bufferSource;

  @BeforeEach
  void setUp() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(SEGMENT_SIZE);
    bufferSource = new ExtendibleHashMap.BufferSource() {
      @Override
      public @NotNull ByteBuffer slice(long offsetInFile, int length) throws IOException {
        return buffer;
      }

      @Override
      public int getInt(long offsetInFile) throws IOException {
        //we emulate 1st segment (because first segment is header, it is special), but buffer is 0-based
        return buffer.getInt((int)offsetInFile - SEGMENT_SIZE);
      }
    };
    segmentLayout = new HashMapSegmentLayout(
      bufferSource,
      SEGMENT_INDEX,
      SEGMENT_SIZE
    );
  }

  @Test
  void initially_thereIsNoAliveEntries() throws IOException {
    assertEquals(
      0,
      segmentLayout.aliveEntriesCount()
    );
  }

  @Test
  void initially_allEntriesAreZero() {
    int entriesCount = segmentLayout.entriesCount();
    for (int i = 0; i < entriesCount; i++) {
      assertEquals(0, segmentLayout.entryKey(i));
      assertEquals(0, segmentLayout.entryValue(i));
    }
  }

  @Test
  void allEntries_CouldBeWritten_AndReadBack() {
    int entriesCount = segmentLayout.entriesCount();
    for (int i = 0; i < entriesCount; i++) {
      segmentLayout.updateEntry(i, i * 2, i * 3);
    }
    for (int i = 0; i < entriesCount; i++) {
      assertEquals(i * 2, segmentLayout.entryKey(i));
      assertEquals(i * 3, segmentLayout.entryValue(i));
    }
  }

  @Test
  void hashSuffixMask_containsHashSuffixDepthBits() {
    for (byte suffixDepth = 0; suffixDepth <= Integer.SIZE; suffixDepth++) {
      int hashSuffix = (1 << suffixDepth) - 1;
      segmentLayout.updateHashSuffix(hashSuffix, suffixDepth);

      assertEquals(
        suffixDepth,
        Integer.bitCount(segmentLayout.hashSuffixMask()),
        "hashSuffixMask(" + suffixDepth + ") must have " + suffixDepth + " 1-bits"
      );
    }
  }

  @Test
  void hashSuffixMask_containsHashSuffixDepthLowestBits() {
    for (byte suffixDepth = 0; suffixDepth < Integer.SIZE; suffixDepth++) {
      int hashSuffix = (1 << suffixDepth) - 1;
      segmentLayout.updateHashSuffix(hashSuffix, suffixDepth);

      assertEquals(
        (1 << suffixDepth) - 1,
        segmentLayout.hashSuffixMask(),
        "hashSuffixMask(" + suffixDepth + ") must be " + Integer.toBinaryString((1 << suffixDepth) - 1)
      );
    }

    segmentLayout.updateHashSuffix(-1, (byte)32);
    assertEquals(
      -1,
      segmentLayout.hashSuffixMask(),
      "hashSuffixMask(32) must be " + Integer.toBinaryString(-1)
    );
  }

  @Test
  void slotIndexesForSegment_produceExpectedResultsOnATestSet() {
    assertArrayEquals(
      ExtendibleHashMap.slotIndexesForSegment(0b0, (byte)0, (byte)0),
      new int[]{0b0}
    );
    assertArrayEquals(
      ExtendibleHashMap.slotIndexesForSegment(0b1, (byte)1, (byte)1),
      new int[]{0b1}
    );
    assertArrayEquals(
      ExtendibleHashMap.slotIndexesForSegment(0b1, (byte)1, (byte)2),
      new int[]{0b01, 0b11}
    );
    assertArrayEquals(
      ExtendibleHashMap.slotIndexesForSegment(0b11, (byte)2, (byte)3),
      new int[]{0b011, 0b111}
    );
    assertArrayEquals(
      ExtendibleHashMap.slotIndexesForSegment(0b11, (byte)2, (byte)4),
      new int[]{0b0011, 0b0111, 0b1011, 0b1111}
    );
  }

  @Test
  void updated_aliveEntriesCount_MustBeReadViaBothAccessors() throws IOException {
    for (int aliveEntriesCount : new int[]{1, 10, 42, Integer.MAX_VALUE / 2}) {

      segmentLayout.updateAliveEntriesCount(aliveEntriesCount);

      assertEquals(
        aliveEntriesCount,
        segmentLayout.aliveEntriesCount(),
        "Must be the value just updated in"
      );

      assertEquals(
        aliveEntriesCount,
        HashMapSegmentLayout.aliveEntriesCount(bufferSource, 1, SEGMENT_SIZE),
        "Must be the value just updated in"
      );
    }
  }
}
