/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BadExceptionThrownInspection extends ExpressionInspection {

  /**
   * @noinspection PublicField
   */
  public String exceptionCheckString = "java.lang.Throwable" + "," +
                                       "java.lang.Exception" + "," +
                                       "java.lang.Error" + "," +
                                       "java.lang.RuntimeException" + "," +
                                       "java.lang.NullPointerException" + "," +
                                       "java.lang.ClassCastException" + "," +
                                       "java.lang.ArrayIndexOutOfBoundsException";
  private final List<String> exceptionsList = new ArrayList<String>(32);
  private final Object lock = new Object();

  {
    parseCallCheckString();
  }

  public void readSettings(Element element) throws InvalidDataException {
    super.readSettings(element);
    parseCallCheckString();
  }

  private void parseCallCheckString() {
    synchronized (lock) {
      exceptionsList.clear();
      final String[] strings = exceptionCheckString.split(",");
      for (String string : strings) {
        exceptionsList.add(string);
      }
    }
  }

  public void writeSettings(Element element) throws WriteExternalException {
    formatCallCheckString();
    super.writeSettings(element);
  }

  private void formatCallCheckString() {
    final StringBuffer buffer = new StringBuffer();
    synchronized (lock) {
      boolean first = true;
      for (String exceptionName : exceptionsList) {
        if (first) {
          first = false;
        }
        else {
          buffer.append(',');
        }
        buffer.append(exceptionName);
      }
    }
    exceptionCheckString = buffer.toString();
  }

  public String getID() {
    return "ProhibitedExceptionThrown";
  }

  public String getGroupDisplayName() {
    return GroupNames.ERRORHANDLING_GROUP_NAME;
  }

  public JComponent createOptionsPanel() {
    final Form form = new Form();
    return form.getContentPanel();
  }

  public String buildErrorString(PsiElement location) {
    final PsiThrowStatement throwStatement = (PsiThrowStatement)location
      .getParent();
    assert throwStatement != null;
    final PsiExpression exception = throwStatement.getException();
    final PsiType type = exception.getType();
    final String exceptionName = type.getPresentableText();
    return InspectionGadgetsBundle.message("bad.exception.thrown.problem.descriptor", exceptionName);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new BadExceptionThrownVisitor();
  }

  private class BadExceptionThrownVisitor extends BaseInspectionVisitor {
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression exception = statement.getException();
      if (exception == null) {
        return;
      }
      final PsiType type = exception.getType();
      if (type == null) {
        return;
      }
      final String text = type.getCanonicalText();

      final List<String> exceptionListCopy;
      synchronized (lock) {
        exceptionListCopy = new ArrayList<String>(exceptionsList);
      }
      for (String exceptionClass : exceptionListCopy) {
        if (text.equals(exceptionClass)) {
          registerStatementError(statement);
          return;
        }
      }
    }
  }

  /**
   * @noinspection PublicInnerClass
   */
  public class Form {
    private JPanel contentPanel;
    private JButton addButton;
    private JButton deleteButton;
    private JTable table;

    public Form() {
      super();
      table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
      table.setRowSelectionAllowed(true);
      table.setSelectionMode(
        ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      table.setEnabled(true);
      final ReturnCheckSpecificationTableModel model =
        new ReturnCheckSpecificationTableModel();
      table.setModel(model);
      addButton.setEnabled(true);
      addButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          synchronized (lock) {
            exceptionsList.add("");
          }
          model.fireTableStructureChanged();
        }
      });
      deleteButton.setEnabled(true);
      deleteButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final int[] selectedRows = table.getSelectedRows();
          Arrays.sort(selectedRows);
          synchronized (lock) {
            for (int i = selectedRows.length - 1; i >= 0; i--) {
              exceptionsList.remove(selectedRows[i]);
            }
          }
          model.fireTableStructureChanged();
        }
      });
    }

    public JComponent getContentPanel() {
      return contentPanel;
    }
  }

  private class ReturnCheckSpecificationTableModel extends AbstractTableModel {
    public int getRowCount() {
      synchronized (lock) {
        return exceptionsList.size();
      }
    }

    public int getColumnCount() {
      return 1;
    }

    public String getColumnName(int columnIndex) {
      return InspectionGadgetsBundle.message("exception.class.column.name");
    }

    public Class getColumnClass(int columnIndex) {
      return String.class;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      synchronized (lock) {
        return exceptionsList.get(rowIndex);
      }
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      synchronized (lock) {
        exceptionsList.set(rowIndex, (String)aValue);
      }
    }
  }
}
