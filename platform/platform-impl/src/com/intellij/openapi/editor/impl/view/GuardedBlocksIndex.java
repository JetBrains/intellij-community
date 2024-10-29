// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@ApiStatus.Internal
public final class GuardedBlocksIndex {
  private final int[] offsets;
  private final boolean[] guards;
  private final int length;

  GuardedBlocksIndex(int @NotNull [] offsets, boolean @NotNull [] guards, int length) {
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
    @NotNull GuardedBlocksIndex build(int start, int end, @NotNull List<RangeMarker> guardedBlocks) {
      assert 0 <= start && start <= end;
      List<Offset> offsetList = guardedBlocks.stream().flatMap(r -> {
        int rangeStart = r.getStartOffset();
        int rangeEnd = r.getEndOffset();
        assert rangeStart <= rangeEnd;
        if (start - 1 <= rangeEnd && rangeStart <= end + 1) {
          Offset o1 = new Offset(alignOffset(rangeStart, true), true);
          Offset o2 = new Offset(alignOffset(rangeEnd, false), false);
          return Stream.of(o1, o2);
        }
        return Stream.empty();
      }).sorted().toList();
      int size = offsetList.size();
      assert size % 2 == 0;
      int[] offsets = new int[size];
      boolean[] guards = new boolean[size];
      int i = 0, j = 0, stack = 0;
      while (j < size) {
        Offset current = offsetList.get(j);
        stack = current.push(stack);
        for (int k = j + 1; k < size; k++) {
          Offset next = offsetList.get(k);
          if (current.value() == next.value()) {
            current = next;
            stack = current.push(stack);
            j++;
          } else {
            break;
          }
        }
        offsets[i] = current.value();
        guards[i] = stack > 0;
        i++; j++;
      }
      assert stack == 0;
      assert i == 0 || !guards[i-1];
      return new GuardedBlocksIndex(offsets, guards, i);
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

  private record Offset(int value, boolean isStart) implements Comparable<Offset> {
    int push(int stack) {
      int result = isStart ? stack + 1 : stack - 1;
      assert result >= 0;
      return result;
    }

    @Override
    public int compareTo(@NotNull GuardedBlocksIndex.Offset o) {
      int compare = Integer.compare(value(), o.value());
      if (compare != 0) {
        return compare;
      }
      if (isStart() == o.isStart()) {
        return 0;
      }
      return isStart() ? -1 : 1;
    }

    @Override
    public @NotNull String toString() {
      String s = isStart ? "start" : "end";
      return s + "[" + value + "]";
    }
  }
}
