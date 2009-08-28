package com.intellij.ui;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

public final class ListToolTipHandler extends AbstractToolTipHandler<Integer, JList>{

  protected ListToolTipHandler(JList list) {
    super(list);

    list.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          repaintHint();
        }
      }
    );

    list.getModel().addListDataListener(
      new ListDataListener() {
        public void intervalAdded(ListDataEvent e) {
          hideHint();
        }

        public void intervalRemoved(ListDataEvent e) {
          hideHint();
        }

        public void contentsChanged(ListDataEvent e) {
          hideHint();
        }
      }
    );
  }

  public static void install(JList list) {
    new ListToolTipHandler(list);
  }

  /**
   * @return java.lang.Integer which rpresent index of the row under the
   * specified point
   */
  protected Integer getCellKeyForPoint(Point point) {
    int rowIndex = myComponent.locationToIndex(point);
    return rowIndex != -1 ? new Integer(rowIndex) : null;
  }

  protected Rectangle getCellBounds(Integer key, Component rendererComponent) {
    int rowIndex = key.intValue();
    Rectangle cellBounds = myComponent.getCellBounds(rowIndex, rowIndex);
    cellBounds.width = rendererComponent.getPreferredSize().width;
    return cellBounds;
  }

  protected Component getRendererComponent(Integer key) {
    ListCellRenderer renderer = myComponent.getCellRenderer();
    if (renderer == null) {
      return null;
    }
    ListModel model = myComponent.getModel();
    int rowIndex = key.intValue();
    if (rowIndex >= model.getSize()) {
      return null;
    }

    return renderer.getListCellRendererComponent(
      myComponent,
      model.getElementAt(rowIndex),
      rowIndex,
      myComponent.isSelectedIndex(rowIndex),
      myComponent.hasFocus()
    );
  }
}