/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extract;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.ui.TypeSelector;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * @author ilyas
 */
public abstract class ParameterTablePanel extends JPanel {

  private ParameterInfo[] myParameterInfos;
  private TypeSelector[] myParameterTypeSelectors;

  private JBTable myTable;
  private MyTableModel myTableModel;
  private JComboBox myTypeRendererCombo;

  public ParameterTablePanel() {
    super(new BorderLayout());
  }

  public void init(ExtractInfoHelper helper) {

    setBorder(IdeBorderFactory.createTitledBorder(GroovyRefactoringBundle.message("parameters.border.title"), false));

    myParameterInfos = helper.getParameterInfos();

    myTableModel = new MyTableModel();
    myTable = new JBTable(myTableModel);
    DefaultCellEditor defaultEditor = (DefaultCellEditor)myTable.getDefaultEditor(Object.class);
    defaultEditor.setClickCountToStart(1);

    myTable.setTableHeader(null);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    TableColumn checkBoxColumn = myTable.getColumnModel().getColumn(MyTableModel.CHECKMARK_COLUMN);
    TableUtil.setupCheckboxColumn(checkBoxColumn);
    checkBoxColumn.setCellRenderer(new CheckBoxTableCellRenderer());

    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_NAME_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        ParameterInfo info = myParameterInfos[row];
        setText(info.getName());
        return this;
      }
    });

    Project project = helper.getProject();
    PsiManager manager = PsiManager.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    myParameterTypeSelectors = new TypeSelector[myParameterInfos.length];
    for (int i = 0; i < myParameterTypeSelectors.length; i++) {
//      final GrExpression[] occurrences = ExtractUtil.findVariableOccurrences(helper.getStatements(), myParameterInfos[i].getName());
//      final TypeSelectorManager manager = new TypeSelectorManagerImpl(myProject, myParameterInfos[i].getType(), occurrences, areTypesDirected());
      PsiType type = myParameterInfos[i].getType();
      myParameterTypeSelectors[i] = new TypeSelector(type != null ? type : PsiType.getJavaLangObject(manager, scope), project);
//      myParameterInfos[i].setTypeName(myParameterTypeSelectors[i].getSelectedType());
    }

    myTypeRendererCombo = new JComboBox(myParameterInfos);
    myTypeRendererCombo.setOpaque(true);
    myTypeRendererCombo.setBorder(null);

    myTypeRendererCombo.setRenderer(new ListCellRendererWrapper<ParameterInfo>() {
      @Override
      public void customize(JList list, ParameterInfo info, int index, boolean selected, boolean hasFocus) {
        PsiType type = info.getType();
        PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
        type = unboxed != null ? unboxed : type;
        setText(type != null ? type.getPresentableText() : "");
      }
    });

    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_TYPE_COLUMN).setCellEditor(new AbstractTableCellEditor() {
      TypeSelector myCurrentSelector;

      @Override
      public Object getCellEditorValue() {
        return myCurrentSelector.getSelectedType();
      }

      @Override
      public Component getTableCellEditorComponent(final JTable table,
                                                   final Object value,
                                                   final boolean isSelected,
                                                   final int row,
                                                   final int column) {
        myCurrentSelector = myParameterTypeSelectors[row];
        return myCurrentSelector.getComponent();
      }
    });

    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_TYPE_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (myParameterTypeSelectors[row].getComponent() instanceof JComboBox) {
          myTypeRendererCombo.setSelectedIndex(row);
          return myTypeRendererCombo;
        }

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        ParameterInfo info = myParameterInfos[row];
        PsiType type = info.getType();
        PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
        type = unboxed != null ? unboxed : type;
        setText(type != null ? type.getPresentableText() : "");
        return this;
      }
    });

    myTable.setPreferredScrollableViewportSize(JBUI.size(250, myTable.getRowHeight() * 5));
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(JBUI.emptySize());
    @NonNls final InputMap inputMap = myTable.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    @NonNls final ActionMap actionMap = myTable.getActionMap();
    actionMap.put("enable_disable", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;
        int[] rows = myTable.getSelectedRows();
        if (rows.length > 0) {
          boolean valueToBeSet = false;
          for (int row : rows) {
            if (!myParameterInfos[row].passAsParameter()) {
              valueToBeSet = true;
              break;
            }
          }
          for (int row : rows) {
            myParameterInfos[row].setPassAsParameter(valueToBeSet);
          }
          myTableModel.fireTableRowsUpdated(rows[0], rows[rows.length - 1]);
          TableUtil.selectRows(myTable, rows);
        }
      }
    });
    // F2 should edit the name
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "edit_parameter_name");
    actionMap.put("edit_parameter_name", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!myTable.isEditing()) {
          int row = myTable.getSelectedRow();
          if (row >= 0 && row < myTableModel.getRowCount()) {
            TableUtil.editCellAt(myTable, row, MyTableModel.PARAMETER_NAME_COLUMN);
          }
        }
      }
    });

    // make ENTER work when the table has focus
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "invokeImpl");
    actionMap.put("invokeImpl", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TableCellEditor editor = myTable.getCellEditor();
        if (editor != null) {
          editor.stopCellEditing();
        }
        else {
          doEnterAction();
        }
      }
    });

    // make ESCAPE work when the table has focus
    actionMap.put("doCancel", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TableCellEditor editor = myTable.getCellEditor();
        if (editor != null) {
          editor.stopCellEditing();
        }
        else {
          doCancelAction();
        }
      }
    });

    JPanel listPanel = ToolbarDecorator.createDecorator(myTable).disableAddAction().disableRemoveAction().createPanel();
    add(listPanel, BorderLayout.CENTER);
  }

  protected abstract void updateSignature();

  protected abstract void doEnterAction();

  protected abstract void doCancelAction();

  private class MyTableModel extends AbstractTableModel implements EditableModel {
    public static final int CHECKMARK_COLUMN = 0;
    public static final int PARAMETER_TYPE_COLUMN = 1;
    public static final int PARAMETER_NAME_COLUMN = 2;

    @Override
    public void addRow() {
      throw new IllegalAccessError("Not implemented");
    }

    @Override
    public void removeRow(int index) {
      throw new IllegalAccessError("Not implemented");
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      if (oldIndex < 0 || newIndex < 0) return;
      if (oldIndex >= myParameterInfos.length || newIndex >= myParameterInfos.length) return;

      final ParameterInfo old = myParameterInfos[oldIndex];
      myParameterInfos[oldIndex] = myParameterInfos[newIndex];
      myParameterInfos[newIndex] = old;

      myParameterInfos[oldIndex].setPosition(oldIndex);
      myParameterInfos[newIndex].setPosition(newIndex);

      fireTableRowsUpdated(Math.min(oldIndex, newIndex), Math.max(oldIndex, newIndex));
      updateSignature();
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      if (oldIndex < 0 || newIndex < 0) return false;
      if (oldIndex >= myParameterInfos.length || newIndex >= myParameterInfos.length) return false;
      return true;
    }

    @Override
    public int getRowCount() {
      return myParameterInfos.length;
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN: {
          return myParameterInfos[rowIndex].passAsParameter();
        }
        case PARAMETER_NAME_COLUMN: {
          return myParameterInfos[rowIndex].getName();
        }
        case PARAMETER_TYPE_COLUMN: {
          PsiType type = myParameterInfos[rowIndex].getType();
          return type != null ? type.getPresentableText() : "";
        }
      }
      assert false;
      return null;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN: {
          myParameterInfos[rowIndex].setPassAsParameter((Boolean)aValue);
          fireTableRowsUpdated(rowIndex, rowIndex);
          myTable.getSelectionModel().setSelectionInterval(rowIndex, rowIndex);
          updateSignature();
          break;
        }
        case PARAMETER_NAME_COLUMN: {
          ParameterInfo info = myParameterInfos[rowIndex];
          String name = (String)aValue;
          if (GroovyNamesUtil.isIdentifier(name)) {
            info.setNewName(name);
          }
          updateSignature();
          break;
        }
        case PARAMETER_TYPE_COLUMN: {
          ParameterInfo info = myParameterInfos[rowIndex];
          info.setType((PsiType)aValue);
          updateSignature();
          break;
        }
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN:
          return isEnabled();
        case PARAMETER_NAME_COLUMN:
          return isEnabled() && myParameterInfos[rowIndex].passAsParameter();
        case PARAMETER_TYPE_COLUMN:
          return isEnabled() &&
                 myParameterInfos[rowIndex].passAsParameter() &&
                 !(myParameterTypeSelectors[rowIndex].getComponent() instanceof JLabel);
        default:
          return false;
      }
    }

    @Override
    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECKMARK_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }
  }

  private class CheckBoxTableCellRenderer extends BooleanTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      rendererComponent.setEnabled(ParameterTablePanel.this.isEnabled());
      return rendererComponent;
    }
  }
}
