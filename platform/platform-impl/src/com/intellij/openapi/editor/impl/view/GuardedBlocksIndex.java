// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.SweepProcessor;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@ApiStatus.Internal
public final class GuardedBlocksIndex {
  private final int[] offsets;
  private final boolean[] guards;
  private final int length;

  private GuardedBlocksIndex(int @NotNull [] offsets, boolean @NotNull [] guards, int length) {
    assert offsets.length == guards.length;
    assert length <= offsets.length;

    this.offsets = offsets;
    this.guards = guards;
    this.length = length;
  }

  public int nearestLeft(int offset) {
    int i = indexOfNearestLeft(offset);
    if (i != -1) {
      return offsets[i];
    }
    return -1;
  }

  public int nearestRight(int offset) {
    int i = indexOfNearestRight(offset);
    if (i != -1) {
      return offsets[i];
    }
    return -1;
  }

  public boolean isGuarded(int offset) {
    int i = indexOfNearestLeft(offset);
    if (i != -1) {
      return guards[i];
    }
    return false;
  }

  private int indexOfNearestLeft(int offset) {
    if (offset == -1) { // rtl case
      return -1;
    }
    assert offset >= 0;
    int i = Arrays.binarySearch(offsets, 0, length, offset);
    if (i < 0) {
      i = -(i + 2);
    }
    if (0 <= i && i < length) {
      return i;
    }
    return -1;
  }

  private int indexOfNearestRight(int offset) {
    assert offset >= 0;
    int i = Arrays.binarySearch(offsets, 0, length, offset);
    if (i < 0) {
      i = -(i + 1);
    }
    if (i < length) {
      return i;
    }
    return -1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GuardedBlocksIndex index)) return false;
    return toString().equals(index.toString());
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public String toString() {
    return IntStream.range(0, length)
      .mapToObj(i -> offsets[i] + (guards[i] ? "+" : "-"))
      .collect(Collectors.joining(")[", "[", ")"));
  }

  // Document independent for unit test purpose
  public static sealed class Builder permits DocumentBuilder {
    @VisibleForTesting
    public @NotNull GuardedBlocksIndex build(int start, int end, @NotNull List<? extends RangeMarker> guardedBlocks) {
      assert 0 <= start && start <= end;
      List<TextRange> intervals = new ArrayList<>(guardedBlocks.size());
      for (RangeMarker r : guardedBlocks) {
        int rangeStart = r.getStartOffset();
        int rangeEnd = r.getEndOffset();
        assert rangeStart <= rangeEnd;
        if (start - 1 <= rangeEnd && rangeStart <= end + 1) {
          intervals.add(new TextRange(alignOffset(rangeStart, true), alignOffset(rangeEnd, false)));
        }
      }
      intervals.sort(Segment.BY_START_OFFSET_THEN_END_OFFSET);
      int[] offsets = new int[intervals.size() * 2];
      boolean[] guards = new boolean[offsets.length];
      var sweepProcessor = new SweepProcessor<TextRange>() {
        private int length;
        private int stack;

        @Override
        public boolean process(int offset,
                               @NotNull TextRange interval,
                               boolean atStart,
                               @NotNull Collection<? extends TextRange> overlappingIntervals) {
          stack += atStart ? 1 : -1;
          assert stack >= 0;
          if (length == 0 || offsets[length - 1] != offset) {
            offsets[length++] = offset;
          }
          guards[length - 1] = stack > 0;
          return true;
        }
      };
      SweepProcessor.sweep(processor -> intervals.stream().allMatch(processor::process), sweepProcessor);
      assert sweepProcessor.stack == 0;
      assert sweepProcessor.length == 0 || !guards[sweepProcessor.length - 1];
      return new GuardedBlocksIndex(offsets, guards, sweepProcessor.length);
    }

    protected int alignOffset(int offset, boolean isStart) {
      return offset;
    }
  }

  static final class DocumentBuilder extends Builder {
    private final DocumentEx document;

    DocumentBuilder(@NotNull DocumentEx document) {
      this.document = document;
    }

    @NotNull GuardedBlocksIndex build(int start, int end) {
      return build(start, end, document.getGuardedBlocks());
    }

    @Override
    protected int alignOffset(int offset, boolean isStart) {
      if (DocumentUtil.isInsideSurrogatePair(document, offset)) {
        if (!isStart && (offset + 1 < document.getTextLength())) {
          return offset + 1;
        }
        return offset - 1;
      }
      return offset;
    }
  }
}
