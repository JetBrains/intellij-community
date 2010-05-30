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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.ui.RowEditableTableModel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
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
  private final List<GrTableParameterInfo> infos;
  private final GrMethod myMethod;
  private final GrChangeSignatureDialog myDialog;
  private final Project myProject;

  public GrParameterTableModel(GrMethod method, GrChangeSignatureDialog dialog, Project project) {
    myMethod = method;
    myDialog = dialog;
    final GrParameter[] parameters = myMethod.getParameters();
    infos = new ArrayList<GrTableParameterInfo>(parameters.length);
    for (int i = 0; i < parameters.length; i++) {
      GrParameter parameter = parameters[i];
      infos.add(new GrTableParameterInfo(parameter, i));
    }
    myProject = project;
  }

  public void addRow() {
    final int row = infos.size();
    infos.add(new GrTableParameterInfo(myProject, myMethod));
    fireTableRowsInserted(row, row);
  }

  public void removeRow(int index) {
    infos.remove(index);
    fireTableRowsDeleted(index, index);
  }

  public void exchangeRows(int index1, int index2) {
    final GrTableParameterInfo info = infos.get(index1);
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
    final GrTableParameterInfo info = infos.get(rowIndex);
    switch (columnIndex) {
      case 0:
        return info.getTypeFragment();
      case 1:
        return info.getNameFragment();
      case 2:
        return info.getDefaultInitializerFragment();
      case 3:
        return info.getDefaultValueFragment();
      default:
        throw new IllegalArgumentException();
    }
  }

  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex) {
    if (rowIndex < 0 || rowIndex >= infos.size()) return;
    if (columnIndex < 0 || columnIndex > 3) return;
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
    switch (columnIndex) {
      case 0:
        return PsiCodeFragment.class;
      case 1:
      case 2:
      case 3:
        return GroovyCodeFragment.class;
      default:
        throw new IllegalArgumentException();
    }
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    if (columnIndex < 3) return true;
    GrTableParameterInfo info = infos.get(rowIndex);
    return info.getOldIndex() < 0;
  }

  public List<GrTableParameterInfo> getParameterInfos() {
    return infos;
  }
}