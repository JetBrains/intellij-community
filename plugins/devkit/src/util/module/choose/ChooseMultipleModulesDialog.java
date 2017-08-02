/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.util.module.choose;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ChooseMultipleModulesDialog extends ChooseModulesDialogBase {
  private final JTable myTable;
  private final boolean[] myStates;

  public ChooseMultipleModulesDialog(Project project, List<Module> candidateModules, String title) {
    super(project, candidateModules, title, DevKitBundle.message("select.plugin.modules.to.patch"));
    myTable = new JBTable(new AbstractTableModel() {
      public int getRowCount() {
        return getCandidateModules().size();
      }

      public int getColumnCount() {
        return 2;
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
      }

      public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        myStates[rowIndex] = (Boolean)aValue;
        fireTableCellUpdated(rowIndex, columnIndex);
      }

      public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 0 ? Boolean.class : Module.class;
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
        return columnIndex == 0 ? myStates[rowIndex] : getCandidateModules().get(rowIndex);
      }
    });

    ChooseModulesDialogUtil.setupTable(myTable, project, this::doOKAction);
    TableUtil.setupCheckboxColumn(myTable, 0);

    myTable.getModel().addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        getOKAction().setEnabled(getSelectedModules().size() > 0);
      }
    });

    myStates = new boolean[candidateModules.size()];
    Arrays.fill(myStates, true);

    init();
  }

  @Override
  protected JTable getTable() {
    return myTable;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  public List<Module> getSelectedModules() {
    final ArrayList<Module> list = new ArrayList<>(getCandidateModules());
    final Iterator<Module> modules = list.iterator();
    for (boolean b : myStates) {
      modules.next();
      if (!b) {
        modules.remove();
      }
    }
    return list;
  }
}
