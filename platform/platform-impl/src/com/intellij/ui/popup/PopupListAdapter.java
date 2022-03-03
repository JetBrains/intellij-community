// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.JBListUpdater;
import com.intellij.openapi.ui.ListComponentUpdater;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.popup.util.PopupImplUtil;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

final class PopupListAdapter<T> implements PopupChooserBuilder.PopupComponentAdapter<T> {
  private final JList<T> myList;
  private final PopupChooserBuilder<T> myBuilder;
  private ListWithFilter<T> myListWithFilter;

  PopupListAdapter(PopupChooserBuilder<T> builder, JList<T> list) {
    myBuilder = builder;
    myList = list;
  }

  @Override
  public JComponent getComponent() {
    return myList;
  }

  @Override
  public void setRenderer(ListCellRenderer<? super T> renderer) {
    myList.setCellRenderer(renderer);
  }

  @Override
  public void setItemChosenCallback(Consumer<? super T> callback) {
    myBuilder.setItemChoosenCallback(() -> {
      T selectedValue = myList.getSelectedValue();
      if (selectedValue != null) {
        callback.consume(selectedValue);
      }
    });
  }

  @Override
  public void setItemsChosenCallback(Consumer<? super Set<T>> callback) {
    myBuilder.setItemChoosenCallback(() -> {
      List<T> list = myList.getSelectedValuesList();
      callback.consume(list != null ? new HashSet<>(list) : Collections.emptySet());
    });
  }

  @Override
  public JScrollPane createScrollPane() {
    return myListWithFilter.getScrollPane();
  }

  @Override
  public boolean hasOwnScrollPane() {
    return true;
  }

  @Nullable
  @Override
  public Predicate<KeyEvent> getKeyEventHandler() {
    return InputEvent::isConsumed;
  }

  @Override
  public JComponent buildFinalComponent() {
    myListWithFilter = (ListWithFilter<T>)ListWithFilter.wrap(myList, new MyListWrapper(myList), myBuilder.getItemsNamer());
    myListWithFilter.setAutoPackHeight(myBuilder.isAutoPackHeightOnFiltering());
    return myListWithFilter;
  }

  @Override
  public void addMouseListener(MouseListener listener) {
    myList.addMouseListener(listener);
  }

  @Override
  public void autoSelect() {
    JList<T> list = myList;
    if (list.getSelectedIndex() == -1) {
      list.setSelectedIndex(0);
    }
  }

  @Override
  public ListComponentUpdater getBackgroundUpdater() {
    return new JBListUpdater((JBList)(myList));
  }

  @Override
  public void setSelectedValue(T preselection, boolean shouldScroll) {
    myList.setSelectedValue(preselection, shouldScroll);
  }

  @Override
  public void setItemSelectedCallback(Consumer<? super T> c) {
    myList.addListSelectionListener(e -> {
      T selectedValue = myList.getSelectedValue();
      c.consume(selectedValue);
    });
  }

  @Override
  public void setSelectionMode(int selection) {
    myList.setSelectionMode(selection);
  }

  @Override
  public boolean checkResetFilter() {
    return myListWithFilter.resetFilter();
  }

  private final class MyListWrapper extends JBScrollPane implements DataProvider {
    private final JList<T> myList;

    private MyListWrapper(final JList<T> list) {
      super(-1);
      list.setVisibleRowCount(myBuilder.getVisibleRowCount());
      setViewportView(list);


      if (myBuilder.isAutoselectOnMouseMove()) {
        ListUtil.installAutoSelectOnMouseMove(list);
      }

      ScrollingUtil.installActions(list);

      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
      myList = list;
    }

    @Override
    @Nullable
    public Object getData(@NotNull @NonNls String dataId) {
      return PopupImplUtil.getDataImplForList(myList, dataId);
    }

    @Override
    public void setBorder(Border border) {
      if (myList != null) {
        myList.setBorder(border);
      }
    }

    @Override
    public void requestFocus() {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));
    }

    @Override
    public synchronized void addMouseListener(MouseListener l) {
      myList.addMouseListener(l);
    }
  }
}
