package com.intellij.ui;

import com.intellij.ui.table.TableView;

import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class TableViewSpeedSearch extends SpeedSearchBase<TableView> {
  public TableViewSpeedSearch(TableView component) {
    super(component);
    setComparator(new SpeedSearchComparator(false));
  }

  @Override
  protected int getSelectedIndex() {
    return getComponent().getSelectedRow();
  }

  @Override
  protected Object[] getAllElements() {
    return getComponent().getItems().toArray();
  }

  @Override
  protected abstract String getElementText(final Object element);

  @Override
  protected void selectElement(final Object element, final String selectedText) {
    final List items = getComponent().getItems();
    for (int i = 0, itemsSize = items.size(); i < itemsSize; i++) {
      final Object o = items.get(i);
      if (o == element) {
        getComponent().getSelectionModel().setSelectionInterval(i, i);
        TableUtil.scrollSelectionToVisible(getComponent());
        break;
      }
    }
  }
}

