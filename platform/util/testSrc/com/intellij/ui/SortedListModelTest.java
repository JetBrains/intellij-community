/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.Assertion;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntArrayList;
import junit.framework.TestCase;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.Comparator;
import java.util.Iterator;

public class SortedListModelTest extends TestCase {
  private final SortedListModel myModel = new SortedListModel(new Comparator() {
    @Override
    public int compare(final Object o1, final Object o2) {
      return ((Comparable) o1).compareTo((Comparable) o2);
    }
  }
  );
  private final Assertion CHECK = new Assertion();

  public void testAdding() {
    myModel.setAll(new String[]{"5", "0", "9"});
    CHECK.compareAll(new String[]{"0", "5", "9"}, myModel.getItems());
    assertEquals(1, myModel.add("3"));
    assertEquals(1, myModel.add("3"));
    CHECK.compareAll(new int[]{7, 5, 4, 1}, myModel.addAll(new String[]{"7", "4", "4", "1"}));
    CHECK.compareAll(new String[]{"0", "1", "3", "3", "4", "4", "5", "7", "9"}, myModel.getItems());
  }

  public void testAccessingViaIterator() {
    myModel.setAll(new String[]{"1", "2", "3", "4"});
    CHECK.compareAll(myModel.getItems(), ContainerUtil.collect(myModel.iterator()));
  }

  public void testRemoveViaIterator() {
    myModel.setAll(new String[]{"1", "2", "3", "4"});
    Iterator iterator = myModel.iterator();
    final TIntArrayList removed = new TIntArrayList();
    myModel.addListDataListener(new ListDataListener() {
      @Override
      public void contentsChanged(ListDataEvent e) {
        throw new RuntimeException();
      }

      @Override
      public void intervalAdded(ListDataEvent e) {
        throw new RuntimeException();
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        assertEquals(e.getIndex0(), e.getIndex1());
        removed.add(e.getIndex0());
      }
    });
    iterator.next();
    iterator.remove();
    assertEquals(1, removed.size());
    assertEquals(0, removed.get(0));
    while (iterator.hasNext()) iterator.next();
    assertFalse(iterator.hasNext());
    iterator.remove();
    assertEquals(2, removed.size());
    assertEquals(2, removed.get(1));
    CHECK.compareAll(new String[]{"2", "3"}, myModel.getItems());
  }

}
