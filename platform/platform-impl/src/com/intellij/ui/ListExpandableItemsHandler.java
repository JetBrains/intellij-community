// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.popup.list.InlineActionsUtilKt;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ListExpandableItemsHandler extends AbstractExpandableItemsHandler<Integer, JList> {
  protected ListExpandableItemsHandler(final JList list) {
    super(list);

    final ListSelectionListener selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        updateSelection(list);
      }
    };
    list.getSelectionModel().addListSelectionListener(selectionListener);

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
      @Override
      public void intervalAdded(ListDataEvent e) {
        updateSelection(list);
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        updateSelection(list);
      }

      @Override
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
          ((ListModel<?>)evt.getOldValue()).removeListDataListener(modelListener);
        }
        if (evt.getNewValue() != null) {
          ((ListModel<?>)evt.getNewValue()).addListDataListener(modelListener);
        }
      }
    });
  }

  private void updateSelection(JList list) {
    int selection = list.getSelectedIndices().length == 1 ? list.getSelectedIndex() : -1;
    handleSelectionChange(selection == -1 ? null : Integer.valueOf(selection));
  }

  @Override
  protected Integer getCellKeyForPoint(Point point) {
    int rowIndex = myComponent.locationToIndex(point);
    return rowIndex != -1 ? Integer.valueOf(rowIndex) : null;
  }

  @Override
  protected Pair<Component, Rectangle> getCellRendererAndBounds(Integer key) {
    int rowIndex = key.intValue();

    Rectangle bounds = myComponent.getCellBounds(rowIndex, rowIndex);
    if (bounds == null) return null;

    ListCellRenderer renderer = myComponent.getCellRenderer();
    if (renderer == null) return null;

    ListModel model = myComponent.getModel();
    if (rowIndex >= model.getSize()) return null;

    Component rendererComponent = renderer.getListCellRendererComponent(
      myComponent,
      model.getElementAt(rowIndex),
      rowIndex,
      myComponent.isSelectedIndex(rowIndex),
      myComponent.hasFocus()
    );

    AppUIUtil.targetToDevice(rendererComponent, myComponent);

    if (!containsInlineButtons(rendererComponent)) bounds.width = rendererComponent.getPreferredSize().width;

    return Pair.create(rendererComponent, bounds);
  }

  private static boolean containsInlineButtons(Component cmp) {
    if (cmp instanceof JComponent && Boolean.TRUE.equals(((JComponent)cmp).getClientProperty(InlineActionsUtilKt.INLINE_BUTTON_MARKER))) return true;

    if (cmp instanceof Container) {
      for (Component child : ((Container)cmp).getComponents()) {
        if (containsInlineButtons(child)) return true;
      }
    }
    return false;
  }
}