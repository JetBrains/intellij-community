// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.SkipInHeadlessEnvironment;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.Arrays;
import java.util.List;

@SkipInHeadlessEnvironment
public class FinderRecursivePanelListModelMergeTest extends LightPlatformTestCase {

  public void testSelectionKeptSingleItem() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 0, 0, "a");
  }

  public void testNoSelectionAfterReplacingAllItems() {
    assertMerge(ArrayUtilRt.EMPTY_STRING_ARRAY, "a", "b", "c", "d");
  }

  public void testNoSelectionNoItems() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 0, -1 /* nothing */);
  }

  public void testSelectionKeptIdenticalItems() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 2, 2, "a", "b", "c", "d");
  }

  public void testListModelMerge5() {
    assertMerge(new String[]{"a", "b", "c", "d"}, "d", "c", "b", "a");
  }

  public void testListModelMerge5_1() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 0, 3, "d", "c", "b", "a");
  }

  public void testListModelMerge5_2() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 3, 0, "d", "c", "b", "a");
  }

  public void testSelectionItemKeptItemsLessAndMovedUp() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 2, 1, "b", "c");
  }

  public void testSelectionItemKeptItemsMovedUp() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 1, 0, "b", "c", "d", "e");
  }

  public void testListModelMerge8() {
    assertMerge(new String[]{"a", "a", "b", "b"}, "b", "a", "b", "a");
  }

  public void testSelectionItemKeptItemsMovedDown() {
    assertMerge(new String[]{"a", "b"}, 1, 3, "e", "d", "a", "b");
  }

  public void testNoSelectionDisjointItems() {
    assertMerge(new String[]{"a", "b"}, 0, -1, "c", "d");
  }

  private void assertMerge(String[] initialItems, String... itemsToMerge) {
    assertMerge(initialItems, -1, -1, itemsToMerge);
  }

  private void assertMerge(String[] initialItems,
                           int initialSelectionIdx,
                           int selectionIndexAfterMerge,
                           String... itemsToMerge) {

    final StringFinderRecursivePanel panel = createStringPanel(initialItems);
    disposeOnTearDown(panel);

    JBList<String> list = panel.getList();
    CollectionListModel<String> model = panel.getListModel();

    list.setSelectedIndex(initialSelectionIdx);

    ListSelectionListener selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (panel.isMergeListItemsRunning()) return;
        fail("selection changed");
      }
    };
    list.addListSelectionListener(selectionListener);

    panel.merge(model, list, Arrays.asList(itemsToMerge));
    assertEquals(itemsToMerge.length, model.getSize());
    for (int i = 0; i < itemsToMerge.length; i++) {
      assertEquals("idx:" + i + " " + toString(model.getItems(), ","), itemsToMerge[i], model.getElementAt(i));
    }
    assertEquals(toString(model.getItems(), ","), selectionIndexAfterMerge, list.getSelectedIndex());

    list.removeListSelectionListener(selectionListener);
  }

  @NotNull
  private StringFinderRecursivePanel createStringPanel(String[] initialItems) {
    StringFinderRecursivePanel panel = new StringFinderRecursivePanel(initialItems, getProject());
    panel.initPanel();
    return panel;
  }

  private static class StringFinderRecursivePanel extends FinderRecursivePanel<String> {
    private final String[] myInitialItems;

    StringFinderRecursivePanel(String[] initialItems, @NotNull Project project) {
      super(project, null);
      myInitialItems = initialItems;
    }

    @NotNull
    @Override
    protected List<String> getListItems() {
      return Arrays.asList(myInitialItems);
    }

    @NotNull
    @Override
    protected String getItemText(@NotNull String s) {
      return "";
    }

    @Override
    protected boolean hasChildren(@NotNull String s) {
      return false;
    }

    public JBList<String> getList() {
      return myList;
    }

    public CollectionListModel<String> getListModel() {
      return myListModel;
    }

    @Override
    protected JBList<String> createList() {
      myList = super.createList();
      ((CollectionListModel<String>)myList.getModel()).replaceAll(getListItems());
      return myList;
    }

    public void merge(@NotNull CollectionListModel<String> listModel, @NotNull JList<String> list, @NotNull List<String> newItems) {
      mergeListItems(listModel, list, newItems);
    }
  }
}