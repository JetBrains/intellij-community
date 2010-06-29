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

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ListExpandTipHandler extends AbstractToolTipHandler<Integer, JList>{
  public static void install(JList list) {
    installAndGet(list);
  }

  public static ListExpandTipHandler installAndGet(JList list) {
    return new ListExpandTipHandler(list);
  }

  protected ListExpandTipHandler(final JList list) {
    super(list);

    final ListSelectionListener selectionListener = new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        updateSelection(list);
      }
    };
    list.getSelectionModel().addListSelectionListener( selectionListener );

    list.addPropertyChangeListener("selectionModel", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateSelection(list);

        if (evt.getOldValue() != null) {
          ((ListSelectionModel)evt.getOldValue()).removeListSelectionListener(selectionListener);
        }
        if (evt.getNewValue() != null) {
          ((ListSelectionModel)evt.getNewValue()).addListSelectionListener(selectionListener);
        }
      }
    });


    final ListDataListener modelListener = new ListDataListener() {
      public void intervalAdded(ListDataEvent e) {
        updateSelection(list);
      }

      public void intervalRemoved(ListDataEvent e) {
        updateSelection(list);
      }

      public void contentsChanged(ListDataEvent e) {
        updateSelection(list);
      }
    };

    if (list.getModel() != null) list.getModel().addListDataListener(modelListener);
    list.addPropertyChangeListener("model", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateSelection(list);

        if (evt.getOldValue() != null) {
          ((ListModel)evt.getOldValue()).removeListDataListener(modelListener);
        }
        if (evt.getNewValue() != null) {
          ((ListModel)evt.getNewValue()).addListDataListener(modelListener);
        }
      }
    });
  }

  private void updateSelection(JList list) {
    int selection = list.getSelectedIndex();
    handleSelectionChange(selection == -1 ? null : new Integer(selection));
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