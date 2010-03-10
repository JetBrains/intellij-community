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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GrParameterTableModel extends AbstractTableModel implements RowEditableTableModel {
  private final List<GrParameterInfo> infos;
  private final GrMethod myMethod;
  private final GrChangeSignatureDialog myDialog;


  public GrParameterTableModel(GrMethod method, GrChangeSignatureDialog dialog) {
    myMethod = method;
    myDialog = dialog;
    final GrParameter[] parameters = myMethod.getParameters();
    infos = new ArrayList<GrParameterInfo>(parameters.length);
    for (int i = 0; i < parameters.length; i++) {
      GrParameter parameter = parameters[i];
      infos.add(new GrParameterInfo(parameter, i));
    }
  }

  public void addRow() {
    final int row = infos.size();
    infos.add(new GrParameterInfo());
    fireTableRowsInserted(row, row);
  }

  public void removeRow(int index) {
    infos.remove(index);
    fireTableRowsDeleted(index, index);
  }

  public void exchangeRows(int index1, int index2) {
    final GrParameterInfo info = infos.get(index1);
    infos.set(index1, infos.get(index2));
    infos.set(index2, info);
    fireTableRowsUpdated(Math.min(index1, index2), Math.max(index1, index2));
  }


  public int getRowCount() {
    return infos.size();
  }

  public int getColumnCount() {
    return 4;
  }

  @Nullable
  public Object getValueAt(int rowIndex, int columnIndex) {
    if (rowIndex < 0 || rowIndex >= infos.size()) return null;
    final GrParameterInfo info = infos.get(rowIndex);
    switch (columnIndex) {
      case 0:
        return info.getType();
      case 1:
        return info.getName();
      case 2:
        return info.getDefaultInitializer();
      case 3:
        return info.getDefaultValue();
      default:
        throw new IllegalArgumentException();
    }
  }

  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex) {
    if (rowIndex < 0 || rowIndex >= infos.size()) return;
    if (columnIndex < 0 || columnIndex > 3) return;

    String s = value instanceof String ? (String)value : "";
    s = s.trim();
    final GrParameterInfo info = infos.get(rowIndex);
    switch (columnIndex) {
      case 0:
        info.setType(s);
      case 1:
        info.setName(s);
      case 2:
        info.setDefaultInitializer(s);
      case 3:
        info.setDefaultValue(s);
    }
    fireTableCellUpdated(rowIndex, columnIndex);
  }


  @Override
  public String getColumnName(int column) {
    switch (column) {
      case 0:
        return GroovyRefactoringBundle.message("column.name.type");
      case 1:
        return GroovyRefactoringBundle.message("column.name.name");
      case 2:
        return GroovyRefactoringBundle.message("column.name.default.initializer");
      case 3:
        return GroovyRefactoringBundle.message("column.name.default.value");
      default:
        throw new IllegalArgumentException();
    }
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    if (columnIndex < 0 || columnIndex > 3) throw new IllegalArgumentException();
    return String.class;
  }

  
}