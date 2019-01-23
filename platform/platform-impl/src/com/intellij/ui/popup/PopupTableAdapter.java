// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.Consumer;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Set;

/**
 * @author yole
 */
class PopupTableAdapter<T> implements PopupChooserBuilder.PopupComponentAdapter<T> {
  private final PopupChooserBuilder myBuilder;
  private final JTable myTable;

  PopupTableAdapter(PopupChooserBuilder builder, JTable table) {
    myBuilder = builder;
    myTable = table;
  }

  @Override
  public JComponent getComponent() {
    return myTable;
  }

  @Override
  public void setItemChosenCallback(Consumer<? super T> callback) {
    throw new UnsupportedOperationException("setItemChosenCallback with element callback is not implemented for tables yet");
  }

  @Override
  public void setItemsChosenCallback(Consumer<? super Set<T>> callback) {
    throw new UnsupportedOperationException("setItemsChosenCallback with element callback is not implemented for tables yet");
  }

  @Override
  public JScrollPane createScrollPane() {
    if (myTable instanceof TreeTable) {
      TreeUtil.expandAll(((TreeTable)myTable).getTree());
    }

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);

    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

    if (myTable.getSelectedRow() == -1) {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }

    if (myTable.getRowCount() >= 20) {
      scrollPane.getViewport().setPreferredSize(new Dimension(myTable.getPreferredScrollableViewportSize().width, 300));
    }
    else {
      scrollPane.getViewport().setPreferredSize(myTable.getPreferredSize());
    }

    if (myBuilder.isAutoselectOnMouseMove()) {
      myTable.addMouseMotionListener(new MouseMotionAdapter() {
        boolean myIsEngaged = false;

        @Override
        public void mouseMoved(MouseEvent e) {
          if (myIsEngaged) {
            int index = myTable.rowAtPoint(e.getPoint());
            myTable.getSelectionModel().setSelectionInterval(index, index);
          }
          else {
            myIsEngaged = true;
          }
        }
      });
    }

    return scrollPane;
  }
}
