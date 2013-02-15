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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
* @author Konstantin Bulenkov
*/
public class SpeedSearchObjectWithWeight {
  public final Object node;
  public final int weight;

  SpeedSearchObjectWithWeight(Object element, String pattern, SpeedSearchBase speedSearch) {
    this.node = element;
    String text = speedSearch.getElementText(element);
    this.weight = text == null ? Integer.MIN_VALUE : speedSearch.getComparator().matchingDegree(pattern, text);
  }

  public int compareWith(SpeedSearchObjectWithWeight obj) {
    return weight == obj.weight ? 0 : weight < obj.weight ? 1 : -1;
  }

  public static List<SpeedSearchObjectWithWeight> findElement(String s, SpeedSearchBase speedSearch) {
    List<SpeedSearchObjectWithWeight> elements = new ArrayList<SpeedSearchObjectWithWeight>();
    s = s.trim();
    //noinspection unchecked
    final ListIterator<Object> it = speedSearch.getElementIterator(0);
    while (it.hasNext()) {
      final SpeedSearchObjectWithWeight o = new SpeedSearchObjectWithWeight(it.next(), s, speedSearch);
      if (o.weight != Integer.MIN_VALUE) {
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
