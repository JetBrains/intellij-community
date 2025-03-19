// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.Function;
import com.intellij.util.containers.Convertor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.ListIterator;

/**
 * To install the speed search on a {@link JList} component,
 * use {@link TreeUIHelper#installListSpeedSearch} or one of the {@link ListSpeedSearch#installOn} static methods
 */
public class ListSpeedSearch<T> extends SpeedSearchBase<JList<T>> {
  private final @Nullable Function<? super T, String> myToStringConvertor;

  protected ListSpeedSearch(@NotNull JList<T> list, Void sig, @Nullable Function<? super T, String> convertor) {
    super(list, sig);
    myToStringConvertor = convertor;
  }

  /**
   * Prefer {@link TreeUIHelper#installListSpeedSearch(JList)} as it located in the API module
   */
  public static <T> @NotNull ListSpeedSearch<T> installOn(@NotNull JList<T> list) {
    return installOn(list, null);
  }

  /**
   * Prefer {@link TreeUIHelper#installListSpeedSearch(JList, Convertor)} as it located in the API module
   */
  public static <T> @NotNull ListSpeedSearch<T> installOn(@NotNull JList<T> list, @Nullable Function<? super T, String> convertor) {
    ListSpeedSearch<T> search = new ListSpeedSearch<>(list, null, convertor);
    search.setupListeners();
    return search;
  }

  /**
   * @deprecated Use {@link TreeUIHelper#installListSpeedSearch(JList)}
   * or the static method {@link ListSpeedSearch#installOn(JList)} to install a speed search on list.
   * The {@link TreeUIHelper#installListSpeedSearch(JList)} is preferable over the static call as it located in the API module
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link ListSpeedSearch#setupListeners()}
   * method to enable speed search
   */
  @Deprecated
  public ListSpeedSearch(@NotNull JList<T> list) {
    super(list);
    myToStringConvertor = null;
    registerSelectAll(list);
  }

  /**
   * @deprecated Use {@link TreeUIHelper#installListSpeedSearch(JList, Convertor)}
   * or the static method {@link ListSpeedSearch#installOn(JList, Function)} to install a speed search on list.
   * The {@link TreeUIHelper#installListSpeedSearch(JList, Convertor)} is preferable over the static call as it located in the API module
   * <p>
   * For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link ListSpeedSearch#setupListeners()}
   * method to enable speed search
   */
  @Deprecated
  public ListSpeedSearch(@NotNull JList<T> list, @NotNull Function<? super T, String> convertor) {
    super(list);
    myToStringConvertor = convertor;
    registerSelectAll(list);
  }

  @Override
  public void setupListeners() {
    super.setupListeners();

    registerSelectAll(myComponent);
  }

  private void registerSelectAll(@NotNull JList<T> list) {
    new MySelectAllAction<>(list, this).registerCustomShortcutSet(list, null);
  }

  @Override
  protected void selectElement(Object element, String selectedText) {
    if (element != null) {
      //noinspection unchecked
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
  protected int getElementCount() {
    return myComponent.getModel().getSize();
  }

  @Override
  protected Object getElementAt(int viewIndex) {
    return myComponent.getModel().getElementAt(viewIndex);
  }

  @Override
  protected String getElementText(Object element) {
    if (myToStringConvertor != null) {
      //noinspection unchecked
      return myToStringConvertor.fun((T)element);
    }
    return element == null ? null : element.toString();
  }

  private @NotNull IntList findAllFilteredElements(@NotNull String s) {
    IntList indices = new IntArrayList();
    String trimmed = s.trim();

    ListIterator<Object> iterator = getElementIterator(0);
    while (iterator.hasNext()) {
      Object element = iterator.next();
      if (isMatchingElement(element, trimmed)) {
        indices.add(iterator.previousIndex());
      }
    }
    return indices;
  }

  private static final class MySelectAllAction<T> extends DumbAwareAction {
    private final @NotNull JList<T> myList;
    private final @NotNull ListSpeedSearch<T> mySearch;

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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ListSelectionModel sm = myList.getSelectionModel();

      String query = mySearch.getEnteredPrefix();
      if (query == null) return;

      IntList filtered = mySearch.findAllFilteredElements(query);
      if (filtered.isEmpty()) {
        return;
      }

      boolean alreadySelected = Arrays.equals(filtered.toIntArray(), myList.getSelectedIndices());

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
          ListIterator<Object> iterator = mySearch.getElementIterator(0);
          while (iterator.hasNext()) {
            if (iterator.next() == currentElement) {
              anchor = iterator.previousIndex();
              break;
            }
          }
        }
        if (anchor == -1) anchor = filtered.getInt(0);

        myList.setSelectedIndices(filtered.toIntArray());
        sm.setAnchorSelectionIndex(anchor);
      }
    }
  }
}