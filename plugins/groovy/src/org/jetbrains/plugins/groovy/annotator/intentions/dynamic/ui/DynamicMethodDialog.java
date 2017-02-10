/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.EventObject;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.02.2008
 */
public class DynamicMethodDialog extends DynamicDialog {

  public DynamicMethodDialog(GrReferenceExpression referenceExpression) {
    super(referenceExpression, QuickfixUtil.createSettings(referenceExpression),
          GroovyExpectedTypesProvider.calculateTypeConstraints((GrExpression)referenceExpression.getParent()), true);
    assert getSettings().isMethod();

    final List<ParamInfo> pairs = getSettings().getParams();
    setupParameterTable(pairs);
    setupParameterList(pairs);
    setTitle(GroovyBundle.message("add.dynamic.method"));
    setUpTypeLabel(GroovyBundle.message("dynamic.method.return.type"));
  }

  private void setupParameterTable(final List<ParamInfo> pairs) {

    MySuggestedNameCellEditor suggestedNameCellEditor = new MySuggestedNameCellEditor(QuickfixUtil.getArgumentsNames(pairs));
    myParametersTable.setDefaultEditor(String.class, suggestedNameCellEditor);

    suggestedNameCellEditor.addCellEditorListener(new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent e) {
        final int editingColumn = myParametersTable.getSelectedColumn();
        if (editingColumn != 0) return;

        final int editingRow = myParametersTable.getSelectedRow();
        if (editingRow < 0 || editingRow >= pairs.size()) return;

        String newNameValue = ((MySuggestedNameCellEditor)e.getSource()).getCellEditorValue();

        final ParamInfo editingPair = pairs.get(editingRow);
        editingPair.name = newNameValue;
      }

      @Override
      public void editingCanceled(ChangeEvent e) {
      }
    });
  }

  private void setupParameterList(List<ParamInfo> arguments) {
    final ListTableModel<ParamInfo> dataModel = new ListTableModel<>(new NameColumnInfo(), new TypeColumnInfo());
    dataModel.setItems(arguments);
    myParametersTable.setModel(dataModel);

    if (arguments.isEmpty()) return;

    String max0 = arguments.get(0).name;
    String max1 = arguments.get(0).type;
    for (ParamInfo argument : arguments) {
      if (argument.name.length() > max0.length()) max0 = argument.name;
      if (argument.type.length() > max1.length()) max1 = argument.type;
    }

    final FontMetrics metrics = myParametersTable.getFontMetrics(myParametersTable.getFont());
    final TableColumn column0 = myParametersTable.getColumnModel().getColumn(0);
    column0.setPreferredWidth(metrics.stringWidth(max0 + "  "));

    final TableColumn column1 = myParametersTable.getColumnModel().getColumn(1);
    column1.setPreferredWidth(metrics.stringWidth(max1 + "  "));
  }


  private class TypeColumnInfo extends ColumnInfo<ParamInfo, String> {
    public TypeColumnInfo() {
      super(GroovyBundle.message("dynamic.type"));
    }

    @Override
    public String valueOf(ParamInfo pair) {
      return pair.type;
    }

    @Override
    public boolean isCellEditable(ParamInfo stringPsiTypeMyPair) {
      return false;
    }

    @Override
    public void setValue(ParamInfo pair, String value) {
      PsiType type;
      try {
        type = GroovyPsiElementFactory.getInstance(myProject).createTypeElement(value).getType();
      }
      catch (IncorrectOperationException e) {
        return;
      }

      pair.type = type.getCanonicalText();
    }
  }

  private static class NameColumnInfo extends ColumnInfo<ParamInfo, String> {
    public NameColumnInfo() {
      super(GroovyBundle.message("dynamic.name"));
    }

    @Override
    public boolean isCellEditable(ParamInfo myPair) {
      return true;
    }

    @Override
    public String valueOf(ParamInfo pair) {
      return pair.name;
    }
  }

  private static class MySuggestedNameCellEditor extends AbstractTableCellEditor {
    JTextField myNameField;

    public MySuggestedNameCellEditor(String[] names) {
      myNameField = names.length == 0 ? new JTextField() : new JTextField(names[0]);
    }

    @Override
    public String getCellEditorValue() {
      return myNameField.getText();
    }

    @Override
    public boolean isCellEditable(EventObject e) {
      return true;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      if (value instanceof String) {
        myNameField.setText((String)value);
      }
      return myNameField;
    }
  }
}