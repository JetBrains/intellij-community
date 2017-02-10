/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;

public class ListSpeedSearch extends SpeedSearchBase<JList> {
  private final Convertor<Object, String> myToStringConvertor;

  public ListSpeedSearch(JList list) {
    this(list, (Convertor<Object, String>)null);
  }

  public ListSpeedSearch(final JList list, @NotNull Function<Object, String> convertor) {
    this(list, (Convertor<Object, String>)convertor::fun);
  }

  public ListSpeedSearch(final JList list, @Nullable Convertor<Object, String> convertor) {
    super(list);
    myToStringConvertor = convertor;

    new MySelectAllAction(list, this).registerCustomShortcutSet(list, null);
  }

  protected void selectElement(Object element, String selectedText) {
    ScrollingUtil.selectItem(myComponent, element);
  }

  protected int getSelectedIndex() {
    return myComponent.getSelectedIndex();
  }

  protected Object[] getAllElements() {
    return getAllListElements(myComponent);
  }

  public static Object[] getAllListElements(final JList list) {
    ListModel model = list.getModel();
    if (model instanceof DefaultListModel){ // optimization
      return ((DefaultListModel)model).toArray();
    }
    else{
      Object[] elements = new Object[model.getSize()];
      for(int i = 0; i < elements.length; i++){
        elements[i] = model.getElementAt(i);
      }
      return elements;
    }
  }

  protected String getElementText(Object element) {
    if (myToStringConvertor != null) {
      return myToStringConvertor.convert(element);
    }
    return element == null ? null : element.toString();
  }

  @NotNull
  private TIntArrayList findAllFilteredElements(String s) {
    TIntArrayList indices = new TIntArrayList();
    String _s = s.trim();

    Object[] elements = getAllListElements(myComponent);
    for (int i = 0; i < elements.length; i++) {
      final Object element = elements[i];
      if (isMatchingElement(element, _s)) indices.add(i);
    }
    return indices;
  }

  private static class MySelectAllAction extends DumbAwareAction {
    @NotNull private final JList myList;
    @NotNull private final ListSpeedSearch mySearch;

    public MySelectAllAction(@NotNull JList list, @NotNull ListSpeedSearch search) {
      myList = list;
      mySearch = search;
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_SELECT_ALL));
      setEnabledInModalContext(true);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(mySearch.isPopupActive() &&
                                     myList.getSelectionModel().getSelectionMode() == MULTIPLE_INTERVAL_SELECTION);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
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