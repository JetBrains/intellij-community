// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.UnmodifiableIterator;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class TextRanges implements Iterable<TextRange> {
  private static final Comparator<TextRange> START_COMP = Comparator.comparingInt(TextRange::getStartOffset);
  private static final Comparator<TextRange> END_COMP = Comparator.comparingInt(TextRange::getEndOffset);
  private final List<TextRange> myRanges = new ArrayList<>();

  public TextRanges union(@Nullable TextRange range) {
    if (range == null || range.isEmpty()) return this;
    int startIdx = Collections.binarySearch(myRanges, TextRange.from(range.getStartOffset(), 0), END_COMP);
    int endIdx = Collections.binarySearch(myRanges, TextRange.from(range.getEndOffset(), 0), START_COMP);

    if (startIdx == endIdx) {
      assert startIdx < 0;
      myRanges.add(-startIdx - 1, range);
      return this;
    }
    if (startIdx < 0) startIdx = -startIdx - 1;
    if (endIdx < 0) endIdx = -endIdx - 1;
    List<TextRange> covered = myRanges.subList(startIdx, endIdx);
    TextRange newRange = new TextRange(
      Math.min(range.getStartOffset(), covered.get(0).getStartOffset()),
      Math.max(range.getEndOffset(), covered.get(covered.size() - 1).getEndOffset())
    );
    covered.clear();
    covered.add(newRange);
    return this;
  }

  @Override
  public Iterator<TextRange> iterator() {
    return new UnmodifiableIterator<>(myRanges.iterator());
  }

  public Iterator<TextRange> revIterator() {
    return new Iterator<TextRange>() {
      private final ListIterator<TextRange> it = myRanges.listIterator(myRanges.size());

      @Override
      public boolean hasNext() {
        return it.hasPrevious();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

      @Override
      public TextRange next() {
        return it.previous();
      }
    };
  }

  public Iterator<TextRange> gapIterator() {
    return gapIterator(iterator());
  }

  public Iterator<TextRange> revGapIterator() {
    return gapIterator(revIterator());
  }

  private static Iterator<TextRange> gapIterator(final Iterator<TextRange> base) {
    return new Iterator<TextRange>() {
      private final Iterator<TextRange> myIt = base;
      private TextRange myPrev = myIt.hasNext() ? myIt.next() : null;

      @Override
      public boolean hasNext() {
        return myPrev != null && myIt.hasNext();
      }

      @Override
      public TextRange next() {
        TextRange cur = myIt.next();
        TextRange res = TextRange.create(myPrev.getEndOffset(), cur.getStartOffset());
        myPrev = cur;
        return res;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public boolean isEmpty() {
    return myRanges.isEmpty();
  }
}
