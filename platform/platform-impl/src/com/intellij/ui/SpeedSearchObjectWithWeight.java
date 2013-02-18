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
  private final int weight;

  SpeedSearchObjectWithWeight(Object element, int weight) {
    this.node = element;
    this.weight = weight;
  }

  public int compareWith(SpeedSearchObjectWithWeight obj) {
    return weight == obj.weight ? 0 : weight < obj.weight ? 1 : -1;
  }

  public static List<SpeedSearchObjectWithWeight> findElement(String pattern, SpeedSearchBase speedSearch) {
    List<SpeedSearchObjectWithWeight> elements = new ArrayList<SpeedSearchObjectWithWeight>();
    pattern = pattern.trim();
    //noinspection unchecked
    final ListIterator<Object> it = speedSearch.getElementIterator(0);
    while (it.hasNext()) {
      Object element = it.next();
      String text = speedSearch.getElementText(element);
      if (text != null) {
        elements.add(new SpeedSearchObjectWithWeight(element, speedSearch.getComparator().matchingDegree(pattern, text)));
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
