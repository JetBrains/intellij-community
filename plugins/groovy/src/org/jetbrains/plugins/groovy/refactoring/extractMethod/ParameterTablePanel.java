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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.ui.TypeSelector;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author ilyas
 */
public class ParameterTablePanel extends JPanel {

  private Project myProject;
  private ParameterInfo[] myParameterInfos;
  private TypeSelector[] myParameterTypeSelectors;
  private GroovyExtractMethodDialog myDialog;

  private Table myTable;
  private MyTableModel myTableModel;
  private JButton myUpButton;
  private JButton myDownButton;
  private JComboBox myTypeRendererCombo;

  public ParameterTablePanel() {
    super(new BorderLayout());
  }

  void init(GroovyExtractMethodDialog dialog, ExtractMethodInfoHelper helper) {

    setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), GroovyRefactoringBundle.message("parameters.border.title")));

    myDialog = dialog;
    myProject = helper.getProject();
    myParameterInfos = helper.getParameterInfos();

    myTableModel = new MyTableModel();
    myTable = new Table(myTableModel);
    DefaultCellEditor defaultEditor = (DefaultCellEditor) myTable.getDefaultEditor(Object.class);
    defaultEditor.setClickCountToStart(1);

    myTable.setTableHeader(null);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getColumnModel().getColumn(MyTableModel.CHECKMARK_COLUMN).setCellRenderer(new CheckBoxTableCellRenderer());
    myTable.getColumnModel().getColumn(MyTableModel.CHECKMARK_COLUMN).setMaxWidth(new JCheckBox().getPreferredSize().width);
    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_NAME_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        ParameterInfo info = myParameterInfos[row];
        setText(info.getName());
        return this;
      }
    });

    PsiManager manager = PsiManager.getInstance(myProject);
    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    myParameterTypeSelectors = new TypeSelector[myParameterInfos.length];
    for (int i = 0; i < myParameterTypeSelectors.length; i++) {
//      final GrExpression[] occurrences = ExtractMethodUtil.findVariableOccurrences(helper.getStatements(), myParameterInfos[i].getName());
//      final TypeSelectorManager manager = new TypeSelectorManagerImpl(myProject, myParameterInfos[i].getType(), occurrences, areTypesDirected());
      PsiType type = myParameterInfos[i].getType();
      myParameterTypeSelectors[i] = new TypeSelector(type != null ? type : PsiType.getJavaLangObject(manager, scope));
//      myParameterInfos[i].setTypeName(myParameterTypeSelectors[i].getSelectedType());
    }

    myTypeRendererCombo = new JComboBox(myParameterInfos);
    myTypeRendererCombo.setOpaque(true);
    myTypeRendererCombo.setBorder(null);

    myTypeRendererCombo.setRenderer(new DefaultListCellRenderer() {

      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        PsiType type = ((ParameterInfo) value).getType();
        PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
        type = unboxed != null ? unboxed : type;
        setText(type != null ? type.getPresentableText() : "");
        return this;
      }
    });

    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_TYPE_COLUMN).setCellEditor(new AbstractTableCellEditor() {
      TypeSelector myCurrentSelector;
      public Object getCellEditorValue() {
        return myCurrentSelector.getSelectedType();
      }

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

    myTable.setPreferredScrollableViewportSize(new Dimension(250, myTable.getRowHeight() * 5));
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    @NonNls final InputMap inputMap = myTable.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    @NonNls final ActionMap actionMap = myTable.getActionMap();
    actionMap.put("enable_disable", new AbstractAction() {
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

    JPanel listPanel = new JPanel(new BorderLayout());
    JBScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    listPanel.add(scrollPane, BorderLayout.CENTER);
    listPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    add(listPanel, BorderLayout.CENTER);

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    add(buttonsPanel, BorderLayout.EAST);

    buttonsPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets = new Insets(2, 4, 2, 4);

    myUpButton = new JButton();
    myUpButton.setText(GroovyRefactoringBundle.message("row.move.up"));
    myUpButton.setDefaultCapable(false);
    myUpButton.setMnemonic(KeyEvent.VK_U);
    buttonsPanel.add(myUpButton, gbConstraints);

    myDownButton = new JButton();
    myDownButton.setText(GroovyRefactoringBundle.message("row.move.down"));
    myDownButton.setMnemonic(KeyEvent.VK_D);
    myDownButton.setDefaultCapable(false);
    buttonsPanel.add(myDownButton, gbConstraints);

    gbConstraints.weighty = 1;
    buttonsPanel.add(new JPanel(), gbConstraints);

    myUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) {
          final boolean isStopped = myTable.getCellEditor().stopCellEditing();
          if (!isStopped) return;
        }
        moveSelectedItem(-1);
        updateSignature();
        myTable.requestFocus();
      }
    });

    myDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) {
          final boolean isStopped = myTable.getCellEditor().stopCellEditing();
          if (!isStopped) return;
        }
        moveSelectedItem(+1);
        updateSignature();
        myTable.requestFocus();
      }
    });

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateMoveButtons();
      }
    });
    if (myParameterInfos.length <= 1) {
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
    else {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }
    updateMoveButtons();
  }

  private void updateMoveButtons() {
    int row = myTable.getSelectedRow();
    if (0 <= row && row < myParameterInfos.length) {
      myUpButton.setEnabled(row > 0);
      myDownButton.setEnabled(row < myParameterInfos.length - 1);
    }
    else {
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
  }

  public void setEnabled(boolean enabled) {
    myTable.setEnabled(enabled);
    if (!enabled) {
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
    else {
      updateMoveButtons();
    }
    super.setEnabled(enabled);
  }


  private void moveSelectedItem(int moveIncrement) {
    int row = myTable.getSelectedRow();
    if (row < 0 || row >= myParameterInfos.length) return;
    int targetRow = row + moveIncrement;
    if (targetRow < 0 || targetRow >= myParameterInfos.length) return;

    ParameterInfo currentItem = myParameterInfos[row];
    int currentPosition = currentItem.getPosition();
    ParameterInfo targetItem = myParameterInfos[targetRow];

    // Change real parameter position
    currentItem.setPosition(targetItem.getPosition());
    targetItem.setPosition(currentPosition);

    myParameterInfos[row] = targetItem;
    myParameterInfos[targetRow] = currentItem;

    TypeSelector currentSelector = myParameterTypeSelectors[row];
    myParameterTypeSelectors[row] = myParameterTypeSelectors[targetRow];
    myParameterTypeSelectors[targetRow] = currentSelector;
    myTypeRendererCombo.setModel(new DefaultComboBoxModel(myParameterInfos));
    myTableModel.fireTableRowsUpdated(Math.min(targetRow, row), Math.max(targetRow, row));
    myTable.getSelectionModel().setSelectionInterval(targetRow, targetRow);
  }

  protected void updateSignature(){
    myDialog.updateSignature();
  }

  protected void doEnterAction(){
    myDialog.clickDefaultButton();
  }

  protected void doCancelAction(){
    myDialog.doCancelAction();
  }

  private class MyTableModel extends AbstractTableModel {
    public static final int CHECKMARK_COLUMN = 0;
    public static final int PARAMETER_TYPE_COLUMN = 1;
    public static final int PARAMETER_NAME_COLUMN = 2;

    public int getRowCount() {
      return myParameterInfos.length;
    }

    public int getColumnCount() {
      return 3;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN: {
          return myParameterInfos[rowIndex].passAsParameter() ? Boolean.TRUE : Boolean.FALSE;
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

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN: {
          myParameterInfos[rowIndex].setPassAsParameter((Boolean) aValue);
          fireTableRowsUpdated(rowIndex, rowIndex);
          myTable.getSelectionModel().setSelectionInterval(rowIndex, rowIndex);
          updateSignature();
          break;
        }
        case PARAMETER_NAME_COLUMN: {
          ParameterInfo info = myParameterInfos[rowIndex];
          String name = (String) aValue;
          if (GroovyNamesUtil.isIdentifier(name)) {
            info.setNewName(name);
          }
          updateSignature();
          break;
        }
        case PARAMETER_TYPE_COLUMN: {
          ParameterInfo info = myParameterInfos[rowIndex];
          info.setType((PsiType) aValue);
          updateSignature();
          break;
        }
      }
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN:
          return isEnabled();
        case PARAMETER_NAME_COLUMN:
          return isEnabled() && myParameterInfos[rowIndex].passAsParameter();
        case PARAMETER_TYPE_COLUMN:
          return isEnabled() && myParameterInfos[rowIndex].passAsParameter() && !(myParameterTypeSelectors[rowIndex].getComponent() instanceof JLabel);
        default:
          return false;
      }
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECKMARK_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }
  }

  private class CheckBoxTableCellRenderer extends BooleanTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      rendererComponent.setEnabled(ParameterTablePanel.this.isEnabled());
      return rendererComponent;
    }
  }


}
