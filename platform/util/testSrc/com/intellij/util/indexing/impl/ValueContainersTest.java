// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.containers.ChangeBufferingList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import junit.framework.TestCase;

import java.util.HashMap;

public class ValueContainersTest extends TestCase {
  public void testNullValueSingleId() {
    runSimpleAddRemoveIteration(new Void[] {null}, new int[][]{{5}});
  }
  
  public void testNullValueSameIdTrice() {
    runSimpleAddRemoveIteration(new Void[] {null}, new int[][]{{5, 5, 5}});
  }

  public void testOneValueManyId() {
    runSimpleAddRemoveIteration(new String[] {"value"}, new int[][]{{2, 3, 4}});
  }

  public void testManyValuesManyId() {
    runSimpleAddRemoveIteration(new Integer[] { 25, 33, 77}, new int[][]{{ 10, 20, 30}, { 11, 22 }, {44}});
  }

  private static <T> void runSimpleAddRemoveIteration(T[] values, int[][] inputIds) {
    HashMap<T, TIntArrayList> valueToIdList = new HashMap<>();
    ValueContainerImpl<T> container = new ValueContainerImpl<>();

    for(int i = 0; i < values.length; ++i) {
      TIntArrayList list = new TIntArrayList(inputIds.length);
      TIntHashSet set = new TIntHashSet(inputIds.length);
      T value = values[i];
      
      for (int inputId : inputIds[i]) {
        container.addValue(inputId, value);
        if (set.add(inputId)) list.add(inputId);
      }
      list.sort();
      valueToIdList.put(value, list);
    }

    InvertedIndexValueIterator<T> valueIterator = container.getValueIterator();

    //noinspection unused
    for (T unusedValue : values) {
      assertTrue(valueIterator.hasNext());

      T value = valueIterator.next();
      TIntArrayList list = valueToIdList.get(value);
      assertTrue(list != null);

      Object object = valueIterator.getFileSetObject();

      if (list.size() == 1) {
        assertEquals(new Integer(list.getQuick(0)), object);
      }
      else {
        assertTrue(object instanceof ChangeBufferingList);
      }

      ValueContainer.IntPredicate predicate = valueIterator.getValueAssociationPredicate();

      for (int inputId : list.toNativeArray()) assertTrue(predicate.contains(inputId));

      ValueContainer.IntIterator iterator = valueIterator.getInputIdsIterator();
      assertEquals(list.size(), iterator.size());

      for (int ignore : list.toNativeArray()) {
        assertTrue(iterator.hasNext());
        assertTrue(list.indexOf(iterator.next()) != -1);
      }

      assertTrue(!iterator.hasNext());
    }

    assertFalse(valueIterator.hasNext());
    
    valueToIdList.forEach((key, value) -> {
      for (int inputId : value.toNativeArray()) {
        container.removeAssociatedValue(inputId);
      }
    });
    
    assertEquals(0, container.size());
    valueIterator = container.getValueIterator();
    assertTrue(!valueIterator.hasNext());
  }
}
