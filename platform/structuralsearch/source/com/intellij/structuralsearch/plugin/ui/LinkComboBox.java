// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.NullableConsumer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
class LinkComboBox extends ActionLink {

  private final List<String> myItems = new SmartList<>();
  private String mySelectedItem;
  private @NlsContexts.Label String myDefaultItem;
  private NullableConsumer<? super String> myConsumer;

  LinkComboBox(@NlsContexts.Label String defaultItem) {
    setAutoHideOnDisable(false);
    setDefaultItem(defaultItem);
    setDropDownLinkIcon();
    addActionListener(e -> showPopup());
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

  /**
   * Sets a label for this component.  If the specified label has a displayed mnemonic,
   * it will call the {@code #doClick} method when the mnemonic is activated.
   *
   * @param label the label referring to this component
   * @see JLabel#setLabelFor
   */
  public void setLabel(@NotNull JLabel label) {
    label.setLabelFor(this);

    label.getActionMap().put("release" /* BasicLabelUI.Actions.RELEASE */, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent event) {
        doClick();
      }
    });
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
    popup.show(new RelativePoint(this, new Point(JBUIScale.scale(-2), getHeight() + JBUIScale.scale(2))));
  }
}
