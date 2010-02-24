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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.EventObject;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.02.2008
 */
public class DynamicMethodDialog extends DynamicDialog {
  private final GrReferenceExpression myReferenceExpression;

  public DynamicMethodDialog(GrReferenceExpression referenceExpression) {
    super(referenceExpression, QuickfixUtil.createSettings(referenceExpression), GroovyExpectedTypesProvider.calculateTypeConstraints((GrExpression)referenceExpression.getParent()));
    myReferenceExpression = referenceExpression;
    assert getSettings().isMethod();

    final List<MyPair> pairs = getSettings().getPairs();
    setupParameterTable(pairs);
    setupParameterList(pairs);
    setTitle(GroovyBundle.message("add.dynamic.method"));
    setUpTypeLabel(GroovyBundle.message("dynamic.method.return.type"));
  }

  protected void setUpTableNameLabel(String text) {
    super.setUpTableNameLabel(getSettings().getPairs().isEmpty() ? GroovyBundle.message("dynamic.properties.table.no.arguments") : text);
  }

  private void setupParameterTable(final List<MyPair> pairs) {
    final JTable table = getParametersTable();

    MySuggestedNameCellEditor suggestedNameCellEditor = new MySuggestedNameCellEditor(QuickfixUtil.getArgumentsNames(pairs));
    table.setDefaultEditor(String.class, suggestedNameCellEditor);

    suggestedNameCellEditor.addCellEditorListener(new CellEditorListener() {
      public void editingStopped(ChangeEvent e) {
        final int editingColumn = table.getSelectedColumn();
        if (editingColumn != 0) return;

        final int editingRow = table.getSelectedRow();
        if (editingRow < 0 || editingRow >= pairs.size()) return;

        String newNameValue = ((MySuggestedNameCellEditor)e.getSource()).getCellEditorValue();

        final MyPair editingPair = pairs.get(editingRow);
        editingPair.setFirst(newNameValue);
      }

      public void editingCanceled(ChangeEvent e) {
      }
    });
  }

  protected boolean isTableVisible() {
    return true;
  }

  private void setupParameterList(List<MyPair> arguments) {
    final JTable table = getParametersTable();

    //TODO: add header
    final ListTableModel<MyPair> dataModel = new ListTableModel<MyPair>(new NameColumnInfo(), new TypeColumnInfo());
    dataModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        fireDataChanged();
      }
    });
    dataModel.setItems(arguments);
    table.setModel(dataModel);
    if (!arguments.isEmpty()) {
      String max0 = arguments.get(0).first;
      String max1 = arguments.get(0).second;
      for (MyPair argument : arguments) {
        if (argument.first.length() > max0.length()) max0 = argument.first;
        if (argument.second.length() > max1.length()) max1 = argument.second;
      }

      final FontMetrics metrics = table.getFontMetrics(table.getFont());
      final TableColumn column0 = table.getColumnModel().getColumn(0);
      column0.setPreferredWidth(metrics.stringWidth(max0 + "  "));

      final TableColumn column1 = table.getColumnModel().getColumn(1);
      column1.setPreferredWidth(metrics.stringWidth(max1 + "  "));
    }
  }


  private class TypeColumnInfo extends ColumnInfo<MyPair, String> {
    public TypeColumnInfo() {
      super(GroovyBundle.message("dynamic.name"));
    }

    public String valueOf(MyPair pair) {
      return pair.second;
    }

    public boolean isCellEditable(MyPair stringPsiTypeMyPair) {
      return false;
    }

    public void setValue(MyPair pair, String value) {
      PsiType type;
      try {
        type = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(value).getType();
      }
      catch (IncorrectOperationException e) {
        return;
      }

      if (type == null) return;
      pair.setSecond(type.getCanonicalText());
    }
  }

  private static class NameColumnInfo extends ColumnInfo<MyPair, String> {
    public NameColumnInfo() {
      super(GroovyBundle.message("dynamic.type"));
    }

    public boolean isCellEditable(MyPair myPair) {
      return true;
    }

    public String valueOf(MyPair pair) {
      return pair.first;
    }
  }

  protected void updateOkStatus() {
    super.updateOkStatus();

    if (getParametersTable().isEditing()) setOKActionEnabled(false);
  }

  private static class MySuggestedNameCellEditor extends AbstractTableCellEditor {
    JTextField myNameField;

    public MySuggestedNameCellEditor(String[] names) {
      myNameField = names.length == 0 ? new JTextField() : new JTextField(names[0]);
    }

    public String getCellEditorValue() {
      return myNameField.getText();
    }

    public boolean isCellEditable(EventObject e) {
      return true;
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      if (value instanceof String) {
        myNameField.setText((String)value);
      }
      return myNameField;
    }
  }
}