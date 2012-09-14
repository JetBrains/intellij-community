/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.util.TextRange;

import java.util.*;

/**
* @author Konstantin Bulenkov
*/
public class SpeedSearchObjectWithWeight {
  private static final Comparator<TextRange> TEXT_RANGE_COMPARATOR = new Comparator<TextRange>() {
    @Override
    public int compare(TextRange o1, TextRange o2) {
      if (o1.getStartOffset() == o2.getStartOffset()) {
        return o2.getEndOffset() - o1.getEndOffset(); //longer is better
      }
      return o1.getStartOffset() - o2.getStartOffset();
    }
  };

  public final Object node;
  final List<TextRange> weights = new ArrayList<TextRange>();

  SpeedSearchObjectWithWeight(Object element, String pattern, SpeedSearchBase speedSearch) {
    this.node = element;
    final String text = speedSearch.getElementText(element);
    if (text != null) {
      final Iterable<TextRange> ranges = speedSearch.getComparator().matchingFragments(pattern, text);
      if (ranges != null) {
        for (TextRange range : ranges) {
          weights.add(range);
        }
      }
    }
    Collections.sort(weights, TEXT_RANGE_COMPARATOR);
  }

  public int compareWith(SpeedSearchObjectWithWeight obj) {
    final List<TextRange> w = obj.weights;
    for (int i = 0; i < weights.size(); i++) {
      if (i >= w.size()) return 1;
      final int result = TEXT_RANGE_COMPARATOR.compare(weights.get(i), w.get(i));
      if (result != 0) {
        return result;
      }
    }

    return 0;
  }

  public static List<SpeedSearchObjectWithWeight> findElement(String s, SpeedSearchBase speedSearch) {
    List<SpeedSearchObjectWithWeight> elements = new ArrayList<SpeedSearchObjectWithWeight>();
    s = s.trim();
    //noinspection unchecked
    final ListIterator<Object> it = speedSearch.getElementIterator(0);
    while (it.hasNext()) {
      final SpeedSearchObjectWithWeight o = new SpeedSearchObjectWithWeight(it.next(), s, speedSearch);
      if (!o.weights.isEmpty()) {
        elements.add(o);
      }
    }
    SpeedSearchObjectWithWeight cur = null;
    ArrayList<SpeedSearchObjectWithWeight> current = new ArrayList<SpeedSearchObjectWithWeight>();
    for (SpeedSearchObjectWithWeight element : elements) {
      if (cur == null) {
        cur = element;
        current.add(cur);
        continue;
      }

      final int i = element.compareWith(cur);
      if (i == 0) {
        current.add(element);
      } else if (i < 0) {
        cur = element;
        current.clear();
        current.add(cur);
      }
    }

    return current;
  }
}
