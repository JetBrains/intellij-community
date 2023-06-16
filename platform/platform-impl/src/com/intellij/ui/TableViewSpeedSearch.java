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
public abstract class TableViewSpeedSearch<Item> extends TableSpeedSearchBase<TableView<Item>> {
  /**
   * @param sig parameter is used to avoid clash with the deprecated constructor
   */
  protected TableViewSpeedSearch(TableView<Item> component, Void sig) {
    super(component, sig);
    setComparator(new SpeedSearchComparator(false));
  }

  /**
   * @deprecated For inheritance use the non-deprecated constructor.
   * <p>
   * Also, note that non-deprecated constructor is side effect free, and you should call for {@link SpeedSearchBase#setupListeners()}
   * method to enable speed search
   */
  public TableViewSpeedSearch(TableView<Item> component) {
    super(component);
    setComparator(new SpeedSearchComparator(false));
  }

  @Override
  protected int getSelectedIndex() {
    return getComponent().getSelectedRow();
  }

  @Override
  protected int getElementCount() {
    // if filtering is enabled rowCount != itemsSize
    return getComponent().getRowCount();
  }

  @Override
  protected Object getElementAt(int viewIndex) {
    return getComponent().getItems().get(myComponent.convertRowIndexToModel(viewIndex));
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
    final List<Item> items = getComponent().getItems();
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

  @Override
  protected boolean isMatchingRow(int modelRow, String pattern) {
    return isMatchingElement(getComponent().getItems().get(modelRow), pattern);
  }
}

