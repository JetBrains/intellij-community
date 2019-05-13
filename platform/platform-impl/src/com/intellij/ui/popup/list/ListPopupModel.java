// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.list;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListPopupModel extends AbstractListModel {

  private final List<Object> myOriginalList;
  private final List<Object> myFilteredList = new ArrayList<>();

  private final ElementFilter myFilter;
  private final ListPopupStep myStep;

  private int myFullMatchIndex = -1;
  private int myStartsWithIndex = -1;
  private final SpeedSearch mySpeedSearch;
  private final Map<Object, ListSeparator> mySeparators = new HashMap<>();

  public ListPopupModel(ElementFilter filter, SpeedSearch speedSearch, ListPopupStep step) {
    myFilter = filter;
    myStep = step;
    mySpeedSearch = speedSearch;
    myOriginalList = new ArrayList<>(step.getValues());
    rebuildLists();
  }

  public void deleteItem(final Object item) {
    final int i = myOriginalList.indexOf(item);
    if (i >= 0) {
      myOriginalList.remove(i);
      rebuildLists();
      fireContentsChanged(this, 0, myFilteredList.size());
    }
  }

  @Nullable
  public Object get(final int i) {
    if (i >= 0 && i < myFilteredList.size()) {
      return myFilteredList.get(i);
    }

    return null;
  }

  private void rebuildLists() {
    myFilteredList.clear();
    mySeparators.clear();
    myFullMatchIndex = -1;
    myStartsWithIndex = -1;

    ListSeparator lastSeparator = null;
    for (Object each : myOriginalList) {
      lastSeparator = ObjectUtils.chooseNotNull(myStep.getSeparatorAbove(each), lastSeparator);

      if (myFilter.shouldBeShowing(each)) {
        addToFiltered(each);
        if (lastSeparator != null) {
          mySeparators.put(each, lastSeparator);
          lastSeparator = null;
        }
      }
    }
  }

  private void addToFiltered(Object each) {
    myFilteredList.add(each);
    String filterString = StringUtil.toUpperCase(mySpeedSearch.getFilter());
    String candidateString = StringUtil.toUpperCase(myStep.getTextFor(each));
    int index = myFilteredList.size() - 1;

    if (myFullMatchIndex == -1 && filterString.equals(candidateString)) {
      myFullMatchIndex = index;
    }

    if (myStartsWithIndex == -1 && candidateString.startsWith(filterString)) {
      myStartsWithIndex = index;
    }
  }

  @Override
  public int getSize() {
    return myFilteredList.size();
  }

  @Override
  public Object getElementAt(int index) {
    if (index >= myFilteredList.size()) {
      return null;
    }
    return myFilteredList.get(index);
  }

  public boolean isSeparatorAboveOf(Object aValue) {
    return getSeparatorAbove(aValue) != null;
  }

  public String getCaptionAboveOf(Object value) {
    ListSeparator separator = getSeparatorAbove(value);
    if (separator != null) {
      return separator.getText();
    }
    return "";
  }

  private ListSeparator getSeparatorAbove(Object value) {
    return mySeparators.get(value);
  }

  public void refilter() {
    rebuildLists();
    if (myFilteredList.isEmpty() && !myOriginalList.isEmpty()) {
      mySpeedSearch.noHits();
    }
    fireContentsChanged(this, 0, myFilteredList.size());
  }

  public boolean isVisible(Object object) {
    return myFilteredList.contains(object);
  }

  public int getClosestMatchIndex() {
    return myFullMatchIndex != -1 ? myFullMatchIndex : myStartsWithIndex;
  }

  public void updateOriginalList() {
    myOriginalList.clear();
    myOriginalList.addAll(myStep.getValues());
    refilter();
  }
}
