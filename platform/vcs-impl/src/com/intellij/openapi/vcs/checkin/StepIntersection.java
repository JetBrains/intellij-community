// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.PeekableIteratorWrapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;


@ApiStatus.Internal
public final class StepIntersection {
  /**
   * Iterate over intersected ranges in two lists, sorted by TextRange.
   */
  public static <T, V> void processIntersections(@NotNull List<? extends T> elements1,
                                                 @NotNull List<? extends V> elements2,
                                                 @NotNull Convertor<? super T, ? extends TextRange> convertor1,
                                                 @NotNull Convertor<? super V, ? extends TextRange> convertor2,
                                                 @NotNull PairConsumer<? super T, ? super V> intersectionConsumer) {
    PeekableIteratorWrapper<T> peekIterator1 = new PeekableIteratorWrapper<>(elements1.iterator());

    outerLoop:
    for (V item2 : elements2) {
      TextRange range2 = convertor2.convert(item2);

      while (peekIterator1.hasNext()) {
        T item1 = peekIterator1.peek();
        TextRange range1 = convertor1.convert(item1);

        if (range1.intersects(range2)) {
          intersectionConsumer.consume(item1, item2);
        }

        if (range2.getEndOffset() < range1.getEndOffset()) {
          continue outerLoop;
        }
        else {
          peekIterator1.next();
        }
      }

      break;
    }
  }

  public static <T, V> void processElementIntersections(@NotNull T element1,
                                                        @NotNull List<? extends V> elements2,
                                                        @NotNull Convertor<? super T, ? extends TextRange> convertor1,
                                                        @NotNull Convertor<? super V, ? extends TextRange> convertor2,
                                                        @NotNull PairConsumer<? super T, ? super V> intersectionConsumer) {
    TextRange range1 = convertor1.convert(element1);
    int index = ObjectUtils.binarySearch(0, elements2.size(), mid -> {
      V item2 = elements2.get(mid);
      TextRange range2 = convertor2.convert(item2);
      return Integer.compare(range2.getEndOffset(), range1.getStartOffset());
    });
    if (index < 0) index = -index - 1;

    for (int i = index; i < elements2.size(); i++) {
      V item2 = elements2.get(i);
      TextRange range2 = convertor2.convert(item2);

      if (range1.intersects(range2)) {
        intersectionConsumer.consume(element1, item2);
      }

      if (range2.getStartOffset() > range1.getEndOffset()) break;
    }
  }
}
