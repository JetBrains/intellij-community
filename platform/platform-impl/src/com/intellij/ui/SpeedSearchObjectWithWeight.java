// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
* @author Konstantin Bulenkov
*/
public final class SpeedSearchObjectWithWeight {
  public final Object node;
  private final int weight;

  SpeedSearchObjectWithWeight(Object element, int weight) {
    this.node = element;
    this.weight = weight;
  }

  public int compareWith(SpeedSearchObjectWithWeight obj) {
    return Integer.compare(obj.weight, weight);
  }

  public static List<SpeedSearchObjectWithWeight> findElement(String pattern, SpeedSearchBase<?> speedSearch) {
    List<SpeedSearchObjectWithWeight> elements = new ArrayList<>();
    pattern = pattern.trim();
    final ListIterator<Object> it = speedSearch.getElementIterator(0);
    while (it.hasNext()) {
      Object element = it.next();
      String text = speedSearch.getElementText(element);
      if (text != null) {
        elements.add(new SpeedSearchObjectWithWeight(element, speedSearch.getComparator().matchingDegree(pattern, text)));
      }
    }
    SpeedSearchObjectWithWeight cur = null;
    ArrayList<SpeedSearchObjectWithWeight> current = new ArrayList<>();
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
