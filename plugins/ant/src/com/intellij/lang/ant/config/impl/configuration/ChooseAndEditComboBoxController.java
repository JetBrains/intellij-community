/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.ui.SortedComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

public abstract class ChooseAndEditComboBoxController<Item, Ref> {
  private final ComboboxWithBrowseButton myCombobox;
  private final Convertor<Item, Ref> myToString;
  private final Map<Ref, Item> myItems = new HashMap<>();

  public ChooseAndEditComboBoxController(ComboboxWithBrowseButton combobox,
                                         Convertor<Item, Ref> toRef,
                                         Comparator<Ref> comparator) {
    myCombobox = combobox;
    myToString = toRef;
    myCombobox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        resetList(openConfigureDialog(myItems.get(getSelectedString()), getCombobox()));
      }
    });
    getCombobox().setModel(new SortedComboBoxModel<>(comparator));
  }

  public void resetList(Item selection) {
    Ref selectedItem = getSelectedString();
    myItems.clear();
    myItems.putAll(ContainerUtil.newMapFromValues(getAllListItems(), myToString));
    SortedComboBoxModel<Ref> model = getModel();
    model.setAll(myItems.keySet());
    if (selection != null) model.setSelectedItem(myToString.convert(selection));
    else model.setSelectedItem(selectedItem);
  }

  protected abstract Iterator<Item> getAllListItems();
  protected abstract Item openConfigureDialog(Item item, JComponent parent);

  private Ref getSelectedString() {
    return (Ref)getCombobox().getSelectedItem();
  }

  private JComboBox getCombobox() {
    return myCombobox.getComboBox();
  }

  private SortedComboBoxModel<Ref> getModel() {
    return ((SortedComboBoxModel<Ref>)getCombobox().getModel());
  }

  public void setRenderer(ListCellRenderer renderer) {
    myCombobox.getComboBox().setRenderer(renderer);
  }

  public Ref getSelectedItem() {
    return (Ref)myCombobox.getComboBox().getSelectedItem();
  }
}
