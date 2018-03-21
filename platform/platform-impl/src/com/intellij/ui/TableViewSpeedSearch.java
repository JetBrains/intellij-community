/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class TableViewSpeedSearch<Item> extends SpeedSearchBase<TableView<Item>> {
  public TableViewSpeedSearch(TableView<Item> component) {
    super(component);
    setComparator(new SpeedSearchComparator(false));
  }

  @Override
  protected int getSelectedIndex() {
    return getComponent().getSelectedRow();
  }

  @Override
  protected int convertIndexToModel(int viewIndex) {
    return myComponent.convertRowIndexToModel(viewIndex);
  }

  @NotNull
  @Override
  protected Object[] getAllElements() {
    return getComponent().getItems().toArray();
  }

  @Nullable
  @Override
  protected String getElementText(Object element) {
    return getItemText((Item)element);
  }

  @Nullable
  protected abstract String getItemText(final @NotNull Item element);

  @Override
  protected void selectElement(final Object element, final String selectedText) {
    final List items = getComponent().getItems();
    for (int i = 0, itemsSize = items.size(); i < itemsSize; i++) {
      final Object o = items.get(i);
      if (o == element) {
        final int viewIndex = myComponent.convertRowIndexToView(i);
        getComponent().getSelectionModel().setSelectionInterval(viewIndex, viewIndex);
        TableUtil.scrollSelectionToVisible(getComponent());
        break;
      }
    }
  }
}

