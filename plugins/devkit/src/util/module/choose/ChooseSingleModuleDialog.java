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
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import java.util.List;

public class ChooseSingleModuleDialog extends ChooseModulesDialogBase {
  private final JTable myTable;

  public ChooseSingleModuleDialog(Project project, List<Module> candidateModules, String title) {
    super(project, candidateModules, title, DevKitBundle.message("select.plugin.module.to.patch"));

    myTable = new JBTable(new AbstractTableModel() {
      public int getRowCount() {
        return getCandidateModules().size();
      }

      public int getColumnCount() {
        return 1;
      }

      public Class<?> getColumnClass(int columnIndex) {
        return Module.class;
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
        return getCandidateModules().get(rowIndex);
      }
    });

    ChooseModulesDialogUtil.setupTable(myTable, project, this::doOKAction);

    myTable.getModel().addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        getOKAction().setEnabled(myTable.getSelectedRowCount() > 0);
      }
    });

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

  @Nullable
  public Module getSelectedModule() {
    int selectedRow = myTable.getSelectedRow();
    if (selectedRow < 0) {
      return null;
    }
    return (Module)myTable.getModel().getValueAt(selectedRow, 0);
  }
}
