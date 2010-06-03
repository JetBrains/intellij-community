/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.ui.RowEditableTableModel;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Maxim.Medvedev
 */
public abstract class TableWithButtons {
  public TableWithButtons(RowEditableTableModel model) {
    myModel = model;
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int selectedColumn = myTable.getSelectedColumn();
        if (selectedColumn < 0) selectedColumn = 0;
        myModel.addRow();
        myTable.setRowSelectionInterval(myModel.getRowCount() - 1, myModel.getRowCount() - 1);
        myTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
        innerUpdate();
        myTable.requestFocus();
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int selectedRow = myTable.getSelectedRow();
        int selectedColumn = myTable.getSelectedColumn();

        myModel.removeRow(myTable.getSelectedRow());

        if (selectedRow == myModel.getRowCount()) selectedRow--;
        if (myModel.getRowCount() == 0) return;
        myTable.setRowSelectionInterval(selectedRow, selectedRow);
        myTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
        innerUpdate();
        myTable.requestFocus();
      }
    });
    myMoveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selectedRow = myTable.getSelectedRow();
        int selectedColumn = myTable.getSelectedColumn();
        myModel.exchangeRows(selectedRow, selectedRow - 1);
        myTable.setRowSelectionInterval(selectedRow - 1, selectedRow - 1);
        myTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
        innerUpdate();
        myTable.requestFocus();
      }
    });
    myMoveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selectedRow = myTable.getSelectedRow();
        int selectedColumn = myTable.getSelectedColumn();
        myModel.exchangeRows(selectedRow, selectedRow + 1);
        myTable.setRowSelectionInterval(selectedRow + 1, selectedRow + 1);
        myTable.setColumnSelectionInterval(selectedColumn, selectedColumn);
        innerUpdate();
        myTable.requestFocus();
      }
    });

    myModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        innerUpdate();
      }
    });
  }

  private RowEditableTableModel myModel;

  private JPanel myPanel;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JButton myMoveUpButton;
  private JButton myMoveDownButton;
  private JBTable myTable;

  private void createUIComponents() {
    myTable = new JBTable(myModel);
    myTable.setPreferredScrollableViewportSize(new Dimension(450, myTable.getRowHeight() * 8));
  }

  private void innerUpdate() {
    final int selectedRow = myTable.getSelectedRow();
    final int rowCount = myModel.getRowCount();
    myMoveUpButton.setEnabled(selectedRow > 0);
    myMoveDownButton.setEnabled(selectedRow + 1 < rowCount && rowCount > 1);
    myRemoveButton.setEnabled(rowCount > 0);
    update();
  }

  protected abstract void update();

  public JBTable getTable() {
    return myTable;
  }

  public JPanel getPanel() {
    return myPanel;
  }
}
