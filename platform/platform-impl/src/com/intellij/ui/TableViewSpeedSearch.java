// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  protected @Nullable String getElementText(Object element) {
    return getItemText((Item)element);
  }

  protected abstract @Nullable String getItemText(final @NotNull Item element);

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

