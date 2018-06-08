// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ui.JBListUpdater;
import com.intellij.openapi.ui.ListComponentUpdater;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
class PopupListAdapter<T> implements PopupChooserBuilder.PopupComponentAdapter<T> {
  private final JList myList;
  private PopupChooserBuilder myBuilder;
  private ListWithFilter myListWithFilter;

  public PopupListAdapter(PopupChooserBuilder builder, JList list) {
    myBuilder = builder;
    myList = list;
  }

  @Override
  public JComponent getComponent() {
    return myList;
  }

  @Override
  public void setRenderer(ListCellRenderer renderer) {
    myList.setCellRenderer(renderer);
  }

  @Override
  public void setItemChosenCallback(Consumer<T> callback) {
    myBuilder.setItemChoosenCallback(() -> {
      Object selectedValue = myList.getSelectedValue();
      if (selectedValue != null) {
        callback.consume((T)selectedValue);
      }
    });
  }

  @Override
  public void setItemsChosenCallback(Consumer<Set<T>> callback) {
    myBuilder.setItemChoosenCallback(() -> {
      List list = myList.getSelectedValuesList();
      callback.consume(list != null ? ContainerUtil.newHashSet(list) : Collections.emptySet());
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
  public BooleanFunction<KeyEvent> getKeyEventHandler() {
    return InputEvent::isConsumed;
  }

  @Override
  public JComponent buildFinalComponent() {
    myListWithFilter = (ListWithFilter)ListWithFilter.wrap(myList, new MyListWrapper(myList), myBuilder.getItemsNamer());
    myListWithFilter.setAutoPackHeight(myBuilder.isAutoPackHeightOnFiltering());
    return myListWithFilter;
  }

  @Override
  public void addMouseListener(MouseListener listener) {
    myList.addMouseListener(listener);
  }

  @Override
  public void autoSelect() {
    JList list = myList;
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
  public void setItemSelectedCallback(Consumer<T> c) {
    myList.addListSelectionListener(e -> {
      Object selectedValue = myList.getSelectedValue();
      c.consume((T)selectedValue);
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

  private class MyListWrapper extends JBScrollPane implements DataProvider {
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private final JList myList;

    private MyListWrapper(final JList list) {
      super(UIUtil.isUnderAquaLookAndFeel() ? 0 : -1);
      list.setVisibleRowCount(myBuilder.getVisibleRowCount());
      setViewportView(list);


      if (myBuilder.isAutoselectOnMouseMove()) {
        ListUtil.installAutoSelectOnMouseMove(list);
      }

      ScrollingUtil.installActions(list);

      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
      myList = list;
    }

    @Nullable
    public Object getData(@NonNls String dataId) {
      if (PlatformDataKeys.SELECTED_ITEM.is(dataId)){
        return myList.getSelectedValue();
      }
      if (PlatformDataKeys.SELECTED_ITEMS.is(dataId)){
        return myList.getSelectedValues();
      }
      return null;
    }

    public void setBorder(Border border) {
      if (myList != null){
        myList.setBorder(border);
      }
    }

    public void requestFocus() {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));
    }

    public synchronized void addMouseListener(MouseListener l) {
      myList.addMouseListener(l);
    }
  }
}
