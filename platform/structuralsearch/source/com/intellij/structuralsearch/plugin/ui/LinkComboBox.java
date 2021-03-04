// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBComboBoxLabel;
import com.intellij.util.NullableConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
class LinkComboBox extends JBComboBoxLabel {

  private final List<String> myItems = new SmartList<>();
  private String mySelectedItem;
  private @NlsContexts.Label String myDefaultItem;
  private NullableConsumer<? super String> myConsumer;

  LinkComboBox(@NlsContexts.Label String defaultItem) {
    setDefaultItem(defaultItem);
    setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        showPopup();
      }
    });
  }

  public void setItemConsumer(@Nullable NullableConsumer<? super String> consumer) {
    myConsumer = consumer;
  }

  public void setItems(@NotNull Collection<String> items) {
    if (items.isEmpty()) throw new IllegalArgumentException("items needs to contain at least one item");
    myItems.clear();
    myItems.addAll(items);
    if (!myItems.contains(mySelectedItem)) {
      setSelectedItem(myDefaultItem != null ? myDefaultItem : myItems.get(0));
    }
  }

  public String getSelectedItem() {
    return mySelectedItem;
  }

  public void setSelectedItem(@NlsContexts.Label String selectedItem) {
    if (!myItems.contains(selectedItem)) throw new IllegalArgumentException("selected item is not contained in items");
    mySelectedItem = selectedItem;
    setText(selectedItem);
  }

  public void setDefaultItem(@NlsContexts.Label String defaultItem) {
    myDefaultItem = defaultItem;
    setText(defaultItem);
  }

  void showPopup() {
    if (!isEnabled()) return;
    final BaseListPopupStep<String> list = new BaseListPopupStep<>(null, myItems) {
      @Override
      public PopupStep<?> onChosen(@NlsContexts.Label String selectedValue, boolean finalChoice) {
        setSelectedItem(selectedValue);
        if (myConsumer != null) myConsumer.consume(selectedValue);
        return super.onChosen(selectedValue, finalChoice);
      }

      @Override
      public int getDefaultOptionIndex() {
        return myItems.indexOf(mySelectedItem);
      }
    };
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(list);
    popup.show(new RelativePoint(this, new Point(-2, 0)));
  }
}
