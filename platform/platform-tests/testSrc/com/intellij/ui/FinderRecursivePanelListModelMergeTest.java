/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.ui.components.JBList;

import java.util.Arrays;

public class FinderRecursivePanelListModelMergeTest extends UsefulTestCase {

  public void testListModelMerge1() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 0, 0, "a");
  }

  public void testListModelMerge2() {
    assertMerge(new String[0], "a", "b", "c", "d");
  }

  public void testListModelMerge3() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 0, -1 /* nothing */);
  }

  public void testListModelMerge4() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 2, 2, "a", "b", "c", "d");
  }

  public void _testListModelMerge5() {
    assertMerge(new String[]{"a", "b", "c", "d"}, "d", "c", "b", "a");
  }

  public void testListModelMerge6() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 2, 1, "b", "c");
  }

  public void testListModelMerge7() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 1, 0, "b", "c", "d", "e");
  }

  public void _testListModelMerge8() {
    assertMerge(new String[]{"a", "a", "b", "b"}, "b", "a", "b", "a");
  }

  public void testListModelMerge9() {
    assertMerge(new String[]{"a", "b"}, 1, 3, "e", "d", "a", "b");
  }

  public void testListModelMerge10() {
    assertMerge(new String[]{"a", "b"}, 0, -1, "c", "d");
  }

  private static void assertMerge(String[] initialItems,
                                  int initialSelectionIdx,
                                  int selectionIndexAfterMerge,
                                  String... itemsToMerge) {
    CollectionListModel<String> model = new CollectionListModel<String>();
    model.add(Arrays.asList(initialItems));
    JBList list = new JBList(model);
    list.setSelectedIndex(initialSelectionIdx);

    FinderRecursivePanel.mergeListItems(model, Arrays.asList(itemsToMerge));
    assertEquals(itemsToMerge.length, model.getSize());
    for (int i = 0; i < itemsToMerge.length; i++) {
      assertEquals("idx:" + i + " " + toString(model.getItems(), ","), itemsToMerge[i], model.getElementAt(i));
    }
    assertEquals(toString(model.getItems(), ","), selectionIndexAfterMerge, list.getSelectedIndex());
  }

  private static void assertMerge(String[] items, String... newItems) {
    assertMerge(items, -1, -1, newItems);
  }
}