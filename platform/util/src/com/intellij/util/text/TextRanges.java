/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.text;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.UnmodifiableIterator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TextRanges implements Iterable<TextRange> {
  private final static Comparator<TextRange> START_COMP = new Comparator<TextRange>() {
    @Override
    public int compare(TextRange o1, TextRange o2) {
      return Comparing.compare(o1.getStartOffset(), o2.getStartOffset());
    }
  };
  private final static Comparator<TextRange> END_COMP = new Comparator<TextRange>() {
    @Override
    public int compare(TextRange o1, TextRange o2) {
      return Comparing.compare(o1.getEndOffset(), o2.getEndOffset());
    }
  };
  private final List<TextRange> myRanges = ContainerUtil.newArrayList();

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
    return new UnmodifiableIterator<TextRange>(myRanges.iterator());
  }

  public Iterator<TextRange> revIterator() {
    return new Iterator<TextRange>() {
      private ListIterator<TextRange> it = myRanges.listIterator(myRanges.size());

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
