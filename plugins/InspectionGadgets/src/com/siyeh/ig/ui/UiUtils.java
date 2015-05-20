/*
 * Copyright 2010-2014 Bas Leijdekkers
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
package com.siyeh.ig.ui;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.Collection;

public class UiUtils {

  private UiUtils() {
  }

  public static void setComponentSize(Component component, int rows, int columns) {
    final FontMetrics fontMetrics = component.getFontMetrics(component.getFont());
    final int width = fontMetrics.charWidth('m') * columns;
    component.setPreferredSize(new Dimension(width, fontMetrics.getHeight() * rows));
  }

  public static JPanel createAddRemovePanel(final ListTable table) {
    final JPanel panel = ToolbarDecorator.createDecorator(table)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final ListWrappingTableModel tableModel = table.getModel();
          tableModel.addRow();
          EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
              final int lastRowIndex = tableModel.getRowCount() - 1;
              editTableCell(table, lastRowIndex, 0);
            }
          });
        }
      }).setRemoveAction(new RemoveAction(table))
      .disableUpDownActions().createPanel();
    panel.setPreferredSize(JBUI.size(150, 100));
    return panel;
  }

  public static JPanel createAddRemoveTreeClassChooserPanel(final ListTable table, final String chooserTitle,
                                                            @NonNls String... ancestorClasses) {
    final ClassFilter filter;
    if (ancestorClasses.length == 0) {
      filter = ClassFilter.ALL;
    }
    else {
      filter = new SubclassFilter(ancestorClasses);
    }
    final JPanel panel = ToolbarDecorator.createDecorator(table)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final DataContext dataContext = DataManager.getInstance().getDataContext(table);
          final Project project = CommonDataKeys.PROJECT.getData(dataContext);
          final int rowIndex;
          final ListWrappingTableModel tableModel = table.getModel();
          if (project == null) {
            tableModel.addRow();
            rowIndex = tableModel.getRowCount() - 1;
          }
          else {
            final TreeClassChooserFactory chooserFactory = TreeClassChooserFactory.getInstance(project);
            final TreeClassChooser classChooser =
              chooserFactory.createWithInnerClassesScopeChooser(chooserTitle, GlobalSearchScope.allScope(project), filter, null);
            classChooser.showDialog();
            final PsiClass selectedClass = classChooser.getSelected();
            if (selectedClass == null) {
              return;
            }
            final String qualifiedName = selectedClass.getQualifiedName();
            final int index = tableModel.indexOf(qualifiedName, 0);
            if (index < 0) {
              tableModel.addRow(qualifiedName);
              rowIndex = tableModel.getRowCount() - 1;
            }
            else {
              rowIndex = index;
            }
          }
          editTableCell(table, rowIndex, table.getColumnCount() > 1 && project != null ? 1 : 0);
        }
      }).setRemoveAction(new RemoveAction(table))
      .disableUpDownActions().createPanel();
    panel.setPreferredSize(JBUI.size(150, 100));
    return panel;
  }

  private static void editTableCell(final ListTable table, final int row, final int column) {
    final ListSelectionModel selectionModel = table.getSelectionModel();
    selectionModel.setSelectionInterval(row, row);
    EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        final ListWrappingTableModel tableModel = table.getModel();
        table.requestFocus();
        final Rectangle rectangle = table.getCellRect(row, column, true);
        table.scrollRectToVisible(rectangle);
        table.editCellAt(row, column);
        final TableCellEditor editor = table.getCellEditor();
        final Component component = editor.getTableCellEditorComponent(table, tableModel.getValueAt(row, column), true, row, column);
        component.requestFocus();
      }
    });
  }

  public static JPanel createTreeClassChooserList(final Collection<String> collection,
                                                  String borderTitle,
                                                  final String chooserTitle,
                                                  String... ancestorClasses) {
    final ClassFilter filter;
    if (ancestorClasses.length == 0) {
      filter = ClassFilter.ALL;
    }
    else {
      filter = new SubclassFilter(ancestorClasses);
    }
    final JPanel optionsPanel = new JPanel(new BorderLayout());
    final JBList list = new JBList(collection);

    final JPanel panel = ToolbarDecorator.createDecorator(list)
      .disableUpDownActions()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          final DataContext dataContext = DataManager.getInstance().getDataContext(list);
          final Project project = CommonDataKeys.PROJECT.getData(dataContext);
          if (project == null) {
            return;
          }
          final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
            .createNoInnerClassesScopeChooser(chooserTitle, GlobalSearchScope.allScope(project), filter, null);
          chooser.showDialog();
          final PsiClass selected = chooser.getSelected();
          if (selected == null) {
            return;
          }
          final String qualifiedName = selected.getQualifiedName();
          final DefaultListModel model = (DefaultListModel)list.getModel();
          final int index = model.indexOf(qualifiedName);
          if (index < 0) {
            model.addElement(qualifiedName);
            collection.add(qualifiedName);
          }
          else {
            list.setSelectedIndex(index);
          }
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          collection.remove(list.getSelectedValue());
          ListUtil.removeSelectedItems(list);
        }
      }).createPanel();
    panel.setPreferredSize(JBUI.size(150, 100));
    optionsPanel.setBorder(IdeBorderFactory.createTitledBorder(borderTitle,
                                                               false, new Insets(10, 0, 0, 0)));
    optionsPanel.add(panel);
    return optionsPanel;
  }

  private static class RemoveAction implements AnActionButtonRunnable {

    private final ListTable table;

    public RemoveAction(ListTable table) {
      this.table = table;
    }

    @Override
    public void run(AnActionButton button) {
      EventQueue.invokeLater(new Runnable() {
        @Override
        public void run() {
          final TableCellEditor editor = table.getCellEditor();
          if (editor != null) {
            editor.stopCellEditing();
          }
          final ListSelectionModel selectionModel = table.getSelectionModel();
          final int minIndex = selectionModel.getMinSelectionIndex();
          final int maxIndex = selectionModel.getMaxSelectionIndex();
          if (minIndex == -1 || maxIndex == -1) {
            return;
          }
          final ListWrappingTableModel tableModel = table.getModel();
          for (int i = minIndex; i <= maxIndex; i++) {
            if (selectionModel.isSelectedIndex(i)) {
              tableModel.removeRow(i);
            }
          }
          final int count = tableModel.getRowCount();
          if (count <= minIndex) {
            selectionModel.setSelectionInterval(count - 1, count - 1);
          }
          else if (minIndex <= 0) {
            if (count > 0) {
              selectionModel.setSelectionInterval(0, 0);
            }
          }
          else {
            selectionModel.setSelectionInterval(minIndex - 1, minIndex - 1);
          }
        }
      });
    }
  }

  private static class SubclassFilter implements ClassFilter {

    private final String[] ancestorClasses;

    private SubclassFilter(String[] ancestorClasses) {
      this.ancestorClasses = ancestorClasses;
    }

    @Override
    public boolean isAccepted(PsiClass aClass) {
      for (String ancestorClass : ancestorClasses) {
        if (InheritanceUtil.isInheritor(aClass, ancestorClass)) {
          return true;
        }
      }
      return false;
    }
  }
}
