// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import junit.framework.TestCase;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.Comparator;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

public class SortedListModelTest extends TestCase {
  private final SortedListModel<String> myModel = new SortedListModel<>(Comparator.naturalOrder());
  public void testAdding() {
    myModel.setAll(new String[]{"5", "0", "9"});
    assertThat(myModel.getItems()).containsExactly("0", "5", "9");
    assertEquals(1, myModel.add("3"));
    assertEquals(1, myModel.add("3"));
    assertThat(myModel.addAll(new String[]{"7", "4", "4", "1"})).containsExactly(7, 5, 4, 1);
    assertThat(myModel.getItems()).containsExactly("0", "1", "3", "3", "4", "4", "5", "7", "9");
  }

  public void testAccessingViaIterator() {
    myModel.setAll(new String[]{"1", "2", "3", "4"});
    assertThat(ContainerUtil.collect(myModel.iterator())).isEqualTo(myModel.getItems());
  }

  public void testRemoveViaIterator() {
    myModel.setAll(new String[]{"1", "2", "3", "4"});
    Iterator iterator = myModel.iterator();
    final IntList removed = new IntArrayList();
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
    assertEquals(0, removed.getInt(0));
    while (iterator.hasNext()) iterator.next();
    assertFalse(iterator.hasNext());
    iterator.remove();
    assertEquals(2, removed.size());
    assertEquals(2, removed.getInt(1));
    assertThat(myModel.getItems()).containsExactly("2", "3");
  }

}
