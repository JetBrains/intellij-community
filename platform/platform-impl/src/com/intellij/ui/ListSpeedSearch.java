// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

public class ListSpeedSearch<T> extends SpeedSearchBase<JList<T>> {
  @Nullable private final Function<? super T, String> myToStringConvertor;

  public ListSpeedSearch(@NotNull JList<T> list) {
    super(list);
    myToStringConvertor = null;
    registerSelectAll(list);
  }

  public ListSpeedSearch(@NotNull JList<T> list, @NotNull Function<? super T, String> convertor) {
    super(list);
    myToStringConvertor = convertor;
    registerSelectAll(list);
  }

  private void registerSelectAll(@NotNull JList<T> list) {
    new MySelectAllAction<>(list, this).registerCustomShortcutSet(list, null);
  }

  @Override
  protected void selectElement(Object element, String selectedText) {
    if (element != null) {
      ScrollingUtil.selectItem(myComponent, (T)element);
    }
    else {
      myComponent.clearSelection();
    }
  }

  @Override
  protected int getSelectedIndex() {
    return myComponent.getSelectedIndex();
  }

  @Override
  protected Object @NotNull [] getAllElements() {
    return getAllListElements(myComponent);
  }

  public static <T> Object[] getAllListElements(final @NotNull JList<T> list) {
    ListModel<T> model = list.getModel();
    if (model instanceof DefaultListModel){ // optimization
      return ((DefaultListModel<T>)model).toArray();
    }
    Object[] elements = new Object[model.getSize()];
    for(int i = 0; i < elements.length; i++){
      elements[i] = model.getElementAt(i);
    }
    return elements;
  }

  @Override
  protected String getElementText(Object element) {
    if (myToStringConvertor != null) {
      //noinspection unchecked
      return myToStringConvertor.fun((T)element);
    }
    return element == null ? null : element.toString();
  }

  @NotNull
  private TIntArrayList findAllFilteredElements(@NotNull String s) {
    TIntArrayList indices = new TIntArrayList();
    String _s = s.trim();

    Object[] elements = getAllListElements(myComponent);
    for (int i = 0; i < elements.length; i++) {
      final Object element = elements[i];
      if (isMatchingElement(element, _s)) indices.add(i);
    }
    return indices;
  }

  private static final class MySelectAllAction<T> extends DumbAwareAction {
    @NotNull private final JList<T> myList;
    @NotNull private final ListSpeedSearch<T> mySearch;

    MySelectAllAction(@NotNull JList<T> list, @NotNull ListSpeedSearch<T> search) {
      myList = list;
      mySearch = search;
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_SELECT_ALL);
      if (action != null) {
        copyShortcutFrom(action);
      }
      setEnabledInModalContext(true);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(mySearch.isPopupActive() &&
                                     myList.getSelectionModel().getSelectionMode() == ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ListSelectionModel sm = myList.getSelectionModel();

      String query = mySearch.getEnteredPrefix();
      if (query == null) return;

      TIntArrayList filtered = mySearch.findAllFilteredElements(query);
      if (filtered.isEmpty()) return;

      boolean alreadySelected = Arrays.equals(filtered.toNativeArray(), myList.getSelectedIndices());

      if (alreadySelected) {
        int anchor = myList.getAnchorSelectionIndex();

        myList.setSelectedIndex(anchor);
        sm.setAnchorSelectionIndex(anchor);

        mySearch.findAndSelectElement(query);
      }
      else {
        int anchor = -1;
        Object currentElement = mySearch.findElement(query);
        if (currentElement != null) {
          List<Object> elements = Arrays.asList(getAllListElements(myList));
          anchor = ContainerUtil.indexOfIdentity(elements, currentElement);
        }
        if (anchor == -1) anchor = filtered.get(0);

        myList.setSelectedIndices(filtered.toNativeArray());
        sm.setAnchorSelectionIndex(anchor);
      }
    }
  }
}