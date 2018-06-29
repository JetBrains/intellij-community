// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBComboBoxLabel;
import com.intellij.util.SmartList;

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
  private String myDefaultItem;

  public LinkComboBox(String defaultItem) {
    setDefaultItem(defaultItem);
    setForeground(JBColor.link());
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (isEnabled()) {
          final BaseListPopupStep<String> list = new BaseListPopupStep<String>(null, myItems) {
            @Override
            public PopupStep onChosen(String selectedValue, boolean finalChoice) {
              setSelectedItem(selectedValue);
              return super.onChosen(selectedValue, finalChoice);
            }

            @Override
            public int getDefaultOptionIndex() {
              return myItems.indexOf(mySelectedItem);
            }
          };
          final ListPopup popup = JBPopupFactory.getInstance().createListPopup(list);
          popup.show(new RelativePoint(LinkComboBox.this, new Point(-2, 0)));
        }
      }
    });
  }

  public void setItems(Collection<String> items) {
    myItems.clear();
    myItems.addAll(items);
    if (!myItems.contains(mySelectedItem)) {
      if (myDefaultItem != null) {
        setSelectedItem(myDefaultItem);
      }
      else if (!items.isEmpty()) {
        setSelectedItem(myItems.get(0));
      }
      else {
        setText("<empty>");
      }
    }
  }

  public String getSelectedItem() {
    return mySelectedItem;
  }

  public void setSelectedItem(String selectedItem) {
    if (!myItems.contains(selectedItem)) {
      throw new IllegalStateException();
    }
    mySelectedItem = selectedItem;
    setText(selectedItem);
  }

  public void setDefaultItem(String defaultItem) {
    myDefaultItem = defaultItem;
    setText(defaultItem);
  }
}
